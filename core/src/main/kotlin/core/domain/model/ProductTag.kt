package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a flexible, user-defined tag or classification label for products.
 *
 * Normalization & Uniqueness Policy:
 * - [name] must be non-blank and normalized without leading or trailing whitespace.
 * - Enforces case-sensitive/literal uniqueness via unique index on [name].
 *
 * Separation from ProductCategory:
 * - [ProductCategory] represents strict, hierarchical taxonomy (e.g. "Pharmaceuticals > Analgesics").
 * - [ProductTag] represents orthogonal, non-hierarchical, multi-dimensional attributes such as:
 *     * "Fast Moving"
 *     * "OTC"
 *     * "High Value"
 *     * "Seasonal"
 *     * "Frequently Dispensed"
 *     * "Controlled / Internal Review"
 * - Extensible: Tags are configurable data records, not hardcoded enums.
 * - Non-Automating: Tags do NOT automatically alter pricing formulas, reorder quantities, or stock logic.
 *
 * Visual Metadata:
 * - [colorHex] optionally defines an RGB/ARGB hex color code (e.g. "#4CAF50" or "#FF4CAF50") for UI badges.
 */
@Entity(
    tableName = "product_tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class ProductTag(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "ProductTag id must not be blank or contain leading/trailing whitespace"
        }
        require(name.isNotBlank() && name.trim() == name) {
            "ProductTag name must not be blank or contain leading/trailing whitespace, got: '$name'"
        }
        if (colorHex != null) {
            require(HEX_COLOR_REGEX.matches(colorHex.trim())) {
                "ProductTag colorHex must be a valid hex color (e.g. #RRGGBB or #AARRGGBB), got: '$colorHex'"
            }
        }
        require(createdAt > 0L) {
            "ProductTag createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
        require(updatedAt > 0L) {
            "ProductTag updatedAt must be a positive epoch timestamp, got: $updatedAt (id=$id)"
        }
        require(updatedAt >= createdAt) {
            "ProductTag updatedAt ($updatedAt) must not precede createdAt ($createdAt) (id=$id)"
        }
    }

    companion object {
        private val HEX_COLOR_REGEX = Regex("""^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$""")
    }
}
