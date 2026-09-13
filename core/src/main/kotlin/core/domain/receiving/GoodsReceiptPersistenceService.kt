package core.domain.receiving
import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.persistence.CoreDatabase
import core.domain.persistence.GoodsReceiptDao
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.RoomTransactionRunner
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.persistence.TransactionRunner
import java.util.Calendar
import java.util.TimeZone
/**
 * Service orchestrating the atomic persistence of inventory receiving transactions.
 *
 * Responsibilities:
 * - Enforces the database transaction boundary for receiving.
 * - Performs duplicate/idempotency checks before persistence.
 * - Loads existing physical batches required by the domain receiving service.
 * - Supplies the current base-unit mapping and reference calendar date required by
 *   [GoodsReceiptService.prepareCommit].
 * - Persists the complete successful receiving result atomically.
 *
 * Important architectural boundary:
 * - GoodsReceiptService owns receiving-domain decisions.
 * - This service owns persistence orchestration.
 * - StockMovement remains the authoritative physical stock-flow ledger.
 * - InventoryCostLayer remains the acquisition-cost representation.
 */
class GoodsReceiptPersistenceService(
    private val transactionRunner: TransactionRunner,
    private val goodsReceiptDao: GoodsReceiptDao,
    private val stockBatchDao: StockBatchDao,
    private val inventoryCostLayerDao: InventoryCostLayerDao,
    private val stockMovementDao: StockMovementDao
) {

    constructor(database: CoreDatabase) : this(
        transactionRunner = RoomTransactionRunner(database),
        goodsReceiptDao = database.goodsReceiptDao(),
        stockBatchDao = database.stockBatchDao(),
        inventoryCostLayerDao = database.inventoryCostLayerDao(),
        stockMovementDao = database.stockMovementDao()
    )

    /**
     * Atomically commits a [GoodsReceipt] and its associated [items].
     *
     * The supplied [unitsById] must contain both the commercial receiving units
     * referenced by the receipt items and the canonical base units for the
     * products involved.
     *
     * @param receipt The goods receipt header to commit.
     * @param items The line items belonging to this receipt.
     * @param productsById Canonical products involved in the receipt.
     * @param unitsById Commercial and base units involved in the receipt.
     * @param commitTimestamp Epoch millisecond timestamp of the commit.
     * @return The successful [ReceivingResult] produced by the domain service.
     */
    fun commitReceipt(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>,
        productsById: Map<String, ProductMaster>,
        unitsById: Map<String, ProductUnit>,
        commitTimestamp: Long
    ): ReceivingResult {
        require(commitTimestamp > 0L) {
            "commitTimestamp must be positive, got: $commitTimestamp"
        }

        return transactionRunner.runInTransaction {
            val existingReceipt = goodsReceiptDao.getReceiptById(receipt.id)
                ?: goodsReceiptDao.getReceiptByNumber(receipt.receiptNumber)

            if (existingReceipt != null && existingReceipt.isCommitted) {
                throw IllegalStateException(
                    "Goods receipt '${receipt.receiptNumber}' " +
                        "(id=${receipt.id}) is already committed and cannot be re-committed."
                )
            }

            val existingLayers =
                inventoryCostLayerDao.getLayersForReceiptRef(receipt.receiptNumber)

            if (existingLayers.isNotEmpty()) {
                throw IllegalStateException(
                    "Duplicate receiving attempt detected: " +
                        "cost layers already exist for receipt '${receipt.receiptNumber}'."
                )
            }

            val existingMovements =
                stockMovementDao.getMovementsBySourceRef(receipt.receiptNumber)

            if (existingMovements.isNotEmpty()) {
                throw IllegalStateException(
                    "Duplicate receiving attempt detected: " +
                        "stock movements already exist for receipt '${receipt.receiptNumber}'."
                )
            }

            val productIds = items
                .map { it.productId }
                .toSet()

            val existingBatches = productIds
                .flatMap { productId ->
                    stockBatchDao.getBatchesForProduct(productId)
                }

            val baseUnitsByProductId = productIds.associateWith { productId ->
                val baseUnits = unitsById.values.filter { unit ->
                    unit.productId == productId && unit.isBaseUnit
                }

                when {
                    baseUnits.isEmpty() -> {
                        throw IllegalArgumentException(
                            "No canonical base unit is available for product '$productId'."
                        )
                    }

                    baseUnits.size > 1 -> {
                        throw IllegalArgumentException(
                            "Multiple canonical base units are defined for product '$productId'."
                        )
                    }

                    else -> baseUnits.single()
                }
            }

            val referenceDateInt = epochMillisToDateInt(receipt.receivedAt)

            val result = GoodsReceiptService.prepareCommit(
                receipt = receipt,
                items = items,
                productsById = productsById,
                unitsById = unitsById,
                baseUnitsByProductId = baseUnitsByProductId,
                existingBatches = existingBatches,
                referenceDateInt = referenceDateInt,
                commitInstant = commitTimestamp
            )

            val success = when (result) {
                is ReceivingResult.Success -> result

                is ReceivingResult.Failure -> {
                    throw IllegalArgumentException(
                        buildReceivingFailureMessage(
                            receiptId = receipt.id,
                            failure = result
                        )
                    )
                }
            }

            if (existingReceipt == null) {
                goodsReceiptDao.insertReceipt(success.committedReceipt)
                goodsReceiptDao.insertReceiptItems(items)
            } else {
                goodsReceiptDao.updateReceipt(success.committedReceipt)

                val existingItems =
                    goodsReceiptDao.getItemsForReceipt(receipt.id)

                if (existingItems.isEmpty()) {
                    goodsReceiptDao.insertReceiptItems(items)
                }
            }

            if (success.newBatches.isNotEmpty()) {
                stockBatchDao.insertBatches(success.newBatches)
            }

            if (success.costLayers.isNotEmpty()) {
                inventoryCostLayerDao.insertLayers(success.costLayers)
            }

            if (success.stockMovements.isNotEmpty()) {
                stockMovementDao.insertMovements(success.stockMovements)
            }

            success
        }
    }

    /**
     * Converts epoch milliseconds into the YYYYMMDD integer representation
     * required by GoodsReceiptService.
     *
     * Calendar is used instead of java.time.Instant because the application
     * minimum SDK is API 24.
     */
    private fun epochMillisToDateInt(epochMillis: Long): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMillis
        }

        return (calendar.get(Calendar.YEAR) * 10_000) +
            ((calendar.get(Calendar.MONTH) + 1) * 100) +
            calendar.get(Calendar.DAY_OF_MONTH)
    }

    private fun buildReceivingFailureMessage(
        receiptId: String,
        failure: ReceivingResult.Failure
    ): String {
        val errors = failure.errors.joinToString(separator = "; ") {
            it.toString()
        }

        return if (failure.warnings.isEmpty()) {
            "Goods receipt '$receiptId' failed validation: $errors"
        } else {
            val warnings = failure.warnings.joinToString(separator = "; ") {
                it.toString()
            }

            "Goods receipt '$receiptId' failed validation: " +
                "$errors. Warnings: $warnings"
        }
    }
}
