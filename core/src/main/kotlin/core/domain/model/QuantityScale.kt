package core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Defines the decimal precision used to represent a physical quantity
 * as an exact integer number of storage units.
 *
 * QuantityScale answers ONE question only:
 *
 *     "How many decimal places are available when representing the
 *      canonical/base quantity?"
 *
 * It does NOT define:
 * - the physical unit itself
 * - whether a product is discrete or continuous
 * - the minimum legal transaction increment
 * - packaging/commercial units
 * - conversion between boxes, blisters, bottles, packs, etc.
 *
 * Those concerns belong to the product/quantity domain.
 *
 * Representation rule:
 *
 *     storageUnits = physicalQuantity × 10^scale
 *
 * Examples:
 *
 * Scale 0:
 *     1 tablet = 1 storage unit
 *     25 tablets = 25 storage units
 *
 * Scale 3:
 *     1.000 mL = 1,000 storage units
 *     100.5 mL = 100,500 storage units
 *     0.25 mL = 250 storage units
 *
 * The storage representation is always an exact Long.
 *
 * Double and Float are deliberately not used.
 */
enum class QuantityScale(
    val scale: Int,
    val multiplier: Long
) {

    /**
     * Whole-unit precision.
     *
     * Example:
     * 1 tablet = 1 storage unit
     */
    SCALE_0(
        scale = 0,
        multiplier = 1L
    ),

    /**
     * One decimal place.
     */
    SCALE_1(
        scale = 1,
        multiplier = 10L
    ),

    /**
     * Two decimal places.
     */
    SCALE_2(
        scale = 2,
        multiplier = 100L
    ),

    /**
     * Three decimal places.
     *
     * Common example:
     * mL represented to 0.001 mL.
     */
    SCALE_3(
        scale = 3,
        multiplier = 1_000L
    ),

    /**
     * Four decimal places.
     */
    SCALE_4(
        scale = 4,
        multiplier = 10_000L
    ),

    /**
     * Five decimal places.
     */
    SCALE_5(
        scale = 5,
        multiplier = 100_000L
    ),

    /**
     * Six decimal places.
     */
    SCALE_6(
        scale = 6,
        multiplier = 1_000_000L
    );

    /**
     * Converts an exact decimal quantity into scaled integer storage units.
     *
     * No rounding is permitted.
     *
     * Examples:
     *
     * SCALE_0.toStorageUnits("10")     -> 10
     * SCALE_3.toStorageUnits("100.5")  -> 100500
     * SCALE_3.toStorageUnits("0.25")   -> 250
     *
     * SCALE_0.toStorageUnits("0.5")
     *
     * is rejected because half of a whole storage unit cannot be represented
     * at scale 0.
     */
    fun toStorageUnits(
        physicalQuantity: String
    ): Long {
        val decimal = try {
            BigDecimal(physicalQuantity.trim())
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Invalid quantity value: '$physicalQuantity'",
                e
            )
        }

        val scaled = decimal.multiply(
            BigDecimal.valueOf(multiplier)
        )

        if (scaled.stripTrailingZeros().scale() > 0) {
            throw IllegalArgumentException(
                "Quantity '$physicalQuantity' cannot be represented exactly " +
                    "at quantity scale $scale."
            )
        }

        return try {
            scaled.longValueExact()
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Quantity '$physicalQuantity' overflows Long storage units " +
                    "at quantity scale $scale."
            )
        }
    }

    /**
     * Converts exact scaled integer storage units back into a decimal string.
     *
     * Examples:
     *
     * SCALE_0.fromStorageUnits(100)      -> "100"
     * SCALE_3.fromStorageUnits(100500)   -> "100.5"
     * SCALE_3.fromStorageUnits(250)      -> "0.25"
     *
     * The returned value contains no unnecessary trailing zeroes while
     * preserving the exact represented quantity.
     */
    fun fromStorageUnits(
        storageUnits: Long
    ): String {
        return BigDecimal
            .valueOf(storageUnits)
            .divide(
                BigDecimal.valueOf(multiplier),
                scale,
                RoundingMode.UNNECESSARY
            )
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * Returns the exact number of decimal places represented by this scale.
     */
    val decimalPlaces: Int
        get() = scale

    /**
     * Returns the factor by which a physical base-unit quantity is multiplied
     * for integer storage.
     *
     * This is a precision multiplier only.
     *
     * It must never be interpreted as a packaging conversion.
     */
    val storageMultiplier: Long
        get() = multiplier

    companion object {

        const val MIN_SCALE: Int = 0
        const val MAX_SCALE: Int = 6

        /**
         * Resolves a QuantityScale from its integer exponent.
         *
         * Valid values are 0 through 6.
         */
        fun fromInt(
            scale: Int
        ): QuantityScale {
            return when (scale) {
                0 -> SCALE_0
                1 -> SCALE_1
                2 -> SCALE_2
                3 -> SCALE_3
                4 -> SCALE_4
                5 -> SCALE_5
                6 -> SCALE_6
                else -> throw IllegalArgumentException(
                    "Invalid quantity scale: $scale. " +
                        "Scale must be between $MIN_SCALE and $MAX_SCALE."
                )
            }
        }

        /**
         * Returns the storage multiplier for an integer scale.
         *
         * Example:
         *
         * storageMultiplierFor(3) == 1_000
         */
        fun storageMultiplierFor(
            scale: Int
        ): Long {
            return fromInt(scale).multiplier
        }
    }
}
