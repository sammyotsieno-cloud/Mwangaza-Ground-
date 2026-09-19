package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_attributes",
    foreignKeys = [ForeignKey(
        entity = ProductMaster::class,
        parentColumns = ["id"],
        childColumns = ["product_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("product_id"), Index(value = ["product_id", "definition_key"])]
)
data class ProductAttribute(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "definition_key") val definitionKey: String,
    @ColumnInfo(name = "value_type") val valueType: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "normalized_value") val normalizedValue: String? = null,
    @ColumnInfo(name = "provenance") val provenance: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && productId.isNotBlank() && definitionKey.isNotBlank())
        require(valueType.isNotBlank() && value.isNotBlank())
        require(createdAt > 0L && updatedAt >= createdAt)
    }
}
