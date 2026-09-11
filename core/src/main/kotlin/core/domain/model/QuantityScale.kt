package core.domain.model

/**
 * Represents the decimal precision scale for quantities in the inventory system.
 *
 * Valid scale exponents range from 0 to 6, defining the power-of-ten multiplier
 * used to convert physical base units into integer storage units:
 *
 * storageUnits = physicalQuantity * multiplier
 *
 * This primitive is strictly decoupled from physical units (e.g. tablet, mL)
 * and discrete vs. continuous classification rules.
 */
enum class QuantityScale(
    val scale: Int,
    val multiplier: Long
) {
    SCALE_0(scale = 0, multiplier = 1L),
    SCALE_1(scale = 1, multiplier = 10L),
    SCALE_2(scale = 2, multiplier = 100L),
    SCALE_3(scale = 3, multiplier = 1_000L),
    SCALE_4(scale = 4, multiplier = 10_000L),
    SCALE_5(scale = 5, multiplier = 100_000L),
    SCALE_6(scale = 6, multiplier = 1_000_000L);

    companion object {
        const val MIN_SCALE: Int = 0
        const val MAX_SCALE: Int = 6

        /**
         * Resolves a [QuantityScale] from an integer scale exponent (0..6).
         * Throws [IllegalArgumentException] if the scale is outside the valid range.
         */
        fun fromInt(scale: Int): QuantityScale {
            return when (scale) {
                0 -> SCALE_0
                1 -> SCALE_1
                2 -> SCALE_2
                3 -> SCALE_3
                4 -> SCALE_4
                5 -> SCALE_5
                6 -> SCALE_6
                else -> throw IllegalArgumentException(
                    "Invalid quantity scale: $scale. Scale must be between $MIN_SCALE and $MAX_SCALE."
                )
            }
        }
    }
}
