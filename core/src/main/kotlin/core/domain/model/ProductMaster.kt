package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the canonical identity and quantity policy
 * of a physical health product or commodity.
 *
 * CANONICAL IDENTITY:
 * - Answers: "What product is this?"
 * - Owns brand/trade name, standard/generic name, manufacturer,
 *   product type, category, and description.
 * - Does NOT track stock balances, batches, expiry dates, acquisition
 *   costs, selling prices, suppliers, or transactions.
 * - Barcode/GTIN is explicitly excluded.
 *
 * QUANTITY POLICY:
 * - Defines the precision at which this product's canonical physical
 *   quantity is stored.
 * - Defines the smallest legal transaction increment.
 * - Does NOT define commercial packaging conversions.
 *
 * ProductUnit owns:
 * - the canonical/base commercial unit identity;
 * - commercial unit names and abbreviations;
 * - exact rational conversion from a commercial unit to the
 *   canonical base unit.
 *
 * Quantity owns the mathematical representation of a quantity as:
 *
 *     storageUnits + QuantityScale
 *
 * Therefore:
 *
 * ProductMaster
 *     -> product-level quantity precision and legal increment
 *
 * ProductUnit
 *     -> physical/commercial unit identity and conversion
 *
 * Quantity
 *     -> exact quantity arithmetic
 *
 * Example:
 *
 *     Paracetamol:
 *         canonical unit = ProductUnit("Tablet")
 *         quantityScale = SCALE_0
 *         minimum increment = 1 storage unit
 *
 *     Syrup:
 *         canonical unit = ProductUnit("mL")
 *         quantityScale = SCALE_3
 *         minimum increment = 500 storage units
 *         therefore 0.5 mL is legal and 0.333 mL is not.
 *
 * The minimum increment is stored in the same scaled representation
 * defined by quantityScale.
 *
 * Historical quantity semantics must not be changed in a way that
 * silently changes the meaning of existing inventory or transactions.
 * Historical records must retain sufficient quantity facts to
 * reconstruct what actually occurred.
 *
 * Registration & Lifecycle:
 * - A registered product is a persistent, reviewable, editable record.
 * - Changing descriptive details preserves the canonical [id].
 * - Soft retirement via [isActive] preserves referential integrity.
 * - Historical ledger records must not be rewritten merely because
 *   descriptive product information changes.
 */
@Entity(
    tableName = "product_masters",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategory::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["brand_name"]),
        Index(value = ["generic_name"])
    ]
)
data class ProductMaster(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "brand_name")
    val brandName: String? = null,

    @ColumnInfo(name = "generic_name")
    val genericName: String? = null,

    @ColumnInfo(name = "product_type")
    val productType: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    @ColumnInfo(name = "manufacturer")
    val manufacturer: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /**
     * Quantity precision for this product's canonical physical quantity.
     *
     * This is NOT a packaging conversion.
     *
     * Examples:
     * - SCALE_0 -> whole tablets/pieces
     * - SCALE_3 -> milli-unit precision, e.g. mL to 0.001 mL
     */
    @ColumnInfo(
        name = "quantity_scale",
        defaultValue = "0"
    )
    val quantityScale: QuantityScale = QuantityScale.SCALE_0,

    /**
     * Smallest legal transaction quantity expressed in the same
     * scaled storage representation as Quantity.storageUnits.
     *
     * Examples:
     * - Tablet at SCALE_0 -> 1
     * - Piece at SCALE_0 -> 1
     * - Liquid at SCALE_3 with 0.5 mL increment -> 500
     * - Liquid at SCALE_3 with 1.0 mL increment -> 1000
     *
     * This value controls transaction legality; it does not define
     * commercial packaging.
     */
    @ColumnInfo(
        name = "minimum_transaction_increment_storage_units",
        defaultValue = "1"
    )
    val minimumTransactionIncrementStorageUnits: Long = 1L,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {

    init {
        require(id.isNotBlank()) {
            "ProductMaster id must not be blank"
        }

        require(!brandName.isNullOrBlank() || !genericName.isNullOrBlank()) {
            "ProductMaster must have at least a brand name or a standard/generic name (id=$id)"
        }

        require(minimumTransactionIncrementStorageUnits > 0L) {
            "ProductMaster minimum transaction increment must be positive " +
                "(id=$id, value=$minimumTransactionIncrementStorageUnits)"
        }
    }

    /**
     * The product's minimum legal transaction increment as an exact
     * Quantity at the product's configured precision.
     */
    val minimumTransactionIncrement: Quantity
        get() = Quantity(
            storageUnits = minimumTransactionIncrementStorageUnits,
            scale = quantityScale
        )

    /**
     * Human-readable display name distinguishing brand and generic identities.
     *
     * Examples:
     * - "Nexaplast (Adhesive bandage)"
     * - "Panadol (Paracetamol)"
     * - "Paracetamol"
     * - "Nexaplast"
     */
    val displayName: String
        get() = when {
            !brandName.isNullOrBlank() && !genericName.isNullOrBlank() ->
                "${brandName.trim()} (${genericName.trim()})"

            !brandName.isNullOrBlank() ->
                brandName.trim()

            !genericName.isNullOrBlank() ->
                genericName.trim()

            else ->
                id
        }
}
