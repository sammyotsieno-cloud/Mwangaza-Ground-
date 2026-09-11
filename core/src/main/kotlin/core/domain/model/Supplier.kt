package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an external commercial party from whom health products and commodities are acquired.
 *
 * Separation of Concerns:
 * - Represents identity and contact metadata of a supplier/distributor only.
 * - Does NOT store current inventory balances, stock counts, or purchasing catalogs.
 * - Does NOT store product prices directly as authoritative acquisition costs (historical acquisition costs
 *   belong strictly to transactional receipts and [InventoryCostLayer]).
 * - Does NOT implement procurement orders, purchase orders, quotations, accounts payable, or invoice processing.
 * - Facility-neutral: Contains zero facility-specific branding or hard-coded assumptions.
 */
@Entity(
    tableName = "suppliers",
    indices = [
        Index(value = ["name"])
    ]
)
data class Supplier(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "contact_person")
    val contactPerson: String? = null,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "address")
    val address: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "Supplier id must not be blank or contain leading/trailing whitespace"
        }
        require(name.isNotBlank() && name.trim() == name) {
            "Supplier name must not be blank or contain leading/trailing whitespace, got: '$name'"
        }
        require(createdAt > 0L) {
            "Supplier createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "Supplier updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "Supplier updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
