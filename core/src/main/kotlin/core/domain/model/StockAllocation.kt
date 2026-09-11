package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an explicit, immutable bridge connecting physical stock consumption
 * to its financial acquisition-cost layer and calculating exact Cost of Goods Sold (COGS).
 *
 * Core Concept & Separation of Concerns:
 * - Answers: "Which specific [InventoryCostLayer] supplied this portion of physical stock,
 *   what was its acquisition unit cost, and what was the resulting COGS for this transaction item?"
 * - [StockMovement] records the physical event ("10 units left the building").
 * - [StockAllocation] records the financial accounting truth ("5 units from Layer A @ 10 KSh + 5 units from Layer B @ 12 KSh").
 * - Connects: Physical Batch -> Cost Layer -> Consumed Quantity -> Exact COGS.
 * - References the authoritative cost layer ([inventoryCostLayerId]) without duplicating the layer entity.
 * - Historical immutability: Once committed, allocation records are permanent audit evidence.
 *   They are never updated or deleted in-place.
 *
 * Exact Monetary & Quantity Invariants:
 * - [allocatedQuantity] must be strictly positive (> 0).
 * - [acquisitionUnitCost] is the exact historical purchase unit cost ([Money]).
 * - [allocatedCost] is the exact COGS contribution ([Money]).
 * - Zero floating-point types (no Double, Float, or BigDecimal).
 */
@Entity(
    tableName = "stock_allocations",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["consumption_transaction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SaleItem::class,
            parentColumns = ["id"],
            childColumns = ["consumption_item_id"],
            onDelete = ForeignKey.RESTRICT
        ),
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
            entity = InventoryCostLayer::class,
            parentColumns = ["id"],
            childColumns = ["inventory_cost_layer_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["consumption_transaction_id"]),
        Index(value = ["consumption_item_id"]),
        Index(value = ["product_id"]),
        Index(value = ["stock_batch_id"]),
        Index(value = ["inventory_cost_layer_id"]),
        Index(value = ["allocated_at"])
    ]
)
data class StockAllocation(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "consumption_transaction_id")
    val consumptionTransactionId: String,

    @ColumnInfo(name = "consumption_item_id")
    val consumptionItemId: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String,

    @ColumnInfo(name = "inventory_cost_layer_id")
    val inventoryCostLayerId: String,

    @Embedded(prefix = "allocated_quantity_")
    val allocatedQuantity: Quantity,

    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: Money,

    @ColumnInfo(name = "allocated_cost")
    val allocatedCost: Money,

    @ColumnInfo(name = "allocated_at")
    val allocatedAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "StockAllocation id must not be blank or contain leading/trailing whitespace"
        }
        require(consumptionTransactionId.isNotBlank() && consumptionTransactionId.trim() == consumptionTransactionId) {
            "StockAllocation consumptionTransactionId must not be blank or contain leading/trailing whitespace"
        }
        require(consumptionItemId.isNotBlank() && consumptionItemId.trim() == consumptionItemId) {
            "StockAllocation consumptionItemId must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "StockAllocation productId must not be blank or contain leading/trailing whitespace"
        }
        require(stockBatchId.isNotBlank() && stockBatchId.trim() == stockBatchId) {
            "StockAllocation stockBatchId must not be blank or contain leading/trailing whitespace"
        }
        require(inventoryCostLayerId.isNotBlank() && inventoryCostLayerId.trim() == inventoryCostLayerId) {
            "StockAllocation inventoryCostLayerId must not be blank or contain leading/trailing whitespace"
        }
        require(allocatedQuantity.isPositive) {
            "StockAllocation allocatedQuantity must be strictly positive (> 0), got: ${allocatedQuantity.storageUnits}"
        }
        require(acquisitionUnitCost.amountMinorUnits >= 0L) {
            "StockAllocation acquisitionUnitCost must not be negative, got: ${acquisitionUnitCost.amountMinorUnits}"
        }
        require(allocatedCost.amountMinorUnits >= 0L) {
            "StockAllocation allocatedCost must not be negative, got: ${allocatedCost.amountMinorUnits}"
        }
        require(allocatedAt > 0L) {
            "StockAllocation allocatedAt must be a positive epoch timestamp, got: $allocatedAt (id=$id)"
        }
        require(createdAt > 0L) {
            "StockAllocation createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
    }
}
