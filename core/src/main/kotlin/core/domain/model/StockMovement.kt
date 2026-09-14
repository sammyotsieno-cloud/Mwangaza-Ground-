package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the authoritative physical inventory movement ledger.
 *
 * ============================================================================
 * PHYSICAL LEDGER AUTHORITY
 * ============================================================================
 *
 * StockMovement answers one question:
 *
 *     "What physical stock moved, when, why, and by how much?"
 *
 * StockMovement is the SINGLE SOURCE OF TRUTH for physical stock flow.
 *
 * Current physical stock is derived from the signed quantities of historical
 * movements rather than from a mutable stock-balance field.
 *
 * Therefore:
 *
 * - StockBatch identifies the physical lot.
 * - InventoryCostLayer identifies the acquisition-cost pool.
 * - StockMovement records physical stock flow.
 * - StockAllocation later connects physical exits to acquisition-cost layers.
 *
 * StockMovement MUST NOT become:
 *
 * - a second stock-balance table;
 * - an acquisition-cost record;
 * - a selling-price record;
 * - a COGS record;
 * - a FEFO engine;
 * - a cost-layer allocation record.
 *
 * ============================================================================
 * IMMUTABILITY
 * ============================================================================
 *
 * A committed StockMovement is historical ledger evidence.
 *
 * The domain model therefore contains no mutation methods and no mutable
 * quantity state.
 *
 * Corrections to physical stock are represented by NEW compensating movements
 * such as:
 *
 * - STOCK_ADJUSTMENT
 * - RETURN
 * - DAMAGE
 * - EXPIRY
 * - LOSS
 * - SUPPLIER_RETURN
 *
 * Existing movement records must not be rewritten merely to correct a later
 * stock balance.
 *
 * Database-level append-only enforcement remains the responsibility of the
 * persistence/workflow layer. This entity deliberately does not pretend that
 * Kotlin val properties alone can prevent a database UPDATE or DELETE.
 *
 * ============================================================================
 * QUANTITY SEMANTICS
 * ============================================================================
 *
 * quantity is always physical stock quantity represented by Quantity.
 *
 * Quantity remains responsible for exact mathematical representation.
 * ProductMaster remains responsible for product-specific quantity policy.
 * ProductUnit remains responsible for commercial-unit conversion.
 *
 * StockMovement records the already-resolved physical quantity. It does not
 * perform commercial-unit conversion itself.
 *
 * No floating-point arithmetic is used.
 *
 * ============================================================================
 * DIRECTION CONVENTION
 * ============================================================================
 *
 * Positive quantity = physical stock enters inventory.
 *
 * Negative quantity = physical stock leaves inventory.
 *
 * STOCK_ADJUSTMENT may be either positive or negative, but never zero.
 *
 * ============================================================================
 * SOURCE TRANSACTION SEMANTICS
 * ============================================================================
 *
 * sourceTransactionRef identifies the originating business transaction.
 *
 * sourceTransactionType identifies the kind of originating transaction.
 *
 * These two fields are intentionally kept together:
 *
 *     sourceTransactionRef = "Which transaction?"
 *     sourceTransactionType = "What kind of transaction?"
 *
 * They are provenance/idempotency metadata only. They do not become another
 * inventory ledger.
 */
