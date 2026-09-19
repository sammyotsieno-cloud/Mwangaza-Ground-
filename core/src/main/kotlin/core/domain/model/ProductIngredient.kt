package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_ingredients",
    foreignKeys = [ForeignKey(
        entity = ProductMaster::class,
        parentColumns = ["id"],
        childColumns = ["product_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("product_id"), Index(value = ["product_id", "sequence"])]
)
data class ProductIngredient(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "ingredient_name") val ingredientName: String,
    @ColumnInfo(name = "normalized_ingredient_name") val normalizedIngredientName: String? = null,
    @ColumnInfo(name = "strength_value") val strengthValue: String? = null,
    @ColumnInfo(name = "strength_unit") val strengthUnit: String? = null,
    @ColumnInfo(name = "denominator_value") val denominatorValue: String? = null,
    @ColumnInfo(name = "denominator_unit") val denominatorUnit: String? = null,
    @ColumnInfo(name = "sequence") val sequence: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && productId.isNotBlank() && ingredientName.isNotBlank())
        require(sequence >= 0)
        require(createdAt > 0L && updatedAt >= createdAt)
    }
}
