package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing optional pharmaceutical-specific clinical and regulatory attributes
 * for a [ProductMaster].
 *
 * Separation of Concerns:
 * - [ProductMaster] is the canonical, universal identity for ANY health product (pharmaceuticals,
 *   consumables, medical devices, non-drug supplies).
 * - Non-pharmaceutical products (e.g. gauze, syringes, surgical gloves, adhesive bandages) do NOT
 *   require a [PharmaceuticalDetail] record.
 * - This entity does NOT implement prescribing logic, clinical decision support, drug interaction checks,
 *   or medical claims.
 * - Barcode/GTIN/QR fields, selling prices, purchasing costs, and inventory stock balances are strictly excluded.
 *
 * Cardinality & Referencing:
 * - Exactly zero or one [PharmaceuticalDetail] record per [ProductMaster] (enforced by unique index on [productId]).
 * - Foreign key references [ProductMaster.id] with [ForeignKey.RESTRICT] to protect clinical audit metadata.
 */
@Entity(
    tableName = "pharmaceutical_details",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"], unique = true)
    ]
)
data class PharmaceuticalDetail(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "active_ingredients")
    val activeIngredients: String? = null,

    @ColumnInfo(name = "strength")
    val strength: String? = null,

    @ColumnInfo(name = "dosage_form")
    val dosageForm: String? = null,

    @ColumnInfo(name = "route")
    val route: String? = null,

    @ColumnInfo(name = "therapeutic_category")
    val therapeuticCategory: String? = null,

    @ColumnInfo(name = "prescription_classification")
    val prescriptionClassification: String? = null,

    @ColumnInfo(name = "storage_condition")
    val storageCondition: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "PharmaceuticalDetail id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "PharmaceuticalDetail productId must not be blank or contain leading/trailing whitespace"
        }
        require(createdAt > 0L) {
            "PharmaceuticalDetail createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "PharmaceuticalDetail updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "PharmaceuticalDetail updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }
}
