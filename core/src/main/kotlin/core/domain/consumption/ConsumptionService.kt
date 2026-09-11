package core.domain.consumption

import core.domain.allocation.CostLayerAllocationService
import core.domain.fefo.ExpiryPolicy
import core.domain.fefo.FefoService
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.Quantity
import core.domain.model.Sale
import core.domain.model.SaleItem
import core.domain.model.StockAllocation
import core.domain.model.StockMovement
import core.domain.persistence.CoreDatabase
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.ProductMasterDao
import core.domain.persistence.RoomTransactionRunner
import core.domain.persistence.SaleDao
import core.domain.persistence.StockAllocationDao
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.persistence.TransactionRunner
import core.domain.time.LocalDateValue
import java.util.UUID

/**
 * Request parameter representing an individual product item to dispense or sell.
 */
data class ConsumptionLineRequest(
    val productId: String,
    val dispensingUnitId: String,
    val requestedQuantity: Quantity,
    val customUnitPrice: Money? = null
)

/**
 * Command requesting a complete consumption/dispensing transaction.
 */
data class ConsumptionRequest(
    val saleId: String = UUID.randomUUID().toString(),
    val saleNumber: String,
    val items: List<ConsumptionLineRequest>,
    val customerRef: String? = null,
    val initiatedByUserId: String? = null,
    val notes: String? = null,
    val facilityCalendarDate: LocalDateValue,
    val expiryPolicy: ExpiryPolicy = ExpiryPolicy.DEFAULT,
    val transactionTimestamp: Long
)

/**
 * Result bundle emitted upon successful atomic commit of a consumption transaction.
 */
data class ConsumptionResult(
    val sale: Sale,
    val items: List<SaleItem>,
    val allocations: List<StockAllocation>,
    val movements: List<StockMovement>,
    val updatedCostLayers: List<InventoryCostLayer>
)

/**
 * Exception thrown when physical or eligible stock is insufficient to fulfill consumption.
 */
class InsufficientStockException(message: String) : IllegalStateException(message)

/**
 * Service orchestrating the end-to-end inventory consumption/dispensing transaction chain:
 *
 * PRODUCT/UNIT VALIDATION -> FEFO BATCH SELECTION -> STOCK ALLOCATION -> EXACT COGS ->
 * SALE/SALE ITEM CREATION -> PHYSICAL STOCK MOVEMENT LEDGER DEDUCTION -> ATOMIC PERSISTENCE
 *
 * Invariants Enforced:
 * 1. Atomicity: Everything commits in a single database transaction, or nothing commits.
 * 2. Idempotency: Duplicate sale numbers are rejected.
 * 3. Exactness: Float/Double strictly prohibited. Money and Quantity calculations are integer-exact.
 * 4. Immutability: StockMovement is append-only. Committed sales, items, and allocations are never rewritten.
 * 5. Cost Layer FIFO: Earlier acquired tranches within selected physical batches are depleted first.
 */
