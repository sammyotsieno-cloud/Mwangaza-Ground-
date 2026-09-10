package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a commercial or operational unit associated with a [ProductMaster].
 *
 * Commercial Unit Definition:
 * - Defines how a product is purchased, stocked, dispensed, or displayed (e.g. tablet, blister, box, mL, bottle).
 * - [conversionMultiplier] represents the EXACT number of scaled base storage units represented by ONE commercial unit:
 *     COMMERCIAL UNIT → SCALED BASE STORAGE UNITS
 *
 * Conversion Semantics & QuantityScale:
 * - Discrete products (QuantityScale = SCALE_0, multiplier = 1):
 *     - tablet (base unit): conversionMultiplier = 1
 *     - blister (10 tablets): conversionMultiplier = 10
 *     - box (100 tablets): conversionMultiplier = 100
 * - Continuous/scaled products (e.g. Liquid, QuantityScale = SCALE_3, multiplier = 1,000):
 *     - mL (base unit): conversionMultiplier = 1,000
 *     - bottle (100 mL): conversionMultiplier = 100,000
 *
 * Architectural Boundaries:
 * - Does NOT own actual quantity (belongs to [Quantity] and transactional ledgers).
 * - Does NOT own pricing or [Money] (belongs to [UnitPriceConfig]).
 * - Does NOT own inventory, batches, expiry, suppliers, or movements.
 * - Does NOT own facility identity.
 * - Does NOT own barcode or GTIN data.
 * - Foreign key to [ProductMaster.id] uses [ForeignKey.RESTRICT] to protect historical transactions
 *   and enforce non-destructive soft retirement via [isActive].
 */
@Entity(
    tableName = "product_units",
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
        Index(value = ["product_id", "name"]),
        Index(value = ["product_id", "is_base_unit"])
    ]
)
data class ProductUnit(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "abbreviation")
    val abbreviation: String? = null,

    @ColumnInfo(name = "conversion_multiplier")
    val conversionMultiplier: Long,

    @ColumnInfo(name = "is_base_unit")
    val isBaseUnit: Boolean = false,

    @ColumnInfo(name = "is_purchase_unit")
    val isPurchaseUnit: Boolean = false,

    @ColumnInfo(name = "is_dispensing_unit")
    val isDispensingUnit: Boolean = false,

    @ColumnInfo(name = "is_display_unit")
    val isDisplayUnit: Boolean = false,

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
        require(id.isNotBlank()) { "ProductUnit id must not be blank" }
        require(productId.isNotBlank()) { "ProductUnit productId must not be blank" }
        require(name.isNotBlank()) { "ProductUnit name must not be blank" }
        require(conversionMultiplier > 0L) {
            "ProductUnit conversionMultiplier must be strictly positive (> 0), got: $conversionMultiplier (id=$id)"
        }
        require(sortOrder >= 0) {
            "ProductUnit sortOrder must be non-negative (>= 0), got: $sortOrder (id=$id)"
        }
    }
}
