package core.domain.receiving

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.InventoryCostLayer
import core.domain.model.Money
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.Quantity
import core.domain.model.QuantityScale
import core.domain.model.StockBatch
import core.domain.model.StockMovement
import java.util.UUID

/**
 * Domain service governing the goods receiving, batch resolution, unit conversion,
 * and cost layer allocation workflow.
 *
 * Core Responsibilities:
 * 1. Discrete vs Continuous Quantity Validation:
 *    Enforces discrete physical integrity for discrete health products (tablets, capsules, syringes, gloves).
 * 2. Receiving Unit to Base Quantity Conversion:
 *    Applies [ProductUnit.conversionMultiplier] using exact integer multiplication with overflow detection.
 * 3. Exact Acquisition Cost Tranche Allocation:
 *    Calculates base unit acquisition costs preserving exact KES minor units without monetary leakage.
 * 4. Tracking Mode & Batch Resolution:
 *    Resolves or creates [StockBatch] records across the 4 tracking modes:
 *    - STANDARD_BATCHED: Supplier batch + valid Gregorian expiry
 *    - BATCH_UNKNOWN_EXPIRY: Supplier batch + expiry -1
 *    - SUPPLIER_UNTRACKED: Receipt-scoped isolated batch (UNSPECIFIED-GR-{receiptId}-{lineIndex})
 *    - NON_BATCHED_COMMODITY: Canonical commodity anchor (COMMODITY) + expiry -1
 * 5. Atomic Commit Bundle Generation:
 *    Prepares the complete set of entities (committed receipt, batches, cost layers, stock movements)
 *    to be persisted atomically within a database transaction.
 * 6. Idempotency & Duplicate Protection:
 *    Safely rejects attempts to commit an already committed or voided receipt.
 */
object GoodsReceiptService {

    const val COMMODITY_BATCH_NUMBER: String = "COMMODITY"

    /**
     * Generates a deterministic, receipt-scoped isolated batch number for supplier-untracked stock.
     * Guarantees that two separate untracked receipts of the same product do NOT merge into the same physical batch.
     */
    fun generateUntrackedBatchNumber(goodsReceiptId: String, lineIndex: Int): String {
        return "UNSPECIFIED-GR-$goodsReceiptId-$lineIndex"
    }

    /**
     * Converts a received commercial packaging quantity into exact base storage units.
     *
     * @param receivedQuantity Quantity received in the commercial [receivingUnit]
     * @param receivingUnit Packaging unit used during receiving
     * @param baseUnit Canonical base storage unit for the product
     * @param lineIndex Index of the line for error reporting
     * @return Result containing either the converted base [Quantity] or a [ReceivingError]
     */
    fun convertToBaseQuantity(
        receivedQuantity: Quantity,
        receivingUnit: ProductUnit,
        baseUnit: ProductUnit,
        lineIndex: Int
    ): Either<ReceivingError, Quantity> {
        val isDiscrete = baseUnit.conversionMultiplier == 1L

        // Discrete check: discrete products must not have fractional commercial units that result in fractional base units
        if (isDiscrete) {
            val scaleMultiplier = receivedQuantity.scale.multiplier
            val hasFractionalCommercialUnit = (receivedQuantity.storageUnits % scaleMultiplier) != 0L
            if (hasFractionalCommercialUnit) {
                // If commercial unit has fractional multiplier, verify if the resulting base unit would be fractional
                val remainderBase = (receivedQuantity.storageUnits * receivingUnit.conversionMultiplier) % scaleMultiplier
                if (remainderBase != 0L) {
                    return Either.Left(
                        ReceivingError.InvalidDiscreteQuantity(
                            lineIndex = lineIndex,
                            rawUnits = receivedQuantity.storageUnits,
                            scale = receivedQuantity.scale.scale
                        )
                    )
                }
            }
        }

        val baseScale = QuantityScale.entries.find { it.multiplier == baseUnit.conversionMultiplier }
            ?: QuantityScale.SCALE_0

        val baseStorageUnits = try {
            val rawScaled = Math.multiplyExact(receivedQuantity.storageUnits, receivingUnit.conversionMultiplier)
            rawScaled / receivedQuantity.scale.multiplier
        } catch (e: ArithmeticException) {
            return Either.Left(
                ReceivingError.QuantityConversionOverflow(
                    lineIndex = lineIndex,
                    detail = e.message ?: "Arithmetic overflow during multiplication"
                )
            )
        }

        return Either.Right(Quantity(baseStorageUnits, baseScale))
    }