class ConsumptionService(
    private val transactionRunner: TransactionRunner,
    private val saleDao: SaleDao,
    private val productMasterDao: ProductMasterDao,
    private val stockBatchDao: StockBatchDao,
    private val inventoryCostLayerDao: InventoryCostLayerDao,
    private val stockMovementDao: StockMovementDao,
    private val stockAllocationDao: StockAllocationDao
) {

    constructor(database: CoreDatabase) : this(
        transactionRunner = RoomTransactionRunner(database),
        saleDao = database.saleDao(),
        productMasterDao = database.productMasterDao(),
        stockBatchDao = database.stockBatchDao(),
        inventoryCostLayerDao = database.inventoryCostLayerDao(),
        stockMovementDao = database.stockMovementDao(),
        stockAllocationDao = database.stockAllocationDao()
    )

    // In-process lock to prevent thread interleaving on top of SQLite transaction serialization
    private val transactionLock = Any()

    /**
     * Executes a complete stock consumption transaction.
     */
    fun consumeStock(request: ConsumptionRequest): ConsumptionResult {
        require(request.items.isNotEmpty()) { "Consumption request must contain at least one item" }

        synchronized(transactionLock) {
            return transactionRunner.runInTransaction {
                // 1. Idempotency check: Reject duplicate sale number
                val existingSale = saleDao.getSaleByNumber(request.saleNumber)
                    ?: saleDao.getSaleById(request.saleId)
                if (existingSale != null) {
                    throw IllegalStateException("Sale transaction '${request.saleNumber}' (id=${request.saleId}) already exists.")
                }

                val allSaleItems = mutableListOf<SaleItem>()
                val allAllocations = mutableListOf<StockAllocation>()
                val allMovements = mutableListOf<StockMovement>()
                val allUpdatedLayers = mutableListOf<InventoryCostLayer>()

                var totalSaleSellingAmountMinor = 0L
                var totalSaleCogsMinor = 0L

                // 2. Process each requested line item
                request.items.forEachIndexed { lineIndex, lineReq ->
                    val product = productMasterDao.getProductById(lineReq.productId)
                        ?: throw IllegalArgumentException("Product not found: ${lineReq.productId}")

                    val dispensingUnit = productMasterDao.getUnitById(lineReq.dispensingUnitId)
                        ?: throw IllegalArgumentException("Unit not found: ${lineReq.dispensingUnitId}")

                    require(dispensingUnit.productId == product.id) {
                        "Unit '${dispensingUnit.id}' does not belong to product '${product.id}'"
                    }
                    require(lineReq.requestedQuantity.isPositive) {
                        "Requested quantity must be strictly positive (> 0), got: ${lineReq.requestedQuantity.storageUnits}"
                    }

                    // Convert dispensing quantity to base quantity
                    val baseQuantityUnits = Math.multiplyExact(
                        lineReq.requestedQuantity.storageUnits,
                        dispensingUnit.conversionMultiplier
                    )
                    val baseQuantity = Quantity(baseQuantityUnits, product.quantityScale)

                    // Load physical batches for this product
                    val batches = stockBatchDao.getBatchesForProduct(product.id)
                    if (batches.isEmpty()) {
                        throw InsufficientStockException(
                            "No physical stock batches exist for product '${product.canonicalName}' (${product.id})"
                        )
                    }

                    // Calculate currently available physical stock for each batch from the authoritative ledger
                    val batchesWithQuantities = batches.map { batch ->
                        val availableUnits = stockMovementDao.getPhysicalStockUnitsForBatch(batch.id)
                        batch to Quantity(availableUnits, product.quantityScale)
                    }

                    // Evaluate batches against FEFO policy
                    val evaluatedCandidates = FefoService.evaluateCandidates(
                        batchesWithQuantities = batchesWithQuantities,
                        facilityCalendarDate = request.facilityCalendarDate,
                        policy = request.expiryPolicy
                    )

                    // Plan batch allocations under FEFO
                    val fefoPlan = FefoService.planAllocation(
                        requestedQuantity = baseQuantity,
                        evaluatedCandidates = evaluatedCandidates
                    )

                    if (!fefoPlan.isFullySatisfied) {
                        throw InsufficientStockException(
                            "Insufficient eligible stock for product '${product.canonicalName}'. " +
                                "Requested: ${baseQuantity.storageUnits} base units, " +
                                "available eligible: ${fefoPlan.allocatedTotal.storageUnits} base units."
                        )
                    }

                    // Resolve active cost layers for the selected batches
                    val layersByBatch = fefoPlan.allocations.associate { candidate ->
                        candidate.batch.id to inventoryCostLayerDao.getActiveLayersForBatch(candidate.batch.id)
                    }

                    val saleItemId = UUID.randomUUID().toString()

                    // Allocate consumption across discrete cost layers & compute exact COGS
                    val allocationResult = CostLayerAllocationService.allocateCostLayers(
                        candidateAllocations = fefoPlan.allocations,
                        activeLayersByBatch = layersByBatch,
                        consumptionTransactionId = request.saleId,
                        consumptionItemId = saleItemId,
                        productId = product.id,
                        allocationTimestamp = request.transactionTimestamp
                    )

                    // Snapshot selling price
                    val sellingPrice = lineReq.customUnitPrice
                        ?: productMasterDao.getActivePriceConfigForUnit(dispensingUnit.id)?.sellingPrice
                        ?: Money.ZERO

                    val lineSellingTotalMinor = Math.multiplyExact(
                        lineReq.requestedQuantity.storageUnits,
                        sellingPrice.amountMinorUnits
                    )
                    val lineSellingTotal = Money(lineSellingTotalMinor)
                    val lineCogs = allocationResult.totalCogs

                    val saleItem = SaleItem(
                        id = saleItemId,
                        saleId = request.saleId,
                        lineIndex = lineIndex,
                        productId = product.id,
                        dispensingUnitId = dispensingUnit.id,
                        requestedQuantity = lineReq.requestedQuantity,
                        baseQuantity = baseQuantity,
                        unitPriceSnapshot = sellingPrice,
                        lineTotal = lineSellingTotal,
                        lineCogs = lineCogs,
                        createdAt = request.transactionTimestamp,
                        updatedAt = request.transactionTimestamp
                    )

                    allSaleItems.add(saleItem)
                    allAllocations.addAll(allocationResult.allocations)
                    allUpdatedLayers.addAll(allocationResult.updatedCostLayers)

                    totalSaleSellingAmountMinor = Math.addExact(totalSaleSellingAmountMinor, lineSellingTotalMinor)
                    totalSaleCogsMinor = Math.addExact(totalSaleCogsMinor, lineCogs.amountMinorUnits)

                    // Create negative stock movements for each consumed batch
                    for (candidate in fefoPlan.allocations) {
                        val negativeQuantity = -candidate.allocatedQuantity
                        val movement = StockMovement(
                            id = UUID.randomUUID().toString(),
                            productId = product.id,
                            stockBatchId = candidate.batch.id,
                            movementType = StockMovement.TYPE_SALE,
                            quantity = negativeQuantity,
                            occurredAt = request.transactionTimestamp,
                            sourceTransactionRef = request.saleNumber,
                            sourceTransactionType = "SALE",
                            initiatedByUserId = request.initiatedByUserId,
                            reason = "Sale: ${request.saleNumber}",
                            createdAt = request.transactionTimestamp
                        )
                        allMovements.add(movement)
                    }
                }

                // 3. Create the parent Sale transaction header
                val sale = Sale(
                    id = request.saleId,
                    saleNumber = request.saleNumber,
                    status = Sale.STATUS_COMPLETED,
                    customerRef = request.customerRef,
                    totalSellingAmount = Money(totalSaleSellingAmountMinor),
                    totalCogs = Money(totalSaleCogsMinor),
                    occurredAt = request.transactionTimestamp,
                    initiatedByUserId = request.initiatedByUserId,
                    notes = request.notes,
                    createdAt = request.transactionTimestamp,
                    updatedAt = request.transactionTimestamp
                )

                // 4. Atomic database writes
                saleDao.insertSale(sale)
                saleDao.insertSaleItems(allSaleItems)
                stockAllocationDao.insertAllocations(allAllocations)
                stockMovementDao.insertMovements(allMovements)
                inventoryCostLayerDao.updateLayers(allUpdatedLayers)

                ConsumptionResult(
                    sale = sale,
                    items = allSaleItems,
                    allocations = allAllocations,
                    movements = allMovements,
                    updatedCostLayers = allUpdatedLayers
                )
            }
        }
    }

    /**
     * Reversal / Void architectural pathway:
     * Atomically voids a previously completed sale without deleting historical records.
     */
    fun voidSale(saleId: String, voidTimestamp: Long, reason: String): Sale {
        synchronized(transactionLock) {
            return transactionRunner.runInTransaction {
                val sale = saleDao.getSaleById(saleId)
                    ?: throw IllegalArgumentException("Sale not found: $saleId")

                if (sale.isVoided) {
                    throw IllegalStateException("Sale '${sale.saleNumber}' is already voided.")
                }

                val allocations = stockAllocationDao.getAllocationsForSale(saleId)
                val compensatingMovements = mutableListOf<StockMovement>()
                val restoredLayers = mutableListOf<InventoryCostLayer>()

                for (alloc in allocations) {
                    val compensatingMovement = StockMovement(
                        id = UUID.randomUUID().toString(),
                        productId = alloc.productId,
                        stockBatchId = alloc.stockBatchId,
                        movementType = StockMovement.TYPE_RETURN,
                        quantity = alloc.allocatedQuantity,
                        occurredAt = voidTimestamp,
                        sourceTransactionRef = sale.saleNumber,
                        sourceTransactionType = "SALE_VOID",
                        reason = "Void of sale ${sale.saleNumber}: $reason",
                        createdAt = voidTimestamp
                    )
                    compensatingMovements.add(compensatingMovement)

                    val layer = inventoryCostLayerDao.getLayerById(alloc.inventoryCostLayerId)
                        ?: throw IllegalStateException("Cost layer '${alloc.inventoryCostLayerId}' not found during void")

                    val restoredQty = layer.remainingQuantity + alloc.allocatedQuantity
                    restoredLayers.add(layer.copy(remainingQuantity = restoredQty, updatedAt = voidTimestamp))
                }

                stockMovementDao.insertMovements(compensatingMovements)
                inventoryCostLayerDao.updateLayers(restoredLayers)

                val voidedSale = sale.copy(
                    status = Sale.STATUS_VOIDED,
                    notes = if (sale.notes == null) "VOIDED: $reason" else "${sale.notes} | VOIDED: $reason",
                    updatedAt = voidTimestamp
                )
                saleDao.updateSale(voidedSale)
                voidedSale
            }
        }
    }
}
