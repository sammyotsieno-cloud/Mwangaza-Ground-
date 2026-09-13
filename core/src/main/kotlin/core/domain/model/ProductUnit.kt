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
 * ProductUnit answers:
 *
 *     "How many canonical BASE UNITS does one of these units represent?"
 *
 * It owns:
 * - commercial/operational unit identity
 * - exact rational conversion to the product's canonical base unit
 * - purchase/dispensing/display capabilities
 * - unit lifecycle
 *
 * It does NOT own:
 * - quantity precision
 * - quantity storage scale
 * - minimum transaction increment
 * - inventory quantity
 * - selling price
 * - acquisition cost
 * - batch or expiry
 *
 * ProductMaster owns the product-level quantity precision and minimum
 * transaction increment.
 *
 * Quantity owns exact quantity arithmetic.
 *
 * ---------------------------------------------------------------------------
 * CONVERSION MODEL
 * ---------------------------------------------------------------------------
 *
 * A commercial unit is represented as:
 *
 *     1 commercial unit
 *         =
 *     conversionNumerator / conversionDenominator canonical base units
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
 * BASE UNIT INVARIANT
 * ---------------------------------------------------------------------------
 *
 * Exactly one ProductUnit for a product is intended to represent the
 * canonical base unit.
 *
 * If isBaseUnit == true, its conversion MUST be exactly:
 *
 *     1/1
 *
 * A base unit representing 10/1 tablets, for example, would contradict the
 * meaning of canonical base unit and is therefore rejected.
 *
 * Database/service-level logic must additionally ensure that a product does
 * not have multiple active base units.
 *
 * ---------------------------------------------------------------------------
 * PRECISION BOUNDARY
 * ---------------------------------------------------------------------------
 *
 * ProductUnit does NOT know whether the product is stored at scale 0, 3, etc.
 *
 * Example:
 *
 *     Product:
 *         canonical base unit = mL
 *         quantity scale = 3
 *
 *     ProductUnit:
 *         Bottle
 *         conversion = 100/1 mL
 *
 * QuantityScale then determines that:
 *
 *     100 mL = 100,000 storage units
 *
 * Packaging conversion and decimal precision therefore remain separate.
 *
 * ---------------------------------------------------------------------------
 * FRACTIONAL COMMERCIAL UNITS
 * ---------------------------------------------------------------------------
 *
 * Rational conversion permits legitimate commercial units that are not whole
 * multiples of the canonical base unit.
 *
 * Example:
 *
 *     1 measuring dose = 1/2 mL
 *
 * Whether that quantity is legally usable in a transaction is determined by
 * the product's quantity precision and minimum transaction increment.
 *
 * ProductUnit does not decide whether fractional dispensing is permitted.
 *
 * ---------------------------------------------------------------------------
 * HISTORICAL SAFETY
 * ---------------------------------------------------------------------------
 *
 * ProductUnit is configuration/master data.
 *
 * Once a unit has participated in a historical transaction, its conversion
 * semantics must not be silently changed in place.
 *
 * The application/service layer must either:
 *
 * - prevent modification of conversion semantics for historically used units,
 *   or
 * - retire the old unit and create a new one.
 *
 * Historical transactions must retain sufficient quantity/conversion facts
 * to reconstruct what actually occurred.
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
     */
    @ColumnInfo(name = "conversion_numerator")
    val conversionNumerator: Long,

    /**
     * Positive denominator of the exact commercial-unit → canonical-base-unit
     * conversion.
     */
    @ColumnInfo(name = "conversion_denominator")
    val conversionDenominator: Long = 1L,

    /**
     * Identifies the canonical base unit for this product.
     *
     * When true, conversion must be exactly 1/1.
     */
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

        require(!isBaseUnit || representsExactlyOneBaseUnit) {
            "A ProductUnit marked as the canonical base unit must represent " +
                "exactly 1/1 canonical base unit (id=$id, conversion=$conversionNumerator/$conversionDenominator)"
        }
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
     */
    val representsExactlyOneBaseUnit: Boolean
        get() =
            normalizedConversionNumerator == 1L &&
                normalizedConversionDenominator == 1L

    /**
     * Returns true when this commercial unit represents a whole-number
     * quantity of canonical base units.
     */
    val representsWholeBaseUnits: Boolean
        get() = normalizedConversionDenominator == 1L

    /**
     * Exact conversion represented as a reduced fraction.
     */
    val conversionFraction: String
        get() =
            "$normalizedConversionNumerator/$normalizedConversionDenominator"

    /**
     * Converts a whole number of commercial units into an exact rational
     * quantity of canonical base units.
     *
     * The result is deliberately rational rather than rounded to Long.
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
     * Exact rational quantity used as an intermediate conversion result.
     *
     * This is NOT the inventory quantity representation.
     *
     * Transactional inventory quantities must ultimately be represented as
     * Quantity using the ProductMaster quantity scale and minimum increment.
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
