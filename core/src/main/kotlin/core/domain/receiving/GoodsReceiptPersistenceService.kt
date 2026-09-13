package core.domain.receiving

import core.domain.model.GoodsReceipt
import core.domain.model.GoodsReceiptItem
import core.domain.model.ProductMaster
import core.domain.model.ProductUnit
import core.domain.model.StockBatch
import core.domain.persistence.CoreDatabase
import core.domain.persistence.GoodsReceiptDao
import core.domain.persistence.InventoryCostLayerDao
import core.domain.persistence.RoomTransactionRunner
import core.domain.persistence.StockBatchDao
import core.domain.persistence.StockMovementDao
import core.domain.persistence.TransactionRunner
import java.time.Instant
import java.time.ZoneOffset

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
     *
     * @throws IllegalStateException If the receipt has already been committed,
     * has already produced cost layers/movements, or domain validation fails.
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
            /*
             * 1. Idempotency guard.
             *
             * A retry after a successful database commit must not create another
             * receipt, movement, batch, or cost layer.
             */
            val existingReceipt = goodsReceiptDao.getReceiptById(receipt.id)
                ?: goodsReceiptDao.getReceiptByNumber(receipt.receiptNumber)

            if (existingReceipt != null && existingReceipt.isCommitted) {
                throw IllegalStateException(
                    "Goods receipt '${receipt.receiptNumber}' " +
                        "(id=${receipt.id}) is already committed and cannot be re-committed."
                )
            }

            /*
             * A committed receipt should have both of these physical/financial
             * consequences. If either already exists while the receipt itself
             * is not marked committed, treat the state as an interrupted or
             * duplicate transaction rather than silently creating more stock.
             */
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

            /*
             * 2. Load existing batches for all products represented by this
             * receipt. GoodsReceiptService uses these to resolve whether a
             * physical StockBatch already exists.
             */
            val productIds = items
                .map { it.productId }
                .toSet()

            val existingBatches = productIds
                .flatMap { productId ->
                    stockBatchDao.getBatchesForProduct(productId)
                }

            /*
             * 3. Resolve canonical base units.
             *
             * ProductUnit already carries the product relationship and the
             * isBaseUnit flag, so the persistence layer does not invent a second
             * base-unit source.
             *
             * A product must have exactly one base unit in the supplied map.
             * Duplicate base units are rejected rather than choosing one
             * arbitrarily.
             */
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

            /*
             * 4. The current domain service expects a reference calendar date
             * in YYYYMMDD form.
             *
             * GoodsReceipt.receivedAt is the authoritative timestamp already
             * carried by the receipt. Convert it to a deterministic UTC calendar
             * date here rather than inventing another date field.
             *
             * Facility-local calendar handling can be hardened later if the
             * architecture introduces an explicit facility timezone.
             */
            val referenceDateInt = epochMillisToDateInt(receipt.receivedAt)

            /*
             * 5. Prepare the domain result.
             *
             * The current GoodsReceiptService API returns ReceivingResult.
             * Do not recreate the obsolete GoodsReceiptCommitBundle abstraction.
             */
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

            /*
             * 6. Persist only a successful domain result.
             *
             * A ReceivingResult.Failure is converted into an exception so the
             * transaction cannot accidentally commit a partially prepared
             * receipt.
             */
            val success = when (result) {
                is ReceivingResult.Success -> result

                is ReceivingResult.Failure -> {
                    throw IllegalArgumentException(
                        buildReceivingFailureMessage(result)
                    )
                }
            }

            /*
             * 7. Persist the complete receiving transaction.
             *
             * All writes occur inside the same transactionRunner boundary.
             * If any write fails, the transaction must roll back.
             */
            if (existingReceipt == null) {
                goodsReceiptDao.insertReceipt(success.committedReceipt)
                goodsReceiptDao.insertReceiptItems(items)
            } else {
                /*
                 * A non-committed existing receipt represents an existing draft.
                 * Update it to the committed domain representation.
                 *
                 * Never overwrite existing committed historical data because
                 * that case was rejected above.
                 */
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

            /*
             * Return the actual domain result, including warnings, rather than
             * constructing a second persistence-specific result object.
             */
            success
        }
    }

    /**
     * Converts epoch milliseconds into the YYYYMMDD integer representation
     * required by GoodsReceiptService.
     *
     * UTC is intentionally used here because GoodsReceipt currently stores an
     * instant and the model does not yet expose an explicit facility timezone.
     */
    private fun epochMillisToDateInt(epochMillis: Long): Int {
        val date = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

        return (date.year * 10_000) +
            (date.monthValue * 100) +
            date.dayOfMonth
    }

    /**
     * Creates a stable diagnostic message for a failed receiving-domain result.
     *
     * The exact error types remain owned by GoodsReceiptService; this persistence
     * layer only needs to prevent a failed domain result from being persisted.
     */
    private fun buildReceivingFailureMessage(
        failure: ReceivingResult.Failure
    ): String {
        val errors = failure.errors.joinToString(separator = "; ") {
            it.toString()
        }

        return if (failure.warnings.isEmpty()) {
            "Goods receipt '${failure.receiptId}' failed validation: $errors"
        } else {
            val warnings = failure.warnings.joinToString(separator = "; ") {
                it.toString()
            }

            "Goods receipt '${failure.receiptId}' failed validation: " +
                "$errors. Warnings: $warnings"
        }
    }
}
