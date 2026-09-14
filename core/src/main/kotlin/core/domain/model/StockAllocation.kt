package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Room entity representing the immutable financial allocation that explains
 * how a physical stock consumption was assigned to a specific acquisition
 * cost layer.
 *
 * Architectural authority:
 *
 * - StockMovement answers:
 *     "What physical stock-flow event changed inventory?"
 *
 * - StockBatch answers:
 *     "Which physical lot did that stock belong to?"
 *
 * - InventoryCostLayer answers:
 *     "At what historical acquisition cost was that stock acquired,
 *      and how much of that acquisition-cost pool remains?"
 *
 * - StockAllocation answers:
 *     "Which acquisition-cost layer was consumed by this particular
 *      stock-exit line, in what quantity, and at what historical cost?"
 *
 * StockAllocation is therefore a financial bridge between physical
 * consumption and acquisition-cost accounting.
 *
 * StockAllocation MUST NOT become:
 * - a second physical stock ledger;
 * - a stock-balance authority;
 * - a replacement for StockMovement;
 * - a cost-layer balance;
 * - a FEFO engine;
 * - a sales transaction;
 * - a selling-price record;
 * - a mutable accounting balance.
 *
 * Physical stock direction belongs to StockMovement.
 * Cost-layer remaining quantity belongs to InventoryCostLayer.
 * FEFO selection belongs to FefoService.
 * COGS allocation orchestration belongs to the consumption workflow.
 *
 * ---------------------------------------------------------------------------
 * QUANTITY SEMANTICS
 * ---------------------------------------------------------------------------
 *
 * allocatedQuantity is always positive.
 *
 * StockAllocation describes how much stock was assigned to a cost layer;
 * it does not describe whether physical stock entered or left inventory.
 * Physical direction is represented by the corresponding StockMovement.
 *
 * allocatedQuantity MUST use the same QuantityScale as the corresponding
 * InventoryCostLayer quantity.
 *
 * ---------------------------------------------------------------------------
 * COST SEMANTICS
 * ---------------------------------------------------------------------------
 *
 * acquisitionUnitCost is the exact historical acquisition cost per canonical
 * quantity represented by the source InventoryCostLayer.
 *
 * allocatedCost is the exact monetary acquisition cost attributed to this
 * allocation.
 *
 * The authoritative invariant is:
 *
 *     allocatedCost =
 *         acquisitionUnitCost × allocatedQuantity
 *
 * Quantity is represented as:
 *
 *     physical quantity = storageUnits / 10^scale
 *
 * RationalCost represents exact monetary values as rational numbers and
 * therefore MUST NOT be converted to integer currency minor units merely
 * to perform authoritative accounting calculations.
 *
 * No Float, Double, truncation, or implicit monetary rounding is permitted.
 *
 * A mathematically fractional allocation cost is valid and MUST be retained
 * exactly. Display rounding and currency-settlement rounding belong outside
 * this entity.
 *
 * ---------------------------------------------------------------------------
 * CROSS-ENTITY INVARIANTS
 * ---------------------------------------------------------------------------
 *
 * The entity cannot independently verify database relationships such as:
 *
 *     allocation.productId == costLayer.productId
 *     allocation.stockBatchId == costLayer.stockBatchId
 *
 * Those invariants belong to the allocation/consumption transaction because
 * they require loading related entities.
 *
 * Likewise:
 *
 *     allocatedQuantity <= costLayer.remainingQuantity
 *
 * must be enforced by the consumption workflow and guarded persistence
 * operation, not by this entity alone.
 *
 * This entity nevertheless validates every invariant that can be established
 * from its own immutable values.
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

    /**
     * Sale/consumption transaction responsible for the physical stock exit.
     */
    @ColumnInfo(name = "consumption_transaction_id")
    val consumptionTransactionId: String,

    /**
     * Specific sale line that consumed the allocated quantity.
     */
    @ColumnInfo(name = "consumption_item_id")
    val consumptionItemId: String,

    /**
     * Product being consumed.
     *
     * Must match both the SaleItem product and the InventoryCostLayer
     * product at the consumption workflow boundary.
     */
    @ColumnInfo(name = "product_id")
    val productId: String,

    /**
     * Physical stock batch from which this quantity was consumed.
     *
     * Must match the InventoryCostLayer stockBatchId.
     */
    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String,

    /**
     * Historical acquisition-cost layer used to explain this portion of COGS.
     */
    @ColumnInfo(name = "inventory_cost_layer_id")
    val inventoryCostLayerId: String,

    /**
     * Positive canonical quantity assigned to this acquisition-cost layer.
     *
     * This is NOT a physical stock movement. Physical direction belongs
     * to StockMovement.
     */
    @Embedded(prefix = "allocated_quantity_")
    val allocatedQuantity: Quantity,

    /**
     * Exact historical acquisition cost per canonical quantity represented
     * by the source InventoryCostLayer.
     *
     * This is a snapshot, not a recalculated current cost.
     *
     * RationalCost is authoritative and preserves mathematically fractional
     * monetary values without display or settlement rounding.
     */
    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: RationalCost,

    /**
     * Exact acquisition cost attributed to this allocation.
     *
     * It MUST equal the exact mathematical value of:
     *
     *     acquisitionUnitCost × allocatedQuantity
     *
     * where:
     *
     *     allocatedQuantity =
     *         storageUnits / 10^quantityScale
     *
     * No rounding is permitted here.
     */
    @ColumnInfo(name = "allocated_cost")
    val allocatedCost: RationalCost,

    /**
     * Timestamp at which the allocation was created.
     */
    @ColumnInfo(name = "allocated_at")
    val allocatedAt: Long,

    /**
     * Database record creation timestamp.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {

    init {
        require(id.isNotBlank() && id.trim() == id) {
            "StockAllocation id must not be blank or contain leading/trailing whitespace"
        }

        require(
            consumptionTransactionId.isNotBlank() &&
                consumptionTransactionId.trim() == consumptionTransactionId
        ) {
            "StockAllocation consumptionTransactionId must not be blank " +
                "or contain leading/trailing whitespace"
        }

        require(
            consumptionItemId.isNotBlank() &&
                consumptionItemId.trim() == consumptionItemId
        ) {
            "StockAllocation consumptionItemId must not be blank " +
                "or contain leading/trailing whitespace"
        }

        require(productId.isNotBlank() && productId.trim() == productId) {
            "StockAllocation productId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(
            stockBatchId.isNotBlank() &&
                stockBatchId.trim() == stockBatchId
        ) {
            "StockAllocation stockBatchId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(
            inventoryCostLayerId.isNotBlank() &&
                inventoryCostLayerId.trim() == inventoryCostLayerId
        ) {
            "StockAllocation inventoryCostLayerId must not be blank or " +
                "contain leading/trailing whitespace"
        }

        require(allocatedQuantity.isPositive) {
            "StockAllocation allocatedQuantity must be strictly positive " +
                "(> 0), got: $allocatedQuantity"
        }

        require(acquisitionUnitCost.isNonNegative) {
            "StockAllocation acquisitionUnitCost must not be negative, " +
                "got: $acquisitionUnitCost"
        }

        require(allocatedCost.isNonNegative) {
            "StockAllocation allocatedCost must not be negative, " +
                "got: $allocatedCost"
        }

        require(allocatedAt > 0L) {
            "StockAllocation allocatedAt must be a positive epoch timestamp, " +
                "got: $allocatedAt"
        }

        require(createdAt > 0L) {
            "StockAllocation createdAt must be a positive epoch timestamp, " +
                "got: $createdAt"
        }

        /*
         * The entity is the final immutable guard for the exact mathematical
         * relationship between quantity, historical acquisition cost,
         * and recorded allocation cost.
         */
        requireExactAllocatedCost()
    }

    /**
     * Verifies the exact mathematical invariant:
     *
     *     allocatedCost =
     *         acquisitionUnitCost × allocatedQuantity
     *
     * Quantity uses decimal storage units:
     *
     *     physical quantity = storageUnits / 10^scale
     *
     * Therefore the expected exact cost is:
     *
     *     acquisitionUnitCost ×
     *         (storageUnits / 10^scale)
     *
     * The resulting value remains a RationalCost.
     *
     * This method deliberately does NOT convert the result to integer
     * currency minor units and does NOT round.
     */
    private fun requireExactAllocatedCost() {

        val scaleFactor = BigInteger.TEN.pow(
            allocatedQuantity.scale.scale
        )

        val expectedAllocatedCost =
            acquisitionUnitCost.multiply(
                numerator = BigInteger.valueOf(
                    allocatedQuantity.storageUnits
                ),
                denominator = scaleFactor
            )

        require(expectedAllocatedCost == allocatedCost) {
            "StockAllocation allocatedCost does not equal exact quantity × " +
                "acquisitionUnitCost: " +
                "expected=$expectedAllocatedCost, " +
                "actual=$allocatedCost"
        }
    }
}
