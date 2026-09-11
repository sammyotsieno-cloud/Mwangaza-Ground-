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

/**
 * Service orchestrating the atomic persistence of inventory receiving transactions.
 *
 * Transaction Boundary & Invariants:
 * - Enforces strict all-or-nothing atomicity for:
 *     1. Committed [GoodsReceipt] state update
 *     2. [GoodsReceiptItem] line items
 *     3. Newly created physical [StockBatch] records
 *     4. Discrete financial [InventoryCostLayer] records
 *     5. Positive [StockMovement] records ([StockMovement.TYPE_PURCHASE_RECEIPT])
 * - Rejects duplicate commit attempts (idempotency guard on receipt status and source transaction references).
 * - Leaves zero partial state if any validation, batch resolution, or database constraint fails.
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
     * @param receipt The goods receipt header to commit.
     * @param items The line items belonging to this receipt.
     * @param productsById Map of canonical products involved in the receipt.
     * @param unitsById Map of commercial and base units used in the receipt.
     * @param commitTimestamp Epoch millisecond timestamp of the commit.
     * @return The [GoodsReceiptCommitBundle] containing all persisted entities.
     * @throws IllegalStateException If the receipt is already committed or contains duplicate records.
     * @throws IllegalArgumentException If receipt data, units, or batches are invalid.
     */
    fun commitReceipt(
        receipt: GoodsReceipt,
        items: List<GoodsReceiptItem>,
        productsById: Map<String, ProductMaster>,
        unitsById: Map<String, ProductUnit>,
        commitTimestamp: Long
    ): GoodsReceiptCommitBundle {
        return transactionRunner.runInTransaction {
            // 1. Idempotency Check: Verify receipt is not already committed
            val existingReceipt = goodsReceiptDao.getReceiptById(receipt.id)
                ?: goodsReceiptDao.getReceiptByNumber(receipt.receiptNumber)

            if (existingReceipt != null && existingReceipt.isCommitted) {
                throw IllegalStateException(
                    "Goods receipt '${receipt.receiptNumber}' (id=${receipt.id}) is already committed and cannot be re-committed."
                )
            }

            // Verify no cost layers or movements already exist with this receipt reference
            val existingLayers = inventoryCostLayerDao.getLayersForReceiptRef(receipt.receiptNumber)
            if (existingLayers.isNotEmpty()) {
                throw IllegalStateException(
                    "Duplicate receiving attempt detected: Cost layers already exist for receipt '${receipt.receiptNumber}'."
                )
            }

            val existingMovements = stockMovementDao.getMovementsBySourceRef(receipt.receiptNumber)
            if (existingMovements.isNotEmpty()) {
                throw IllegalStateException(
                    "Duplicate receiving attempt detected: Stock movements already exist for receipt '${receipt.receiptNumber}'."
                )
            }

            // 2. Load existing batches for all products in this receipt to enable deterministic deduplication
            val productIds = items.map { it.productId }.toSet()
            val existingBatches = productIds.flatMap { stockBatchDao.getBatchesForProduct(it) }

            // 3. Prepare the atomic commit bundle using domain logic
            val bundle = GoodsReceiptService.prepareCommit(
                receipt = receipt,
                items = items,
                productsById = productsById,
                unitsById = unitsById,
                existingBatches = existingBatches,
                commitTimestamp = commitTimestamp
            )

            // 4. Atomic persistence operations
            if (existingReceipt == null) {
                goodsReceiptDao.insertReceipt(bundle.committedReceipt)
                goodsReceiptDao.insertReceiptItems(items)
            } else {
                goodsReceiptDao.updateReceipt(bundle.committedReceipt)
                val existingItems = goodsReceiptDao.getItemsForReceipt(receipt.id)
                if (existingItems.isEmpty()) {
                    goodsReceiptDao.insertReceiptItems(items)
                }
            }

            if (bundle.newBatches.isNotEmpty()) {
                stockBatchDao.insertBatches(bundle.newBatches)
            }

            if (bundle.newCostLayers.isNotEmpty()) {
                inventoryCostLayerDao.insertLayers(bundle.newCostLayers)
            }

            if (bundle.newMovements.isNotEmpty()) {
                stockMovementDao.insertMovements(bundle.newMovements)
            }

            bundle
        }
    }
}
