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
 * Atomic persistence boundary for the receiving workflow.
 *
 * Responsibilities:
 * - Enforce one database transaction for the complete receiving event.
 * - Perform deterministic idempotency checks.
 * - Load the existing physical batches required by GoodsReceiptService.
 * - Resolve the canonical base-unit mapping required by receiving.
 * - Invoke GoodsReceiptService for domain preparation.
 * - Persist receipt + receipt items + new batches + cost layers + movements
 *   atomically.
 *
 * Architectural boundaries:
 * - GoodsReceiptValidation owns receiving-domain validation rules.
 * - GoodsReceiptService owns deterministic receiving preparation.
 * - This service owns persistence orchestration and transaction boundaries.
 * - StockMovement remains the physical stock-flow authority.
 * - InventoryCostLayer remains the acquisition-cost representation.
 *
 * This class must not:
 * - implement FEFO;
 * - calculate COGS;
 * - become a second inventory ledger;
 * - create alternative quantity semantics;
 * - silently recover from partial persistence failures.
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
     * Atomically commits a GoodsReceipt and its associated items.
     *
     * Idempotency is enforced before any new persistence occurs.
     *
     * A successful return means the complete receiving bundle was persisted
     * inside the same transaction.
     *
     * A failure is returned as ReceivingResult.Failure rather than being used
     * as normal control flow through unchecked exceptions.
     *
     * Database-level constraint failures may still throw and must propagate:
     * they indicate a persistence invariant was violated and therefore must
     * cause the transaction to roll back.
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
             * -------------------------------------------------------------
             * 1. Validate receipt identity/state before loading inventory.
             * -------------------------------------------------------------
             */
            val structuralErrors = GoodsReceiptValidation.validateReceipt(
                receipt = receipt,
                items = items
            )

            if (structuralErrors.isNotEmpty()) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = structuralErrors
                )
            }

            /*
             * -------------------------------------------------------------
             * 2. Resolve the persisted receipt identity.
             *
             * Receipt ID is the primary identity.
             * Receipt number is a historical/business reference.
             *
             * If either identifies an already committed receipt, the
             * operation must not create another receiving event.
             * -------------------------------------------------------------
             */
            val existingReceiptById =
                goodsReceiptDao.getReceiptById(receipt.id)

            val existingReceiptByNumber =
                goodsReceiptDao.getReceiptByNumber(receipt.receiptNumber)

            if (
                existingReceiptById != null &&
                existingReceiptById.id != receipt.id
            ) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.ReceiptIdentityConflict(
                            receiptId = receipt.id,
                            receiptNumber = receipt.receiptNumber,
                            persistedReceiptId = existingReceiptById.id
                        )
                    )
                )
            }

            if (
                existingReceiptByNumber != null &&
                existingReceiptByNumber.id != receipt.id
            ) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.ReceiptNumberConflict(
                            receiptNumber = receipt.receiptNumber,
                            persistedReceiptId = existingReceiptByNumber.id
                        )
                    )
                )
            }

            val existingReceipt =
                existingReceiptById ?: existingReceiptByNumber

            if (existingReceipt?.isCommitted == true) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.AlreadyCommitted(
                            existingReceipt.id
                        )
                    )
                )
            }

            if (existingReceipt?.isVoided == true) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.ReceiptVoided(
                            existingReceipt.id
                        )
                    )
                )
            }

            /*
             * -------------------------------------------------------------
             * 3. Idempotency checks for downstream receiving artefacts.
             *
             * GoodsReceiptService creates StockMovement.sourceTransactionRef
             * using receipt.id. The persistence boundary therefore checks
             * the same identity rather than receiptNumber.
             *
             * Cost layers retain receipt.receiptNumber as their historical
             * sourceReceiptRef, so both representations are checked where
             * their respective models define them.
             * -------------------------------------------------------------
             */
            val existingLayers =
                inventoryCostLayerDao.getLayersForReceiptRef(
                    receipt.receiptNumber
                )

            if (existingLayers.isNotEmpty()) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.DuplicateReceivingArtifacts(
                            receiptId = receipt.id,
                            detail =
                                "Inventory cost layers already exist for " +
                                    "receipt number '${receipt.receiptNumber}'."
                        )
                    )
                )
            }

            val existingMovements =
                stockMovementDao.getMovementsBySourceRef(
                    receipt.id
                )

            if (existingMovements.isNotEmpty()) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = listOf(
                        ReceivingError.DuplicateReceivingArtifacts(
                            receiptId = receipt.id,
                            detail =
                                "Stock movements already exist for " +
                                    "receipt id '${receipt.id}'."
                        )
                    )
                )
            }

            /*
             * -------------------------------------------------------------
             * 4. Resolve all product IDs represented by this receipt.
             * -------------------------------------------------------------
             */
            val productIds = items
                .map { it.productId }
                .toSet()

            /*
             * -------------------------------------------------------------
             * 5. Load all existing physical batches for the products.
             *
             * GoodsReceiptService decides whether a batch already exists
             * and creates only the missing physical identities.
             * -------------------------------------------------------------
             */
            val existingBatches = productIds
                .flatMap { productId ->
                    stockBatchDao.getBatchesForProduct(productId)
                }

            /*
             * -------------------------------------------------------------
             * 6. Resolve exactly one canonical base unit per product.
             *
             * Multiple canonical base units would make the receiving
             * quantity authority ambiguous, so this is a hard failure.
             * -------------------------------------------------------------
             */
            val baseUnitsByProductId = mutableMapOf<String, ProductUnit>()

            for (productId in productIds) {

                val productUnits = unitsById.values.filter { unit ->
                    unit.productId == productId
                }

                val baseUnits = productUnits.filter { unit ->
                    unit.isBaseUnit
                }

                when {
                    baseUnits.isEmpty() -> {
                        return@runInTransaction ReceivingResult.Failure(
                            errors = listOf(
                                ReceivingError.BaseUnitNotFound(
                                    productId = productId,
                                    lineIndex = items
                                        .firstOrNull {
                                            it.productId == productId
                                        }
                                        ?.lineIndex
                                        ?: -1
                                )
                            )
                        )
                    }

                    baseUnits.size > 1 -> {
                        return@runInTransaction ReceivingResult.Failure(
                            errors = listOf(
                                ReceivingError.MultipleBaseUnits(
                                    productId = productId,
                                    count = baseUnits.size
                                )
                            )
                        )
                    }

                    else -> {
                        baseUnitsByProductId[productId] =
                            baseUnits.single()
                    }
                }
            }

            /*
             * -------------------------------------------------------------
             * 7. Validate every item against the resolved product/unit/base
             *    unit context before asking GoodsReceiptService to prepare
             *    the commit.
             *
             * This is the File-7 validation boundary being consumed here.
             * -------------------------------------------------------------
             */
            val validationErrors = mutableListOf<ReceivingError>()

            for (item in items) {

                val product = productsById[item.productId]

                val receivingUnit =
                    unitsById[item.receivingUnitId]

                val baseUnit =
                    baseUnitsByProductId[item.productId]

                validationErrors +=
                    GoodsReceiptValidation.validateItem(
                        item = item,
                        product = product,
                        receivingUnit = receivingUnit,
                        baseUnit = baseUnit
                    )
            }

            if (validationErrors.isNotEmpty()) {
                return@runInTransaction ReceivingResult.Failure(
                    errors = validationErrors
                )
            }

            /*
             * -------------------------------------------------------------
             * 8. Determine the calendar reference date.
             *
             * This is a receiving-time calendar concern only. It does not
             * redefine expiry policy; ExpiryPolicy remains the later
             * authority for stock-exit eligibility.
             * -------------------------------------------------------------
             */
            val referenceDateInt =
                epochMillisToDateInt(receipt.receivedAt)

            /*
             * -------------------------------------------------------------
             * 9. Prepare the complete receiving bundle.
             *
             * No persistence should occur before this succeeds.
             * -------------------------------------------------------------
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

            val success = when (result) {

                is ReceivingResult.Success -> {
                    result
                }

                is ReceivingResult.Failure -> {
                    return@runInTransaction result
                }
            }

            /*
             * -------------------------------------------------------------
             * 10. Persist the entire receiving event.
             *
             * Ordering:
             *
             * GoodsReceipt header/items
             *        ↓
             * StockBatch
             *        ↓
             * InventoryCostLayer
             *        ↓
             * StockMovement
             *
             * Everything occurs inside the same transaction.
             *
             * If any DAO operation fails, the transaction runner must roll
             * the entire operation back.
             * -------------------------------------------------------------
             */
            if (existingReceipt == null) {

                goodsReceiptDao.insertReceipt(
                    success.committedReceipt
                )

                goodsReceiptDao.insertReceiptItems(
                    items
                )

            } else {

                goodsReceiptDao.updateReceipt(
                    success.committedReceipt
                )

                val existingItems =
                    goodsReceiptDao.getItemsForReceipt(
                        receipt.id
                    )

                if (existingItems.isEmpty()) {
                    goodsReceiptDao.insertReceiptItems(
                        items
                    )
                } else {
                    /*
                     * Existing items are preserved during an idempotent
                     * continuation. We do not silently replace historical
                     * receipt lines.
                     */
                }
            }

            if (success.newBatches.isNotEmpty()) {
                stockBatchDao.insertBatches(
                    success.newBatches
                )
            }

            if (success.costLayers.isNotEmpty()) {
                inventoryCostLayerDao.insertLayers(
                    success.costLayers
                )
            }

            if (success.stockMovements.isNotEmpty()) {
                stockMovementDao.insertMovements(
                    success.stockMovements
                )
            }

            /*
             * The successful result is returned only after every persistence
             * stage has completed.
             */
            success
        }
    }

    /**
     * Converts epoch milliseconds into YYYYMMDD.
     *
     * UTC is deliberately used so the same receiving timestamp does not
     * produce different expiry/reference dates on different device
     * time zones.
     *
     * Calendar is used because the application minimum SDK is API 24.
     */
    private fun epochMillisToDateInt(
        epochMillis: Long
    ): Int {

        val calendar =
            Calendar.getInstance(
                TimeZone.getTimeZone("UTC")
            ).apply {
                timeInMillis = epochMillis
            }

        return (calendar.get(Calendar.YEAR) * 10_000) +
            ((calendar.get(Calendar.MONTH) + 1) * 100) +
            calendar.get(Calendar.DAY_OF_MONTH)
    }
}
