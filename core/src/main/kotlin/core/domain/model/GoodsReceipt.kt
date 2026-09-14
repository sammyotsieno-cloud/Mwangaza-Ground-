package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an operational goods-receiving event/document.
 *
 * Architectural authority:
 *
 * GoodsReceipt answers:
 *
 *     "What receiving event/document represents the arrival of these goods?"
 *
 * It owns:
 * - receipt identity;
 * - supplier relationship at the acquisition-event level;
 * - receipt number;
 * - source-document reference;
 * - receiving timestamp;
 * - receiving user;
 * - receipt lifecycle status.
 *
 * GoodsReceipt does NOT own:
 * - physical stock balance;
 * - stock movement;
 * - acquisition cost-layer balance;
 * - FEFO;
 * - COGS;
 * - selling price;
 * - product quantity conversion;
 * - cost allocation.
 *
 * Those responsibilities belong to their respective domain authorities.
 *
 * ---------------------------------------------------------------------------
 * LIFECYCLE
 * ---------------------------------------------------------------------------
 *
 * DRAFT
 *     Receipt is being prepared.
 *     No stock movement or acquisition-cost layer may exist for it.
 *
 * COMMITTED
 *     Receipt has been atomically posted.
 *     Its receipt items become historical receiving facts and the commit
 *     workflow must have created the corresponding physical stock movements
 *     and acquisition-cost layers.
 *
 * VOIDED
 *     Draft receiving document was abandoned before commitment.
 *
 * Once COMMITTED:
 * - the receipt must never be rewritten;
 * - the receipt must never be deleted;
 * - the receipt must never be committed again;
 * - committedAt must remain present;
 * - its identity must remain stable.
 *
 * ---------------------------------------------------------------------------
 * IDEMPOTENCY
 * ---------------------------------------------------------------------------
 *
 * Receipt identity has two relevant dimensions:
 *
 * - id
 * - receiptNumber
 *
 * Both are independently meaningful.
 *
 * The database already enforces receiptNumber uniqueness.
 * The persistence workflow must additionally detect:
 *
 * - same id with different receiptNumber;
 * - same receiptNumber with different id.
 *
 * Those are domain conflicts, not ordinary "already committed" cases.
 *
 * ---------------------------------------------------------------------------
 * SUPPLIER
 * ---------------------------------------------------------------------------
 *
 * Supplier belongs to the acquisition event:
 *
 *     Supplier -> GoodsReceipt -> InventoryCostLayer
 *
 * Supplier is deliberately NOT part of StockBatch identity.
 *
 * ---------------------------------------------------------------------------
 * IMMUTABILITY
 * ---------------------------------------------------------------------------
 *
 * The data class uses val properties so the entity itself does not expose
 * mutable state.
 *
 * Lifecycle transitions must occur by constructing a new valid GoodsReceipt
 * value and applying the transition through the receiving workflow.
 *
 * This entity does not perform database updates itself.
 */