    /**
     * Calculates exact acquisition cost layers for a received item line without monetary leakage.
     *
     * Where totalCost does not divide evenly into base units, divides into two deterministic tranches:
     * - Tranche 1: (totalUnits - remainder) units at floor(totalCost / totalUnits)
     * - Tranche 2: remainder units at floor(totalCost / totalUnits) + 1 cent
     *
     * The sum of (Tranche 1 value + Tranche 2 value) strictly equals [totalCost] to the exact minor unit.
     */
    fun calculateCostLayerTranches(
        totalCost: Money,
        baseQuantity: Quantity,
        productId: String,
        stockBatchId: String,
        supplierId: String?,
        sourceReceiptRef: String?,
        acquiredAt: Long,
        createdAt: Long,
        idGenerator: (index: Int) -> String = { index -> "ICL-${UUID.randomUUID()}-$index" }
    ): List<InventoryCostLayer> {
        val units = baseQuantity.storageUnits
        require(units > 0L) { "Base storage units must be strictly positive: $units" }

        val totalMinor = totalCost.amountMinorUnits
        val unitCostMinor = totalMinor / units
        val remainderMinor = totalMinor % units

        return if (remainderMinor == 0L) {
            listOf(
                InventoryCostLayer(
                    id = idGenerator(0),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = baseQuantity,
                    remainingQuantity = baseQuantity,
                    acquisitionUnitCost = Money(unitCostMinor),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            )
        } else {
            val primaryUnits = units - remainderMinor
            val primaryQuantity = Quantity(primaryUnits, baseQuantity.scale)
            val remainderQuantity = Quantity(remainderMinor, baseQuantity.scale)

            listOf(
                InventoryCostLayer(
                    id = idGenerator(0),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = primaryQuantity,
                    remainingQuantity = primaryQuantity,
                    acquisitionUnitCost = Money(unitCostMinor),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                ),
                InventoryCostLayer(
                    id = idGenerator(1),
                    productId = productId,
                    stockBatchId = stockBatchId,
                    supplierId = supplierId,
                    initialQuantity = remainderQuantity,
                    remainingQuantity = remainderQuantity,
                    acquisitionUnitCost = Money(unitCostMinor + 1L),
                    acquiredAt = acquiredAt,
                    sourceReceiptRef = sourceReceiptRef,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            )
        }
    }

    /**
     * Resolves the physical [StockBatch] for a receipt item according to its tracking mode.
     * Reuses an existing matching batch if one already exists; otherwise constructs a new one.
     */
    fun resolveBatch(
        item: GoodsReceiptItem,
        receiptId: String,
        existingBatches: List<StockBatch>,
        createdAt: Long,
        idGenerator: () -> String = { "SB-${UUID.randomUUID()}" }
    ): Pair<StockBatch, Boolean> {
        val (normalizedBatchNumber, resolvedExpiry) = when (item.trackingMode) {
            StockBatch.TRACKING_STANDARD_BATCHED -> {
                item.batchNumber!!.trim().uppercase() to item.expiryDateInt
            }
            StockBatch.TRACKING_BATCH_UNKNOWN_EXPIRY -> {
                item.batchNumber!!.trim().uppercase() to StockBatch.EXPIRY_UNKNOWN_OR_NONE
            }
            StockBatch.TRACKING_SUPPLIER_UNTRACKED -> {
                generateUntrackedBatchNumber(receiptId, item.lineIndex) to item.expiryDateInt
            }
            StockBatch.TRACKING_NON_BATCHED_COMMODITY -> {
                COMMODITY_BATCH_NUMBER to StockBatch.EXPIRY_UNKNOWN_OR_NONE
            }
            else -> {
                (item.batchNumber?.trim()?.uppercase() ?: generateUntrackedBatchNumber(receiptId, item.lineIndex)) to item.expiryDateInt
            }
        }

        val existing = existingBatches.find { batch ->
            batch.productId == item.productId &&
            batch.batchNumber == normalizedBatchNumber &&
            batch.expiryDateInt == resolvedExpiry
        }

        return if (existing != null) {
            existing to false
        } else {
            val newBatch = StockBatch(
                id = idGenerator(),
                productId = item.productId,
                batchNumber = normalizedBatchNumber,
                expiryDateInt = resolvedExpiry,
                trackingMode = item.trackingMode,
                createdAt = createdAt,
                updatedAt = createdAt
            )
            newBatch to true
        }
    }

    /**
     * Prepares an atomic commit bundle for a [GoodsReceipt] and its [items].
     *
     * Validates all items, checks discrete constraints, converts packaging units to base quantities,
     * resolves batches, generates exact remainder-allocated cost layers, and creates physical stock movements.
     *
     * @param receipt The goods receipt to commit
     * @param items Items attached to this receipt
     * @param productsById Map of canonical products
     * @param unitsById Map of commercial units
     * @param baseUnitsByProductId Map of base units per product
     * @param existingBatches Currently existing stock batches in the facility
     * @param referenceDateInt Facility calendar date (YYYYMMDD) for warning evaluation
     * @param commitInstant Epoch millisecond instant of commitment
     */
    fun prepareCommit(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>,
        productsById: Map<String, ProductMaster>,
        unitsById: Map<String, ProductUnit>,
        baseUnitsByProductId: Map<String, ProductUnit>,
        existingBatches: List<StockBatch>,
        referenceDateInt: Int,
        commitInstant: Long
    ): ReceivingResult {
        // Idempotency: Prevent duplicate commit
        if (receipt.isCommitted) {
            return ReceivingResult.Failure(listOf(ReceivingError.AlreadyCommitted(receipt.id)))
        }
        if (receipt.isVoided) {
            return ReceivingResult.Failure(listOf(ReceivingError.ReceiptVoided(receipt.id)))
        }
        if (items.isEmpty()) {
            return ReceivingResult.Failure(listOf(ReceivingError.EmptyReceipt(receipt.id)))
        }

        val errors = mutableListOf<ReceivingError>()
        val warnings = mutableListOf<ReceivingWarning>()

        if (receipt.supplierId == null) {
            warnings.add(ReceivingWarning.NoSupplierSpecified(receipt.id))
        }
        if (receipt.sourceDocumentRef.isNullOrBlank()) {
            warnings.add(ReceivingWarning.MissingSupplierReference(receipt.id))
        }

        val allKnownBatches = existingBatches.toMutableList()
        val createdBatches = mutableListOf<StockBatch>()
        val createdCostLayers = mutableListOf<InventoryCostLayer>()
        val createdMovements = mutableListOf<StockMovement>()

        for (item in items) {
            val product = productsById[item.productId]
            if (product == null) {
                errors.add(ReceivingError.ProductNotFound(item.productId, item.lineIndex))
                continue
            }
            if (!product.isActive) {
                errors.add(ReceivingError.ProductInactive(item.productId, item.lineIndex))
                continue
            }

            val receivingUnit = unitsById[item.receivingUnitId]
            if (receivingUnit == null) {
                errors.add(ReceivingError.UnitNotFound(item.receivingUnitId, item.lineIndex))
                continue
            }
            if (receivingUnit.productId != product.id) {
                errors.add(
                    ReceivingError.UnitProductMismatch(
                        unitId = item.receivingUnitId,
                        expectedProductId = product.id,
                        actualProductId = receivingUnit.productId,
                        lineIndex = item.lineIndex
                    )
                )
                continue
            }

            val baseUnit = baseUnitsByProductId[product.id]
            if (baseUnit == null) {
                errors.add(ReceivingError.BaseUnitNotFound(product.id, item.lineIndex))
                continue
            }

            // Expiry date validation and warnings
            if (item.expiryDateInt != StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
                if (!StockBatch.isValidExpiryDateInt(item.expiryDateInt)) {
                    errors.add(ReceivingError.InvalidExpiryDate(item.lineIndex, item.expiryDateInt))
                    continue
                }
                if (item.expiryDateInt < referenceDateInt) {
                    warnings.add(
                        ReceivingWarning.StockAlreadyExpired(
                            lineIndex = item.lineIndex,
                            expiryDateInt = item.expiryDateInt,
                            referenceDateInt = referenceDateInt
                        )
                    )
                } else if (item.expiryDateInt == referenceDateInt) {
                    warnings.add(
                        ReceivingWarning.StockExpiringToday(
                            lineIndex = item.lineIndex,
                            expiryDateInt = item.expiryDateInt
                        )
                    )
                }
            }

            // Unit conversion
            val baseQuantityResult = convertToBaseQuantity(item.receivedQuantity, receivingUnit, baseUnit, item.lineIndex)
            val baseQuantity = when (baseQuantityResult) {
                is Either.Left -> {
                    errors.add(baseQuantityResult.value)
                    continue
                }
                is Either.Right -> baseQuantityResult.value
            }

            // Batch resolution
            val (resolvedBatch, isNew) = resolveBatch(
                item = item,
                receiptId = receipt.id,
                existingBatches = allKnownBatches,
                createdAt = commitInstant
            )
            if (isNew) {
                createdBatches.add(resolvedBatch)
                allKnownBatches.add(resolvedBatch)
            }

            // Cost layers (remainder-allocated, zero leakage)
            val layers = calculateCostLayerTranches(
                totalCost = item.totalCost,
                baseQuantity = baseQuantity,
                productId = product.id,
                stockBatchId = resolvedBatch.id,
                supplierId = receipt.supplierId,
                sourceReceiptRef = receipt.sourceDocumentRef ?: receipt.receiptNumber,
                acquiredAt = receipt.receivedAt,
                createdAt = commitInstant,
                idGenerator = { trancheIndex -> "ICL-GR-${receipt.id}-${item.lineIndex}-$trancheIndex" }
            )
            createdCostLayers.addAll(layers)

            // Physical StockMovement (PURCHASE_RECEIPT)
            val movement = StockMovement(
                id = "SM-GR-${receipt.id}-${item.lineIndex}",
                productId = product.id,
                stockBatchId = resolvedBatch.id,
                movementType = StockMovement.TYPE_PURCHASE_RECEIPT,
                quantity = baseQuantity,
                occurredAt = receipt.receivedAt,
                sourceTransactionRef = receipt.id,
                sourceTransactionType = "GOODS_RECEIPT",
                initiatedByUserId = receipt.receivedByUserId,
                reason = "Purchasing receipt: ${receipt.receiptNumber} (Line ${item.lineIndex})",
                createdAt = commitInstant
            )
            createdMovements.add(movement)
        }

        if (errors.isNotEmpty()) {
            return ReceivingResult.Failure(errors = errors, warnings = warnings)
        }

        val committedReceipt = receipt.copy(
            status = GoodsReceipt.STATUS_COMMITTED,
            committedAt = commitInstant,
            updatedAt = commitInstant
        )

        return ReceivingResult.Success(
            committedReceipt = committedReceipt,
            newBatches = createdBatches,
            costLayers = createdCostLayers,
            stockMovements = createdMovements,
            warnings = warnings
        )
    }

    /**
     * Lightweight functional Either construct to avoid external dependency.
     */
    sealed class Either<out L, out R> {
        data class Left<out L>(val value: L) : Either<L, Nothing>()
        data class Right<out R>(val value: R) : Either<Nothing, R>()
    }
}
