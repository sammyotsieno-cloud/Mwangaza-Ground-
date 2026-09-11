package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an operational dispensing or customer sales transaction that consumes inventory.
 *
 * Core Responsibilities & Invariants:
 * - Represents the business transaction event that consumed stock.
 * - Distinct from physical stock ledger: [Sale] is a commercial document; inventory deductions
 *   are recorded in [StockMovement] with [StockMovement.TYPE_SALE] and linked via [StockMovement.sourceTransactionRef].
 * - Status transitions:
 *     * [STATUS_COMPLETED]: Transaction successfully posted, stock movements appended, cost layers depleted.
 *     * [STATUS_VOIDED]: Transaction voided via compensating movements and cost restoration; original record preserved.
 * - Immutability: Once committed, historical sales figures, prices, and line items must NEVER be overwritten.
 * - Contains zero clinical/EMR patient record details.
 */
@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["sale_number"], unique = true),
        Index(value = ["status"]),
        Index(value = ["occurred_at"]),
        Index(value = ["customer_ref"])
    ]
)
data class Sale(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "sale_number")
    val saleNumber: String,

    @ColumnInfo(name = "status")
    val status: String = STATUS_COMPLETED,

    @ColumnInfo(name = "customer_ref")
    val customerRef: String? = null,

    @ColumnInfo(name = "total_selling_amount")
    val totalSellingAmount: Money,

    @ColumnInfo(name = "total_cogs")
    val totalCogs: Money,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "initiated_by_user_id")
    val initiatedByUserId: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "Sale id must not be blank or contain leading/trailing whitespace"
        }
        require(saleNumber.isNotBlank() && saleNumber.trim() == saleNumber) {
            "Sale saleNumber must not be blank or contain leading/trailing whitespace"
        }
        require(status == STATUS_COMPLETED || status == STATUS_VOIDED) {
            "Invalid Sale status: '$status'"
        }
        require(totalSellingAmount.amountMinorUnits >= 0L) {
            "Sale totalSellingAmount must not be negative, got: ${totalSellingAmount.amountMinorUnits} (id=$id)"
        }
        require(totalCogs.amountMinorUnits >= 0L) {
            "Sale totalCogs must not be negative, got: ${totalCogs.amountMinorUnits} (id=$id)"
        }
        require(occurredAt > 0L) {
            "Sale occurredAt must be a positive epoch timestamp, got: $occurredAt (id=$id)"
        }
        require(createdAt > 0L) {
            "Sale createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "Sale updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }

    val isCompleted: Boolean get() = status == STATUS_COMPLETED
    val isVoided: Boolean get() = status == STATUS_VOIDED

    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_VOIDED = "VOIDED"
    }
}
