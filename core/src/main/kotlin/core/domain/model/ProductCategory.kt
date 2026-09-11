package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a reusable, hierarchical classification node for products.
 *
 * Supports arbitrary taxonomy nesting depth via self-referencing [parentCategoryId]:
 * - Root categories have [parentCategoryId] == null.
 * - Subcategories reference their parent's [id].
 *
 * Design Invariants:
 * - Stable identity: [id] is independent of category name, allowing renaming without breaking references.
 * - Reusable taxonomy: Contains zero facility-specific logic, branding, or hardcoded facility identity.
 * - Pure classification: Contains zero inventory, stock batch, pricing, or product master logic.
 * - Non-destructive lifecycle: Soft retirement via [isActive] preserves historical product classifications.
 * - Hierarchy validation: Preventing indirect multi-node cycles belongs to the repository/domain layer.
 */
@Entity(
    tableName = "product_categories",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategory::class,
            parentColumns = ["id"],
            childColumns = ["parent_category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["parent_category_id"]),
        Index(value = ["parent_category_id", "name"])
    ]
)
data class ProductCategory(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "parent_category_id")
    val parentCategoryId: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank()) { "ProductCategory id must not be blank" }
        require(name.isNotBlank()) { "ProductCategory name must not be blank" }
        require(parentCategoryId != id) { "ProductCategory cannot be its own parent (id=$id)" }
    }

    /**
     * True if this category is at the root of the classification hierarchy (has no parent).
     */
    val isRoot: Boolean
        get() = parentCategoryId == null
}
