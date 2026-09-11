package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the canonical identity of a physical health product or commodity.
 *
 * Canonical Identity ("What product is this?"):
 * - Answers identity: brand/trade name, standard/generic name, manufacturer, product type, category.
 * - Does NOT track physical stock balances, batches, lot numbers, expiry dates, acquisition costs,
 *   selling prices, suppliers, or transactions.
 * - Barcode/GTIN is explicitly excluded: discovery and identification are driven by visual product/document imaging and OCR.
 *
 * Registration & Lifecycle:
 * - A registered product is a persistent, reviewable, editable record.
 * - Changing descriptive details (e.g. correcting a manufacturer, improving an incomplete name)
 *   preserves the canonical [id] without creating duplicate products or altering historical ledger records.
 * - Soft retirement via [isActive] preserves referential integrity with historical transactions.
 * - Brand/trade name and standard/generic name remain clearly separated.
 */
@Entity(
    tableName = "product_masters",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategory::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["brand_name"]),
        Index(value = ["generic_name"])
    ]
)
data class ProductMaster(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "brand_name")
    val brandName: String? = null,

    @ColumnInfo(name = "generic_name")
    val genericName: String? = null,

    @ColumnInfo(name = "product_type")
    val productType: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "ProductMaster id must not be blank" }
        require(!brandName.isNullOrBlank() || !genericName.isNullOrBlank()) {
            "ProductMaster must have at least a brand name or a standard/generic name (id=$id)"
        }
    }

    /**
     * Human-readable display name distinguishing brand and generic identities.
     * Examples:
     * - "Nexaplast (Adhesive bandage)"
     * - "Panadol (Paracetamol)"
     * - "Paracetamol" (when only generic name is known)
     * - "Nexaplast" (when only brand name is known)
     */
    val displayName: String
        get() = when {
            !brandName.isNullOrBlank() && !genericName.isNullOrBlank() ->
                "${brandName.trim()} (${genericName.trim()})"
            !brandName.isNullOrBlank() ->
                brandName.trim()
            !genericName.isNullOrBlank() ->
                genericName.trim()
            else ->
                id
        }
}
