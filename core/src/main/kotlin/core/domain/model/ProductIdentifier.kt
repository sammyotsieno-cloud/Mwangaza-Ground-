package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_identifiers",
    foreignKeys = [ForeignKey(
        entity = ProductMaster::class,
        parentColumns = ["id"],
        childColumns = ["product_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index("product_id"),
        Index(value = ["identifier_type", "normalized_value"], unique = true)
    ]
)
data class ProductIdentifier(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "identifier_type") val identifierType: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "normalized_value") val normalizedValue: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && productId.isNotBlank())
        require(identifierType.isNotBlank() && value.isNotBlank() && normalizedValue.isNotBlank())
        require(createdAt > 0L && updatedAt >= createdAt)
    }
}
