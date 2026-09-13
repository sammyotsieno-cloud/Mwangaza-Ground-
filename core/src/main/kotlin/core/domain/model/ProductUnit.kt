package core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigInteger

/**
 * Room entity defining one commercial or operational unit for a ProductMaster.
 *
 * ProductUnit answers one question:
 *
 *     "How many canonical BASE UNITS does one of these units represent?"
 *
 * It does NOT define:
 * - quantity precision
 * - quantity storage scale
 * - minimum transaction increment
 * - actual inventory quantity
 * - price
 * - acquisition cost
 * - batch or expiry
 *
 * Those responsibilities belong to Quantity/QuantityScale, transactional records,
 * pricing models, and inventory models respectively.
 *
 * ---------------------------------------------------------------------------
 * CONVERSION MODEL
 * ---------------------------------------------------------------------------
 *
 * A commercial unit is represented as an exact rational conversion:
 *
 *     1 commercial unit
 *         =
 *     conversionNumerator / conversionDenominator base units
 *
 * Examples:
 *
 *     Tablet:
 *         1 tablet = 1/1 tablet
 *
 *     Blister:
 *         1 blister = 10/1 tablets
 *
 *     Box:
 *         1 box = 100/1 tablets
 *
 *     Bottle:
 *         1 bottle = 100/1 mL
 *
 *     Half-mL measure:
 *         1 measure = 1/2 mL
 *
 * No floating-point arithmetic is used.
 *
 * ---------------------------------------------------------------------------
 * PRECISION BOUNDARY
 * ---------------------------------------------------------------------------
 *
 * ProductUnit does NOT know whether the product is stored at scale 0, 3, etc.
 *
 * For example:
 *
 *     Product:
 *         canonical base unit = mL
 *         quantity scale = 3
 *
 *     ProductUnit:
 *         Bottle
 *         conversion = 100/1 mL
 *
 * The conversion layer therefore remains expressed in physical base units,
 * while QuantityScale separately determines that:
 *
 *     100 mL = 100,000 storage units
 *
 * This prevents packaging conversion from being confused with decimal
 * precision/storage scale.
 *
 * ---------------------------------------------------------------------------
 * FRACTIONAL COMMERCIAL UNITS
 * ---------------------------------------------------------------------------
 *
 * Rational conversion allows legitimate commercial units that are not whole
 * multiples of the base unit.
 *
 * Example:
 *
 *     1 measuring dose = 1/2 mL
 *
 * Whether a transaction may use that unit, and whether the resulting quantity
 * satisfies the product's minimum transaction increment, is decided by the
 * quantity/domain validation layer.
 *
 * ProductUnit itself does not decide whether a product may be fractionally
 * dispensed.
 *
 * ---------------------------------------------------------------------------
 * HISTORICAL SAFETY
 * ---------------------------------------------------------------------------
 *
 * A ProductUnit is configuration/master data.
 *
 * Once a unit has been used by a historical transaction, its conversion
 * semantics must not be silently changed in place. The application/service
 * layer must either:
 *
 * - prevent modification of the conversion of a historically used unit, or
 * - retire the old ProductUnit and create a new one.
 *
 * Historical transaction records must retain the quantity/conversion facts
 * necessary to reconstruct what actually occurred.
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

    /**
     * Numerator of the exact commercial-unit → canonical-base-unit conversion.
     *
     * Examples:
     * - tablet = 1
     * - blister = 10
     * - box = 100
     * - bottle of 100 mL = 100
     * - half-mL measure = 1
     */
    @ColumnInfo(name = "conversion_numerator")
    val conversionNumerator: Long,

    /**
     * Positive denominator of the exact commercial-unit → canonical-base-unit
     * conversion.
     *
     * Examples:
     * - tablet = 1
     * - blister = 1
     * - box = 1
     * - half-mL measure = 2
     */
    @ColumnInfo(name = "conversion_denominator")
    val conversionDenominator: Long = 1L,

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
        require(id.isNotBlank()) {
            "ProductUnit id must not be blank"
        }

        require(productId.isNotBlank()) {
            "ProductUnit productId must not be blank"
        }

        require(name.isNotBlank()) {
            "ProductUnit name must not be blank"
        }

        require(conversionNumerator > 0L) {
            "ProductUnit conversionNumerator must be strictly positive (> 0), " +
                "got: $conversionNumerator (id=$id)"
        }

        require(conversionDenominator > 0L) {
            "ProductUnit conversionDenominator must be strictly positive (> 0), " +
                "got: $conversionDenominator (id=$id)"
        }

        require(sortOrder >= 0) {
            "ProductUnit sortOrder must be non-negative (>= 0), " +
                "got: $sortOrder (id=$id)"
        }

        /*
         * Store every rational conversion in canonical reduced form.
         *
         * This does not mutate the constructor values, but the domain exposes
         * the normalized values through normalizedConversionNumerator and
         * normalizedConversionDenominator.
         */
    }

    /**
     * Exact rational conversion numerator after reduction.
     */
    val normalizedConversionNumerator: Long
        get() {
            val gcd = BigInteger.valueOf(conversionNumerator)
                .gcd(BigInteger.valueOf(conversionDenominator))

            return BigInteger.valueOf(conversionNumerator)
                .divide(gcd)
                .longValueExact()
        }

    /**
     * Exact rational conversion denominator after reduction.
     */
    val normalizedConversionDenominator: Long
        get() {
            val gcd = BigInteger.valueOf(conversionNumerator)
                .gcd(BigInteger.valueOf(conversionDenominator))

            return BigInteger.valueOf(conversionDenominator)
                .divide(gcd)
                .longValueExact()
        }

    /**
     * Returns true when this commercial unit represents exactly one
     * canonical base unit.
     *
     * Example:
     *     tablet = 1/1 tablet
     */
    val representsExactlyOneBaseUnit: Boolean
        get() =
            normalizedConversionNumerator == 1L &&
                normalizedConversionDenominator == 1L

    /**
     * Returns true when this commercial unit represents a whole-number
     * quantity of canonical base units.
     *
     * Examples:
     *     blister = 10/1 tablets -> true
     *     box = 100/1 tablets -> true
     *     half-mL measure = 1/2 mL -> false
     */
    val representsWholeBaseUnits: Boolean
        get() = normalizedConversionDenominator == 1L

    /**
     * Returns the exact conversion as a human-readable fraction.
     *
     * Examples:
     *     "1/1"
     *     "10/1"
     *     "100/1"
     *     "1/2"
     */
    val conversionFraction: String
        get() =
            "$normalizedConversionNumerator/$normalizedConversionDenominator"

    /**
     * Converts a whole number of commercial units into a rational number
     * of canonical base units.
     *
     * The result is deliberately represented as numerator/denominator rather
     * than Long so that a non-integral conversion cannot be silently rounded.
     *
     * Example:
     *
     *     3 half-mL measures
     *
     *     = 3 × 1/2 mL
     *     = 3/2 mL
     */
    fun convertCommercialUnits(
        commercialUnits: Long
    ): RationalQuantity {
        require(commercialUnits > 0L) {
            "Commercial quantity must be strictly positive, got: $commercialUnits"
        }

        val resultNumerator = BigInteger.valueOf(commercialUnits)
            .multiply(BigInteger.valueOf(normalizedConversionNumerator))

        val resultDenominator =
            BigInteger.valueOf(normalizedConversionDenominator)

        return RationalQuantity(
            numerator = resultNumerator.longValueExact(),
            denominator = resultDenominator.longValueExact()
        )
    }

    /**
     * Exact rational quantity used only as an intermediate conversion result.
     *
     * It is intentionally NOT the inventory quantity representation.
     *
     * Transactional inventory quantities must ultimately be converted into
     * Quantity using the ProductMaster's canonical quantity precision and
     * minimum transaction increment rules.
     */
    data class RationalQuantity(
        val numerator: Long,
        val denominator: Long
    ) {

        init {
            require(numerator > 0L) {
                "Rational quantity numerator must be positive"
            }

            require(denominator > 0L) {
                "Rational quantity denominator must be positive"
            }
        }

        val normalizedNumerator: Long
            get() {
                val gcd = BigInteger.valueOf(numerator)
                    .gcd(BigInteger.valueOf(denominator))

                return BigInteger.valueOf(numerator)
                    .divide(gcd)
                    .longValueExact()
            }

        val normalizedDenominator: Long
            get() {
                val gcd = BigInteger.valueOf(numerator)
                    .gcd(BigInteger.valueOf(denominator))

                return BigInteger.valueOf(denominator)
                    .divide(gcd)
                    .longValueExact()
            }

        val isWholeNumber: Boolean
            get() = normalizedDenominator == 1L
    }
}
