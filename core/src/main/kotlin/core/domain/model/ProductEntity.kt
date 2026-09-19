package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_entities",
    foreignKeys = [ForeignKey(
        entity = ProductMaster::class,
        parentColumns = ["id"],
        childColumns = ["product_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("product_id"), Index(value = ["product_id", "role", "sequence"])]
)
data class ProductEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "entity_name") val entityName: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String? = null,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "address") val address: String? = null,
    @ColumnInfo(name = "sequence") val sequence: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    init {
        require(id.isNotBlank() && productId.isNotBlank() && entityName.isNotBlank() && role.isNotBlank())
        require(sequence >= 0)
        require(createdAt > 0L && updatedAt >= createdAt)
    }
}