@Entity(
    tableName = "stock_movements",
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
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["stock_batch_id"]),
        Index(value = ["movement_type"]),
        Index(value = ["occurred_at"]),
        Index(value = ["source_transaction_ref"])
    ]
)
data class StockMovement(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Product whose physical stock changed.
     */
    @ColumnInfo(name = "product_id")
    val productId: String,

    /**
     * Physical stock batch affected by the movement.
     *
     * This may be null for movement types where batch identity is genuinely
     * unavailable or not applicable.
     *
     * When a movement is batch-specific, the persistence/workflow layer must
     * ensure that the referenced batch belongs to the same product.
     */
    @ColumnInfo(name = "stock_batch_id")
    val stockBatchId: String? = null,

    /**
     * Physical movement classification.
     */
    @ColumnInfo(name = "movement_type")
    val movementType: String,

    /**
     * Signed physical quantity.
     *
     * Positive = stock enters.
     * Negative = stock leaves.
     */
    @Embedded(prefix = "quantity_")
    val quantity: Quantity,

    /**
     * Historical business-event timestamp.
     *
     * This describes when the physical movement occurred, not when the row
     * happened to be written to the database.
     */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /**
     * Stable identifier of the originating business transaction.
     *
     * For GoodsReceipt, this is the GoodsReceipt.id, not the human-facing
     * receipt number. The receipt number remains historical business
     * information on GoodsReceipt itself.
     */
    @ColumnInfo(name = "source_transaction_ref")
    val sourceTransactionRef: String? = null,

    /**
     * Type of the originating business transaction.
     */
    @ColumnInfo(name = "source_transaction_type")
    val sourceTransactionType: String? = null,

    /**
     * User who initiated the originating operation, where applicable.
     */
    @ColumnInfo(name = "initiated_by_user_id")
    val initiatedByUserId: String? = null,

    /**
     * Human-readable operational explanation.
     *
     * This is descriptive metadata, not accounting authority.
     */
    @ColumnInfo(name = "reason")
    val reason: String? = null,

    /**
     * Database creation timestamp.
     *
     * This is distinct from occurredAt because a historical movement may be
     * recorded after the physical event occurred.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {

    init {

        require(id.isNotBlank() && id.trim() == id) {
            "StockMovement id must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(productId.isNotBlank() && productId.trim() == productId) {
            "StockMovement productId must not be blank or contain " +
                "leading/trailing whitespace"
        }

        if (stockBatchId != null) {
            require(
                stockBatchId.isNotBlank() &&
                    stockBatchId.trim() == stockBatchId
            ) {
                "StockMovement stockBatchId must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        /*
         * A physical ledger entry with zero quantity carries no physical
         * information and would distort neither stock nor auditability.
         */
        require(!quantity.isZero) {
            "StockMovement quantity must not be zero"
        }

        /*
         * Enforce the domain's movement-direction convention at construction
         * time so an invalid ledger event cannot be represented.
         */
        when (movementType) {

            TYPE_PURCHASE_RECEIPT,
            TYPE_RETURN -> {
                require(quantity.isPositive) {
                    "StockMovement movementType '$movementType' must have " +
                        "a positive quantity (inflow), got: " +
                        "${quantity.storageUnits}"
                }
            }

            TYPE_SALE,
            TYPE_DAMAGE,
            TYPE_EXPIRY,
            TYPE_LOSS,
            TYPE_SUPPLIER_RETURN -> {
                require(quantity.isNegative) {
                    "StockMovement movementType '$movementType' must have " +
                        "a negative quantity (outflow), got: " +
                        "${quantity.storageUnits}"
                }
            }

            TYPE_STOCK_ADJUSTMENT -> {
                /*
                 * Zero is already rejected above.
                 *
                 * Both positive and negative adjustments are legitimate:
                 *
                 *   positive = surplus physically found
                 *   negative = physical deficit
                 */
            }

            else -> {
                throw IllegalArgumentException(
                    "Unsupported StockMovement movementType: '$movementType'"
                )
            }
        }

        /*
         * Source reference and source type form one provenance pair.
         *
         * Allowing one without the other would create ambiguous transaction
         * provenance and make idempotency checks less reliable.
         */
        require(
            (sourceTransactionRef == null) ==
                (sourceTransactionType == null)
        ) {
            "StockMovement sourceTransactionRef and " +
                "sourceTransactionType must either both be provided or " +
                "both be null"
        }

        if (sourceTransactionRef != null) {
            require(
                sourceTransactionRef.isNotBlank() &&
                    sourceTransactionRef.trim() == sourceTransactionRef
            ) {
                "StockMovement sourceTransactionRef must not be blank or " +
                    "contain leading/trailing whitespace if provided"
            }
        }

        if (sourceTransactionType != null) {
            require(
                sourceTransactionType.isNotBlank() &&
                    sourceTransactionType.trim() == sourceTransactionType
            ) {
                "StockMovement sourceTransactionType must not be blank or " +
                    "contain leading/trailing whitespace if provided"
            }
        }

        if (initiatedByUserId != null) {
            require(
                initiatedByUserId.isNotBlank() &&
                    initiatedByUserId.trim() == initiatedByUserId
            ) {
                "StockMovement initiatedByUserId must not be blank or " +
                    "contain leading/trailing whitespace if provided"
            }
        }

        if (reason != null) {
            require(
                reason.isNotBlank() &&
                    reason.trim() == reason
            ) {
                "StockMovement reason must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        require(occurredAt > 0L) {
            "StockMovement occurredAt must be a positive epoch timestamp, " +
                "got: $occurredAt (id=$id)"
        }

        require(createdAt > 0L) {
            "StockMovement createdAt must be a positive epoch timestamp, " +
                "got: $createdAt (id=$id)"
        }
    }

    companion object {

        const val TYPE_PURCHASE_RECEIPT = "PURCHASE_RECEIPT"

        const val TYPE_SALE = "SALE"

        const val TYPE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT"

        const val TYPE_RETURN = "RETURN"

        const val TYPE_DAMAGE = "DAMAGE"

        const val TYPE_EXPIRY = "EXPIRY"

        const val TYPE_LOSS = "LOSS"

        const val TYPE_SUPPLIER_RETURN = "SUPPLIER_RETURN"

        /**
         * Stable source-transaction type for goods-receipt movements.
         *
         * GoodsReceiptService currently uses this semantic value when it
         * creates the physical receipt movement.
         */
        const val SOURCE_TYPE_GOODS_RECEIPT = "GOODS_RECEIPT"
    }
}
