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
 * acquisitionUnitCost is a historical snapshot of the acquisition cost
 * represented by the InventoryCostLayer at allocation time.
 *
 * allocatedCost is the exact monetary acquisition cost attributed to this
 * allocation.
 *
 * The authoritative invariant is:
 *
 *     allocatedCost =
 *         exact mathematical value of
 *         allocatedQuantity × acquisitionUnitCost
 *
 * Quantity is represented as:
 *
 *     physical quantity = storageUnits / 10^scale
 *
 * Money is represented as integer currency minor units.
 *
 * Therefore the exact allocation cost in minor units is:
 *
 *     storageUnits × acquisitionUnitCost.amountMinorUnits
 *     ---------------------------------------------------
 *                         10^quantityScale
 *
 * The calculation MUST use exact integer arithmetic.
 *
 * No Float, Double, truncation, or implicit monetary rounding is permitted.
 *
 * If the mathematical result is not an integral number of currency
 * minor units, the allocation is rejected.
 *
 * This rejection is deliberate. StockAllocation has no independent monetary
 * residual field and therefore cannot safely represent an allocation whose
 * exact monetary value would require rounding.
 *
 * The receiving workflow is responsible for creating acquisition-cost
 * tranches whose monetary values conserve the authoritative receipt total.
 * The consumption workflow is responsible for allocating only quantities
 * whose exact cost can be represented by this immutable allocation record.
 *
 * StockAllocation therefore acts as a final accounting-integrity boundary;
 * it does not invent or discard money to make an allocation fit.
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
     * Historical acquisition cost per canonical quantity represented by
     * the source InventoryCostLayer.
     *
     * This is a snapshot, not a recalculated current cost.
     */
    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: Money,

    /**
     * Exact acquisition cost attributed to this allocation.
     *
     * It MUST equal the exact mathematical value of:
     *
     *     allocatedQuantity × acquisitionUnitCost
     *
     * after applying the QuantityScale denominator.
     *
     * No rounding is permitted.
     */
    @ColumnInfo(name = "allocated_cost")
    val allocatedCost: Money,

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

        require(acquisitionUnitCost.amountMinorUnits >= 0L) {
            "StockAllocation acquisitionUnitCost must not be negative, " +
                "got: ${acquisitionUnitCost.amountMinorUnits}"
        }

        require(allocatedCost.amountMinorUnits >= 0L) {
            "StockAllocation allocatedCost must not be negative, " +
                "got: ${allocatedCost.amountMinorUnits}"
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
         * The entity is the final immutable guard for the monetary
         * relationship between quantity, historical acquisition cost,
         * and recorded allocation cost.
         */
        requireExactAllocatedCost()
    }

    /**
     * Verifies the exact monetary invariant:
     *
     *     allocatedCost =
     *         allocatedQuantity × acquisitionUnitCost
     *
     * Quantity uses decimal storage units:
     *
     *     physical quantity = storageUnits / 10^scale
     *
     * Therefore:
     *
     *     expectedCostMinorUnits =
     *         storageUnits × unitCostMinorUnits / 10^scale
     *
     * The division MUST have zero remainder.
     *
     * This method intentionally rejects non-integral monetary results rather
     * than rounding because StockAllocation has no residual-money authority.
     */
    private fun requireExactAllocatedCost() {

        val scaleFactor = BigInteger.TEN.pow(
            allocatedQuantity.scale.scale
        )

        val numerator =
            BigInteger.valueOf(allocatedQuantity.storageUnits)
                .multiply(
                    BigInteger.valueOf(
                        acquisitionUnitCost.amountMinorUnits
                    )
                )

        val quotientAndRemainder =
            numerator.divideAndRemainder(scaleFactor)

        val expectedMinorUnits =
            quotientAndRemainder[0]

        val remainder =
            quotientAndRemainder[1]

        require(remainder == BigInteger.ZERO) {
            "StockAllocation allocated cost is not exactly representable " +
                "in currency minor units: " +
                "quantity=$allocatedQuantity, " +
                "acquisitionUnitCost=$acquisitionUnitCost"
        }

        val actualMinorUnits =
            BigInteger.valueOf(
                allocatedCost.amountMinorUnits
            )

        require(expectedMinorUnits == actualMinorUnits) {
            "StockAllocation allocatedCost does not equal exact quantity × " +
                "acquisitionUnitCost: " +
                "expected=${expectedMinorUnits} minor units, " +
                "actual=${actualMinorUnits} minor units"
        }
    }
}