@Entity(
    tableName = "goods_receipts",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["supplier_id"]),
        Index(value = ["status"]),
        Index(value = ["received_at"]),
        Index(value = ["receipt_number"], unique = true),
        Index(value = ["source_document_ref"])
    ]
)
data class GoodsReceipt(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Supplier responsible for this acquisition event.
     *
     * Null is permitted because receiving may occur before a supplier
     * relationship is recorded.
     */
    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    /**
     * Operational receiving-document identifier.
     *
     * This must be unique across the database.
     */
    @ColumnInfo(name = "receipt_number")
    val receiptNumber: String,

    /**
     * Optional external document/reference supplied by the vendor or facility.
     *
     * This is informational identity, not the primary receipt identity.
     */
    @ColumnInfo(name = "source_document_ref")
    val sourceDocumentRef: String? = null,

    /**
     * Receipt lifecycle state.
     */
    @ColumnInfo(name = "status")
    val status: String = STATUS_DRAFT,

    /**
     * Time the physical goods were received.
     *
     * This is a business-event timestamp and must not be replaced by
     * createdAt or committedAt.
     */
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    /**
     * Time the receipt was atomically posted.
     *
     * Null while DRAFT or VOIDED.
     */
    @ColumnInfo(name = "committed_at")
    val committedAt: Long? = null,

    /**
     * User who physically/operationally recorded the receipt.
     *
     * This is provenance, not supplier identity.
     */
    @ColumnInfo(name = "received_by_user_id")
    val receivedByUserId: String? = null,

    /**
     * Free-form operational notes.
     *
     * Notes do not alter receipt identity or accounting semantics.
     */
    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {

    init {

        require(id.isNotBlank() && id.trim() == id) {
            "GoodsReceipt id must not be blank or contain " +
                "leading/trailing whitespace"
        }

        require(
            receiptNumber.isNotBlank() &&
                receiptNumber.trim() == receiptNumber
        ) {
            "GoodsReceipt receiptNumber must not be blank or contain " +
                "leading/trailing whitespace"
        }

        if (supplierId != null) {
            require(
                supplierId.isNotBlank() &&
                    supplierId.trim() == supplierId
            ) {
                "GoodsReceipt supplierId must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        if (sourceDocumentRef != null) {
            require(
                sourceDocumentRef.isNotBlank() &&
                    sourceDocumentRef.trim() == sourceDocumentRef
            ) {
                "GoodsReceipt sourceDocumentRef must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        if (receivedByUserId != null) {
            require(
                receivedByUserId.isNotBlank() &&
                    receivedByUserId.trim() == receivedByUserId
            ) {
                "GoodsReceipt receivedByUserId must not be blank or contain " +
                    "leading/trailing whitespace if provided"
            }
        }

        require(
            status == STATUS_DRAFT ||
                status == STATUS_COMMITTED ||
                status == STATUS_VOIDED
        ) {
            "Invalid GoodsReceipt status: '$status'"
        }

        require(receivedAt > 0L) {
            "GoodsReceipt receivedAt must be a positive epoch timestamp, " +
                "got: $receivedAt (id=$id)"
        }

        require(createdAt > 0L) {
            "GoodsReceipt createdAt must be a positive epoch timestamp, " +
                "got: $createdAt (id=$id)"
        }

        require(updatedAt > 0L) {
            "GoodsReceipt updatedAt must be a positive epoch timestamp, " +
                "got: $updatedAt (id=$id)"
        }

        require(updatedAt >= createdAt) {
            "GoodsReceipt updatedAt ($updatedAt) must not precede " +
                "createdAt ($createdAt) (id=$id)"
        }

        when (status) {

            STATUS_COMMITTED -> {
                require(committedAt != null && committedAt > 0L) {
                    "Committed GoodsReceipt must have a positive " +
                        "committedAt timestamp (id=$id)"
                }

                require(committedAt >= createdAt) {
                    "Committed GoodsReceipt committedAt ($committedAt) " +
                        "must not precede createdAt ($createdAt) (id=$id)"
                }

                require(committedAt >= receivedAt) {
                    "Committed GoodsReceipt committedAt ($committedAt) " +
                        "must not precede receivedAt ($receivedAt) (id=$id)"
                }
            }

            STATUS_DRAFT,
            STATUS_VOIDED -> {
                require(committedAt == null) {
                    "Non-committed GoodsReceipt (status=$status) must not " +
                        "have a committedAt timestamp (id=$id)"
                }
            }
        }
    }

    val isDraft: Boolean
        get() = status == STATUS_DRAFT

    val isCommitted: Boolean
        get() = status == STATUS_COMMITTED

    val isVoided: Boolean
        get() = status == STATUS_VOIDED

    /**
     * Returns a new committed representation of this receipt.
     *
     * Persistence is intentionally NOT performed here.
     *
     * The receiving persistence workflow remains responsible for atomically
     * posting the receipt together with its items, StockBatch records,
     * InventoryCostLayer records and StockMovement records.
     */
    fun asCommitted(
        committedAt: Long
    ): GoodsReceipt {

        require(isDraft) {
            "Only a DRAFT GoodsReceipt may transition to COMMITTED: " +
                "id=$id, status=$status"
        }

        require(committedAt > 0L) {
            "GoodsReceipt committedAt must be a positive epoch timestamp, " +
                "got: $committedAt"
        }

        require(committedAt >= receivedAt) {
            "GoodsReceipt committedAt ($committedAt) must not precede " +
                "receivedAt ($receivedAt)"
        }

        return copy(
            status = STATUS_COMMITTED,
            committedAt = committedAt,
            updatedAt = committedAt
        )
    }

    /**
     * Returns a new voided representation of this receipt.
     *
     * Only an uncommitted draft may be voided.
     *
     * Persistence remains the responsibility of the receiving workflow.
     */
    fun asVoided(
        voidedAt: Long
    ): GoodsReceipt {

        require(isDraft) {
            "Only a DRAFT GoodsReceipt may transition to VOIDED: " +
                "id=$id, status=$status"
        }

        require(voidedAt > 0L) {
            "GoodsReceipt voidedAt must be a positive epoch timestamp, " +
                "got: $voidedAt"
        }

        return copy(
            status = STATUS_VOIDED,
            committedAt = null,
            updatedAt = voidedAt
        )
    }

    companion object {

        const val STATUS_DRAFT = "DRAFT"

        const val STATUS_COMMITTED = "COMMITTED"

        const val STATUS_VOIDED = "VOIDED"
    }
}
