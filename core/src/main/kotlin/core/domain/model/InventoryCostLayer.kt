package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a discrete financial acquisition tranche used to value stock
 * and calculate future Cost of Goods Sold (COGS).
 *
 * Core Concept:
 * - Answers: "At what specific acquisition unit cost was this quantity of stock acquired, and how much of this cost pool remains?"
 * - Distinct from [StockBatch]:
 *     * [StockBatch] answers: "Which physical lot is this?"
 *     * [InventoryCostLayer] answers: "What did this quantity cost to acquire?"
 * - A single physical [StockBatch] can have multiple [InventoryCostLayer] records if acquired at different
 *   times, prices, or from different purchase receipts.
 *
 * Immutable Acquisition Facts vs. Mutable Operational State:
 * 1. Immutable Historical Acquisition Facts:
 *    - [id], [productId], [stockBatchId], [supplierId], [initialQuantity], [acquisitionUnitCost],
 *      [acquiredAt], [sourceReceiptRef], and [createdAt].
 *    - Once created and committed, these fields represent immutable historical procurement facts.
 *      They must NEVER be rewritten, edited in place, or altered by operational corrections.
 * 2. Mutable Operational State:
 *    - [remainingQuantity] and [updatedAt].
 *    - [remainingQuantity] is the current operational quantity remaining in this cost layer.
 *      It mutates operationally as stock is consumed (e.g. through future sales, damages, expiries)
 *      or restored through explicit transactional reversal/restoration mechanisms.
 *    - Historical immutability does NOT mean [remainingQuantity] is frozen; rather, it means
 *      historical acquisition facts cannot be rewritten to explain current balances.
 *
 * Invariants & Exactness:
 * - [acquisitionUnitCost] is an exact [Money] value object (Phase 1 KES minor units, 2 decimal places).
 * - Invariant: 0 <= [remainingQuantity] <= [initialQuantity].
 * - Invariant: [initialQuantity] and [remainingQuantity] must share the exact same [QuantityScale].
 * - Invariant: [initialQuantity] must be strictly positive (> 0).
 */
@Entity(
    tableName = "inventory_cost_layers",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StockBatch::class,
            parentColumns = ["id"],
            childColumns = ["stock_batch_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["stock_batch_id"]),
        Index(value = ["supplier_id"]),
        Index(value = ["acquired_at"])
    ]
)
data class InventoryCostLayer(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String,

    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    @Embedded(prefix = "initial_quantity_")
    val initialQuantity: Quantity,

    @Embedded(prefix = "remaining_quantity_")
    val remainingQuantity: Quantity,

    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: Money,

    @ColumnInfo(name = "acquired_at")
    val acquiredAt: Long,

    @ColumnInfo(name = "source_receipt_ref")
    val sourceReceiptRef: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "InventoryCostLayer id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "InventoryCostLayer productId must not be blank or contain leading/trailing whitespace"
        }
        require(stockBatchId.isNotBlank() && stockBatchId.trim() == stockBatchId) {
            "InventoryCostLayer stockBatchId must not be blank or contain leading/trailing whitespace"
        }
        if (supplierId != null) {
            require(supplierId.isNotBlank() && supplierId.trim() == supplierId) {
                "InventoryCostLayer supplierId must not be blank or contain whitespace if provided"
            }
        }
        require(initialQuantity.scale == remainingQuantity.scale) {
            "InventoryCostLayer initialQuantity scale (${initialQuantity.scale}) and remainingQuantity scale (${remainingQuantity.scale}) must match"
        }
        require(initialQuantity.storageUnits > 0L) {
            "InventoryCostLayer initialQuantity must be strictly positive (> 0), got: ${initialQuantity.storageUnits}"
        }
        require(remainingQuantity.storageUnits >= 0L) {
            "InventoryCostLayer remainingQuantity must be non-negative (>= 0), got: ${remainingQuantity.storageUnits}"
        }
        require(remainingQuantity.storageUnits <= initialQuantity.storageUnits) {
            "InventoryCostLayer remainingQuantity (${remainingQuantity.storageUnits}) must not exceed initialQuantity (${initialQuantity.storageUnits})"
        }
        require(acquisitionUnitCost.amountMinorUnits >= 0L) {
            "InventoryCostLayer acquisitionUnitCost must not be negative, got: ${acquisitionUnitCost.amountMinorUnits}"
        }
        require(acquiredAt > 0L) {
            "InventoryCostLayer acquiredAt must be a positive epoch timestamp, got: $acquiredAt (id=$id)"
        }
        require(createdAt > 0L) {
            "InventoryCostLayer createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "InventoryCostLayer updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "InventoryCostLayer updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
