package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room join entity modeling the many-to-many relationship between [ProductMaster] and [ProductTag].
 *
 * Foreign Key Policy:
 * - References [ProductMaster.id] with [ForeignKey.RESTRICT] to prevent accidental cascading deletion.
 * - References [ProductTag.id] with [ForeignKey.RESTRICT] to protect assigned tag associations.
 * - Enforces uniqueness on (product_id, tag_id) to prevent duplicate tag assignments to the same product.
 */
@Entity(
    tableName = "product_tag_assignments",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductTag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id", "tag_id"], unique = true),
        Index(value = ["tag_id"])
    ]
)
data class ProductTagAssignment(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "tag_id")
    val tagId: String,

    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "ProductTagAssignment id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "ProductTagAssignment productId must not be blank or contain leading/trailing whitespace"
        }
        require(tagId.isNotBlank() && tagId.trim() == tagId) {
            "ProductTagAssignment tagId must not be blank or contain leading/trailing whitespace"
        }
        require(assignedAt > 0L) {
            "ProductTagAssignment assignedAt must be a positive epoch timestamp, got: $assignedAt (id=$id)"
        }
    }
}
