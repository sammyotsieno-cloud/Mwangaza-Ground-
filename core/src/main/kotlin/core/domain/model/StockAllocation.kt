package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Room entity representing the immutable financial allocation of physical
 * stock consumption to an InventoryCostLayer.
 *
 * ============================================================================
 * ARCHITECTURAL ROLE
 * ============================================================================
 *
 * StockMovement:
 *
 *     "How much physical stock moved?"
 *
 * InventoryCostLayer:
 *
 *     "At what historical acquisition cost was that stock acquired,
 *      and how much of that acquisition pool remains?"
 *
 * StockAllocation:
 *
 *     "Which acquisition-cost layer supplied this consumed quantity,
 *      and what exact COGS did that quantity contribute?"
 *
 * Therefore StockAllocation is the BRIDGE between:
 *
 *     physical stock consumption
 *                  ↓
 *            cost-layer depletion
 *                  ↓
 *                COGS
 *
 * It is NOT:
 *
 * - a physical stock ledger;
 * - a stock-balance table;
 * - a StockMovement replacement;
 * - a cost-layer balance;
 * - a selling-price record;
 * - a FEFO engine;
 * - a sales transaction;
 * - a mutable accounting balance.
 *
 * ============================================================================
 * IMMUTABILITY
 * ============================================================================
 *
 * A committed allocation is historical accounting evidence.
 *
 * Once persisted, it must not be edited or deleted merely to correct a later
 * calculation.
 *
 * Corrections/reversals must be represented by the appropriate higher-level
 * business workflow rather than rewriting historical allocation evidence.
 *
 * This entity contains only val properties and has no mutation methods.
 * Database-level append-only enforcement remains a persistence/workflow
 * responsibility.
 *
 * ============================================================================
 * EXACT QUANTITY AND MONEY
 * ============================================================================
 *
 * allocatedQuantity is expressed using Quantity.
 *
 * acquisitionUnitCost and allocatedCost are expressed using Money.
 *
 * No floating-point arithmetic is used.
 *
 * The fundamental financial invariant is:
 *
 *     allocatedCost
 *         =
 *     allocatedQuantity × acquisitionUnitCost
 *
 * However, Quantity and Money use different storage scales, so the actual
 * multiplication must account for both scales.
 *
 * Let:
 *
 *     quantity = Q / 10^quantityScale
 *     unitCost = C / 10^moneyScale
 *
 * Then:
 *
 *     cost = Q × C
 *            -------------------------------
 *            10^(quantityScale + moneyScale)
 *
 * The result must be exactly representable in Money's minor-unit scale.
 *
 * No rounding, truncation, or floating-point approximation is permitted.
 *
 * ============================================================================
 * CROSS-ENTITY IDENTITY
 * ============================================================================
 *
 * The following relationship must hold:
 *
 *     StockAllocation.productId
 *         == InventoryCostLayer.productId
 *
 *     StockAllocation.stockBatchId
 *         == InventoryCostLayer.stockBatchId
 *
 * The entity cannot independently verify those relationships because that
 * would require loading another entity.
 *
 * Therefore the allocation service/persistence transaction must verify them
 * before insertion.
 *
 * Likewise:
 *
 *     allocatedQuantity <= InventoryCostLayer.remainingQuantity
 *
 * must be enforced by the allocation/depletion workflow, preferably through
 * the guarded DAO operation on InventoryCostLayer.
 *
 * This entity therefore enforces all invariants that can be established from
 * its own immutable values and leaves cross-entity transactional invariants
 * to the service layer.
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
     * Sale/consumption transaction to which this allocation belongs.
     */
    @ColumnInfo(name = "consumption_transaction_id")
    val consumptionTransactionId: String,

    /**
     * Specific sale/consumption line supplied by the allocation.
     */
    @ColumnInfo(name = "consumption_item_id")
    val consumptionItemId: String,

    /**
     * Product being consumed.
     */
    @ColumnInfo(name = "product_id")
    val productId: String,

    /**
     * Physical batch from which the consumed stock originated.
     */
    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String,

    /**
     * Acquisition-cost layer supplying this allocation.
     */
    @ColumnInfo(name = "inventory_cost_layer_id")
    val inventoryCostLayerId: String,

    /**
     * Physical quantity consumed from the referenced cost layer.
     *
     * This is always positive.
     *
     * The corresponding StockMovement normally records the physical
     * consumption as a negative quantity.
     */
    @Embedded(prefix = "allocated_quantity_")
    val allocatedQuantity: Quantity,

    /**
     * Historical acquisition cost per canonical quantity represented by the
     * referenced cost layer.
     *
     * This value is copied as an immutable historical snapshot so the
     * allocation remains independently auditable even though the authoritative
     * acquisition layer is referenced by inventoryCostLayerId.
     */
    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: Money,

    /**
     * Exact COGS contribution of this allocation.
     */
    @ColumnInfo(name = "allocated_cost")
    val allocatedCost: Money,

    /**
     * Timestamp at which the allocation was committed.
     */
    @ColumnInfo(name = "allocated_at")
    val allocatedAt: Long,

    /**
     * Database creation timestamp.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {

    init {

        require(id.isNotBlank() && id.trim() == id) {
            "StockAllocation id must not be blank or contain " +
                "leading/trailing whitespace"
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
            "StockAllocation consumptionItemId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(
            productId.isNotBlank() &&
                productId.trim() == productId
        ) {
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

        /*
         * Allocation quantity is deliberately positive.
         *
         * Direction belongs to StockMovement.
         *
         * This distinction prevents the financial allocation from becoming
         * another physical stock-flow ledger.
         */
        require(allocatedQuantity.isPositive) {
            "StockAllocation allocatedQuantity must be strictly positive " +
                "(> 0), got: ${allocatedQuantity.storageUnits}"
        }

        /*
         * Historical acquisition cost cannot be negative.
         *
         * Zero remains valid for legitimate zero-cost acquisition.
         */
        require(acquisitionUnitCost.amountMinorUnits >= 0L) {
            "StockAllocation acquisitionUnitCost must not be negative, " +
                "got: ${acquisitionUnitCost.amountMinorUnits}"
        }

        /*
         * COGS contribution cannot be negative.
         */
        require(allocatedCost.amountMinorUnits >= 0L) {
            "StockAllocation allocatedCost must not be negative, " +
                "got: ${allocatedCost.amountMinorUnits}"
        }

        /*
         * The allocation's COGS must be mathematically consistent with its
         * quantity and historical acquisition unit cost.
         *
         * Money stores minor units, while Quantity stores scaled units.
         *
         * Therefore:
         *
         *     expectedMinorUnits =
         *         allocatedQuantity.storageUnits
         *         × acquisitionUnitCost.amountMinorUnits
         *         ÷ quantityScaleMultiplier
         *
         * Exact division is mandatory.
         *
         * A non-exact result means the requested allocation cannot be
         * represented in Money's minor-unit precision without rounding.
         *
         * We reject rather than silently round.
         */
        val quantityScaleMultiplier =
            allocatedQuantity.scale.multiplier.toLong()

        require(quantityScaleMultiplier > 0L) {
            "StockAllocation quantity scale multiplier must be positive"
        }

        val expectedCostNumerator =
            BigInteger.valueOf(allocatedQuantity.storageUnits)
                .multiply(
                    BigInteger.valueOf(
                        acquisitionUnitCost.amountMinorUnits
                    )
                )

        val divisor = BigInteger.valueOf(quantityScaleMultiplier)

        val division = expectedCostNumerator.divideAndRemainder(divisor)

        require(division[1].signum() == 0) {
            "StockAllocation allocated quantity and acquisition unit cost " +
                "produce a COGS value that is not exactly representable in " +
                "Money minor units; rounding/truncation is not permitted"
        }

        val expectedAllocatedCost = try {
            division[0].longValueExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "StockAllocation expected allocated cost exceeds Long " +
                    "Money storage capacity",
                e
            )
        }

        require(
            allocatedCost.amountMinorUnits == expectedAllocatedCost
        ) {
            "StockAllocation allocatedCost " +
                "(${allocatedCost.amountMinorUnits}) does not equal the " +
                "exact quantity × acquisitionUnitCost result " +
                "($expectedAllocatedCost)"
        }

        require(allocatedAt > 0L) {
            "StockAllocation allocatedAt must be a positive epoch " +
                "timestamp, got: $allocatedAt (id=$id)"
        }

        require(createdAt > 0L) {
            "StockAllocation createdAt must be a positive epoch " +
                "timestamp, got: $createdAt (id=$id)"
        }
    }
}
