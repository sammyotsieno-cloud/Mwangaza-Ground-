package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an operational goods receiving event/document.
 *
 * Core Concept:
 * - Represents the physical arrival and verification of goods at the facility.
 * - Answers: "When did stock arrive, from which supplier, under what reference, and who received it?"
 * - Distinct from an invoice: An invoice is a financial/payable document that may arrive before or after
 *   goods without immediately altering physical stock.
 * - Distinct from the stock ledger: A [GoodsReceipt] does NOT directly store physical balances.
 *   Only when committed does it produce immutable [StockMovement] entries and [InventoryCostLayer] tranches.
 *
 * Lifecycle & Idempotency:
 * - Status transitions:
 *     * [STATUS_DRAFT]: The receipt is being prepared or edited. ZERO stock movements and ZERO cost layers exist.
 *     * [STATUS_COMMITTED]: The receipt has been atomically posted to the ledger. Stock movements and cost layers exist.
 *     * [STATUS_VOIDED]: Draft was discarded prior to commit.
 * - Once [STATUS_COMMITTED], the receipt and its items represent immutable historical facts.
 *   They must NEVER be rewritten, deleted, or committed a second time.
 * - Repeated commit requests must be safely rejected as already committed (idempotent).
 *
 * Supplier Relationship:
 * - Supplier identity is an acquisition-level relationship:
 *     Supplier -> GoodsReceipt -> InventoryCostLayer
 * - Supplier is NOT attached to [StockBatch] (physical batch identity is decoupled from supplier).
 *
 * Reusability:
 * - Contains zero hard-coded facility names ("Mwangaza-Ground"). Reusable across any health facility.
 * - Operates entirely offline with local Room persistence.
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
        Index(value = ["receipt_number"]),
        Index(value = ["source_document_ref"])
    ]
)
data class GoodsReceipt(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "supplier_id")
    val supplierId: String? = null,

    @ColumnInfo(name = "receipt_number")
    val receiptNumber: String,

    @ColumnInfo(name = "source_document_ref")
    val sourceDocumentRef: String? = null,

    @ColumnInfo(name = "status")
    val status: String = STATUS_DRAFT,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "committed_at")
    val committedAt: Long? = null,

    @ColumnInfo(name = "received_by_user_id")
    val receivedByUserId: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "GoodsReceipt id must not be blank or contain leading/trailing whitespace"
        }
        require(receiptNumber.isNotBlank() && receiptNumber.trim() == receiptNumber) {
            "GoodsReceipt receiptNumber must not be blank or contain leading/trailing whitespace"
        }
        if (supplierId != null) {
            require(supplierId.isNotBlank() && supplierId.trim() == supplierId) {
                "GoodsReceipt supplierId must not be blank or contain whitespace if provided"
            }
        }
        if (sourceDocumentRef != null) {
            require(sourceDocumentRef.isNotBlank() && sourceDocumentRef.trim() == sourceDocumentRef) {
                "GoodsReceipt sourceDocumentRef must not be blank or contain leading/trailing whitespace if provided"
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
            "GoodsReceipt receivedAt must be a positive epoch timestamp, got: $receivedAt (id=$id)"
        }
        require(createdAt > 0L) {
            "GoodsReceipt createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "GoodsReceipt updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "GoodsReceipt updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
        if (status == STATUS_COMMITTED) {
            require(committedAt != null && committedAt > 0L) {
                "Committed GoodsReceipt must have a positive committedAt timestamp (id=$id)"
            }
            require(committedAt >= createdAt) {
                "Committed GoodsReceipt committedAt ($committedAt) must not precede createdAt ($createdAt) (id=$id)"
            }
        } else {
            require(committedAt == null) {
                "Non-committed GoodsReceipt (status=$status) must not have a committedAt timestamp (id=$id)"
            }
        }
    }

    val isDraft: Boolean get() = status == STATUS_DRAFT
    val isCommitted: Boolean get() = status == STATUS_COMMITTED
    val isVoided: Boolean get() = status == STATUS_VOIDED

    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_COMMITTED = "COMMITTED"
        const val STATUS_VOIDED = "VOIDED"
    }
}
