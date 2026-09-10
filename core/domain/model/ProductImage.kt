package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing retained product-image metadata and reference paths for a [ProductMaster].
 *
 * Visual Identification & Metadata Architecture:
 * - A product may have multiple retained visual representations (e.g. front packaging, label,
 *   side panel, back panel, insert, container).
 * - Stores URI/path references only ([imageUri]), never raw binary image data, preventing Room database bloat.
 * - Captures origin/provenance via [imageSource] (e.g. CAMERA, GALLERY_IMPORT, SCANNER_OUTPUT, OTHER).
 * - Supports camera capture, imported local images, enhanced/processed captures, and future visual scanner output.
 * - Does NOT require OCR, ML Kit, or CameraX to be present before images can be stored and cataloged.
 * - Barcode/GTIN/QR fields are strictly excluded: identification is driven by visual product/label capture.
 *
 * Primary & Ordering:
 * - [isPrimary] flags the default image used for product listings and selection cards.
 * - [sortOrder] dictates user-defined display sequence in visual galleries.
 */
@Entity(
    tableName = "product_images",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["product_id", "is_primary"])
    ]
)
data class ProductImage(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "image_uri")
    val imageUri: String,

    @ColumnInfo(name = "image_source")
    val imageSource: String = SOURCE_CAMERA,

    @ColumnInfo(name = "image_side")
    val imageSide: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "caption")
    val caption: String? = null,

    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    init {
        require(id.isNotBlank() && id.trim() == id) {
            "ProductImage id must not be blank or contain leading/trailing whitespace"
        }
        require(productId.isNotBlank() && productId.trim() == productId) {
            "ProductImage productId must not be blank or contain leading/trailing whitespace"
        }
        require(imageUri.isNotBlank()) {
            "ProductImage imageUri must not be blank"
        }
        require(imageSource.isNotBlank() && imageSource.trim() == imageSource) {
            "ProductImage imageSource must not be blank or contain leading/trailing whitespace"
        }
        require(sortOrder >= 0) {
            "ProductImage sortOrder must be non-negative (>= 0), got: $sortOrder (id=$id)"
        }
        require(createdAt > 0L) {
            "ProductImage createdAt must be a positive epoch timestamp, got: $createdAt (id=$id)"
        }
    }

    companion object {
        const val SOURCE_CAMERA = "CAMERA"
        const val SOURCE_GALLERY_IMPORT = "GALLERY_IMPORT"
        const val SOURCE_SCANNER_OUTPUT = "SCANNER_OUTPUT"
        const val SOURCE_OTHER = "OTHER"

        const val SIDE_FRONT = "FRONT"
        const val SIDE_BACK = "BACK"
        const val SIDE_SIDE = "SIDE"
        const val SIDE_LABEL = "LABEL"
        const val SIDE_PACKAGING = "PACKAGING"
        const val SIDE_OTHER = "OTHER"
    }
}
