package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing one discrete financial acquisition tranche.
 *
 * Architectural authority:
 *
 * - StockBatch answers:
 *     "Which physical lot is this stock from?"
 *
 * - InventoryCostLayer answers:
 *     "At what acquisition cost was this quantity acquired,
 *      and how much of that acquisition-cost pool remains?"
 *
 * - StockMovement answers:
 *     "What physical stock-flow event changed inventory?"
 *
 * - StockAllocation answers:
 *     "Which cost layers were consumed to explain a later
 *      stock-exit/COGS event?"
 *
 * InventoryCostLayer MUST NOT become:
 * - a second physical stock ledger;
 * - a batch-identity authority;
 * - a selling-price record;
 * - a FEFO engine;
 * - a COGS allocation engine;
 * - a receipt workflow;
 * - a replacement for StockMovement.
 *
 * ---------------------------------------------------------------------------
 * HISTORICAL ACQUISITION FACTS
 * ---------------------------------------------------------------------------
 *
 * The following fields describe the historical acquisition event and are
 * immutable after the layer is committed:
 *
 * - id
 * - productId
 * - stockBatchId
 * - supplierId
 * - initialQuantity
 * - acquisitionUnitCost
 * - acquiredAt
 * - sourceReceiptRef
 * - createdAt
 *
 * These values must never be rewritten merely to make current inventory
 * balances appear correct.
 *
 * ---------------------------------------------------------------------------
 * OPERATIONAL STATE
 * ---------------------------------------------------------------------------
 *
 * remainingQuantity is the only quantity state that changes as the cost layer
 * is consumed or explicitly restored.
 *
 * It is constrained by:
 *
 *     0 <= remainingQuantity <= initialQuantity
 *
 * The DAO also performs guarded atomic quantity changes. This entity's role
 * is to reject impossible states; it does not perform the transaction itself.
 *
 * ---------------------------------------------------------------------------
 * QUANTITY SEMANTICS
 * ---------------------------------------------------------------------------
 *
 * initialQuantity and remainingQuantity use the same exact QuantityScale.
 *
 * Quantity remains responsible for mathematical representation.
 * ProductMaster remains responsible for product-specific quantity policy.
 * InventoryCostLayer records the quantity belonging to this acquisition
 * tranche; it does not define the product's commercial conversion policy.
 *
 * ---------------------------------------------------------------------------
 * COST SEMANTICS
 * ---------------------------------------------------------------------------
 *
 * acquisitionUnitCost is the historical acquisition cost per canonical
 * quantity unit represented by the layer.
 *
 * It is NOT:
 * - the current selling price;
 * - the product's configured selling price;
 * - a recalculated average cost;
 * - a display approximation.
 *
 * The receiving workflow is responsible for constructing this value.
 * Later COGS logic consumes the layer without rewriting its historical
 * acquisition cost.
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
        Index(value = ["acquired_at"]),
        Index(value = ["source_receipt_ref"])
    ]
)
data class InventoryCostLayer(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Product whose physical stock and acquisition cost this layer represents.
     */
    @ColumnInfo(name = "product_id")
    val productId: String,

    /**
     * Physical batch to which this acquisition layer belongs.
     *
     * The batch remains the physical identity authority.
     */
    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String,

    /**
     * Supplier associated with this acquisition event.
     *
     * Supplier attribution belongs to the acquisition layer rather than
     * StockBatch because the same physical batch can be acquired through
     * multiple receiving events.
     */
    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    /**
     * Quantity originally acquired into this cost layer.
     *
     * This is an immutable historical quantity.
     */
    @Embedded(prefix = "initial_quantity_")
    val initialQuantity: Quantity,

    /**
     * Quantity from this acquisition layer that remains available.
     *
     * This is operational state and may change through controlled inventory
     * consumption or explicit restoration.
     */
    @Embedded(prefix = "remaining_quantity_")
    val remainingQuantity: Quantity,

    /**
     * Historical acquisition cost per canonical quantity represented by
     * this layer.
     *
     * Money is exact; no floating-point representation is permitted.
     */
    @ColumnInfo(name = "acquisition_unit_cost")
    val acquisitionUnitCost: Money,

    /**
     * Timestamp at which the acquisition occurred.
     *
     * This is historical and must not be rewritten to alter layer ordering.
     */
    @ColumnInfo(name = "acquired_at")
    val acquiredAt: Long,

    /**
     * Historical reference to the receiving event that created this layer.
     *
     * This is a provenance reference, not the layer's identity.
     */
    @ColumnInfo(name = "source_receipt_ref")
    val sourceReceiptRef: String? = null,

    /**
     * Timestamp at which this database record was created.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * Timestamp of the most recent operational-state persistence change.
     *
     * Historical acquisition facts remain immutable even when this value
     * changes because remainingQuantity changes.
     */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {

    init {

        require(id.isNotBlank() && id.trim() == id) {
            "InventoryCostLayer id must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(productId.isNotBlank() && productId.trim() == productId) {
            "InventoryCostLayer productId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(
            stockBatchId.isNotBlank() &&
                stockBatchId.trim() == stockBatchId
        ) {
            "InventoryCostLayer stockBatchId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        if (supplierId != null) {
            require(
                supplierId.isNotBlank() &&
                    supplierId.trim() == supplierId
            ) {
                "InventoryCostLayer supplierId must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        if (sourceReceiptRef != null) {
            require(
                sourceReceiptRef.isNotBlank() &&
                    sourceReceiptRef.trim() == sourceReceiptRef
            ) {
                "InventoryCostLayer sourceReceiptRef must not be blank or " +
                    "contain leading/trailing whitespace if provided"
            }
        }

        /*
         * The initial and remaining quantities belong to the same cost pool.
         * They therefore MUST use the exact same QuantityScale.
         *
         * No implicit rescaling is performed here.
         */
        require(initialQuantity.scale == remainingQuantity.scale) {
            "InventoryCostLayer initialQuantity scale " +
                "(${initialQuantity.scale}) and remainingQuantity scale " +
                "(${remainingQuantity.scale}) must match"
        }

        /*
         * A cost layer cannot represent an empty acquisition event.
         */
        require(initialQuantity.storageUnits > 0L) {
            "InventoryCostLayer initialQuantity must be strictly positive " +
                "(> 0), got: ${initialQuantity.storageUnits}"
        }

        /*
         * Remaining quantity may reach zero after complete consumption,
         * but it can never become negative.
         */
        require(remainingQuantity.storageUnits >= 0L) {
            "InventoryCostLayer remainingQuantity must be non-negative " +
                "(>= 0), got: ${remainingQuantity.storageUnits}"
        }

        /*
         * Operational state may never exceed the historical acquisition.
         */
        require(
            remainingQuantity.storageUnits <= initialQuantity.storageUnits
        ) {
            "InventoryCostLayer remainingQuantity " +
                "(${remainingQuantity.storageUnits}) must not exceed " +
                "initialQuantity (${initialQuantity.storageUnits})"
        }

        /*
         * Acquisition cost is historical monetary value.
         * Negative acquisition cost is not valid for this domain.
         *
         * A zero acquisition cost is deliberately permitted because the
         * receiving domain may legitimately represent donated/promotional/
         * zero-cost acquisition.
         */
        require(acquisitionUnitCost.amountMinorUnits >= 0L) {
            "InventoryCostLayer acquisitionUnitCost must not be negative, " +
                "got: ${acquisitionUnitCost.amountMinorUnits}"
        }

        require(acquiredAt > 0L) {
            "InventoryCostLayer acquiredAt must be a positive epoch " +
                "timestamp, got: $acquiredAt (id=$id)"
        }

        require(createdAt > 0L) {
            "InventoryCostLayer createdAt must be a positive epoch " +
                "timestamp, got: $createdAt (id=$id)"
        }

        require(updatedAt > 0L) {
            "InventoryCostLayer updatedAt must be a positive epoch " +
                "timestamp, got: $updatedAt (id=$id)"
        }

        require(updatedAt >= createdAt) {
            "InventoryCostLayer updatedAt ($updatedAt) must not precede " +
                "createdAt ($createdAt) (id=$id)"
        }
    }

    /**
     * Returns true when the cost layer has no quantity remaining.
     *
     * This is derived operational state only.
     * It does not delete or invalidate the historical acquisition layer.
     */
    val isDepleted: Boolean
        get() = remainingQuantity.storageUnits == 0L

    /**
     * Returns true when some, but not all, of the original acquisition
     * quantity remains.
     */
    val isPartiallyConsumed: Boolean
        get() =
            remainingQuantity.storageUnits > 0L &&
                remainingQuantity.storageUnits <
                    initialQuantity.storageUnits

    /**
     * Returns true when the complete acquisition quantity remains.
     */
    val isFullyAvailable: Boolean
        get() =
            remainingQuantity.storageUnits ==
                initialQuantity.storageUnits
}
