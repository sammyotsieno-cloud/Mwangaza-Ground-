package core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact monetary value object for Mwangaza-Ground Phase 1.
 *
 * Currency:
 * - Kenyan Shilling (KES)
 * - Symbol: KSh
 * - Minor unit: 1/100 KSh
 * - Fixed precision: 2 decimal places
 *
 * Internal representation:
 * - Long minor units
 *
 * Examples:
 * - KSh 125.50 = 12_550
 * - KSh 10.00  = 1_000
 * - KSh 5.75   = 575
 * - KSh 0.01   = 1
 *
 * Money deliberately knows nothing about:
 * - inventory
 * - products
 * - packaging
 * - quantities
 * - cost layers
 * - FEFO
 * - COGS
 *
 * Acquisition-cost allocation must therefore remain outside this class.
 * In particular, a total acquisition cost that cannot be divided exactly
 * into minor currency units must remain a cost pool rather than being
 * repeatedly rounded into a historical unit cost.
 *
 * Double and Float are prohibited from authoritative monetary calculations.
 */
data class Money(
    val amountMinorUnits: Long
) : Comparable<Money> {

    val isZero: Boolean
        get() = amountMinorUnits == 0L

    val isPositive: Boolean
        get() = amountMinorUnits > 0L

    val isNegative: Boolean
        get() = amountMinorUnits < 0L

    /**
     * Adds two monetary amounts with overflow protection.
     */
    operator fun plus(other: Money): Money {
        return Money(
            Math.addExact(
                amountMinorUnits,
                other.amountMinorUnits
            )
        )
    }

    /**
     * Subtracts two monetary amounts with overflow protection.
     */
    operator fun minus(other: Money): Money {
        return Money(
            Math.subtractExact(
                amountMinorUnits,
                other.amountMinorUnits
            )
        )
    }

    /**
     * Negates this amount with overflow protection.
     */
    operator fun unaryMinus(): Money {
        return Money(
            Math.negateExact(amountMinorUnits)
        )
    }

    /**
     * Multiplies a monetary amount by an exact integer multiplier.
     *
     * This is appropriate for operations such as:
     *
     * KSh 7.00 × 10 units = KSh 70.00
     *
     * It is not a replacement for fractional cost allocation.
     */
    operator fun times(multiplier: Long): Money {
        return Money(
            Math.multiplyExact(
                amountMinorUnits,
                multiplier
            )
        )
    }

    operator fun times(multiplier: Int): Money {
        return times(multiplier.toLong())
    }

    /**
     * Returns the absolute monetary value.
     *
     * Throws if the amount is Long.MIN_VALUE because its positive
     * counterpart cannot be represented by Long.
     */
    fun abs(): Money {
        if (amountMinorUnits == Long.MIN_VALUE) {
            throw ArithmeticException(
                "Overflow computing abs(Long.MIN_VALUE)"
            )
        }

        return if (amountMinorUnits < 0L) {
            Money(-amountMinorUnits)
        } else {
            this
        }
    }

    /**
     * Divides this amount by an integer divisor only when the result
     * is exactly representable in minor currency units.
     *
     * Example:
     *
     * KSh 9.00 / 3 = KSh 3.00
     *
     * But:
     *
     * KSh 100.00 / 3
     *
     * is rejected because 10,000 / 3 cents is not integral.
     *
     * This method deliberately does NOT silently round.
     */
    fun divideExact(divisor: Long): Money {
        require(divisor != 0L) {
            "Division by zero"
        }

        if (amountMinorUnits % divisor != 0L) {
            throw ArithmeticException(
                "Cannot divide $amountMinorUnits minor units " +
                    "by $divisor exactly without remainder."
            )
        }

        return Money(
            amountMinorUnits / divisor
        )
    }

    /**
     * Divides this amount using an explicitly selected rounding mode.
     *
     * This method is intentionally named divideRounded rather than
     * divideHalfUp so callers must acknowledge that rounding is occurring.
     *
     * IMPORTANT:
     * This must NOT be used as the authoritative mechanism for allocating
     * an acquisition-cost pool across inventory quantities.
     *
     * Example:
     *
     * KSh 100.00 / 3
     *
     * cannot become three authoritative KSh 33.33 costs because that would
     * account for only KSh 99.99.
     *
     * Inventory cost allocation must instead retain the total acquisition
     * cost and allocate the remaining remainder deterministically.
     */
    fun divideRounded(
        divisor: Long,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        require(divisor != 0L) {
            "Division by zero"
        }

        val result = BigDecimal
            .valueOf(amountMinorUnits)
            .divide(
                BigDecimal.valueOf(divisor),
                0,
                roundingMode
            )

        return try {
            Money(result.longValueExact())
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Rounded division result overflows Long minor units."
            )
        }
    }

    /**
     * Calculates a percentage using a decimal percentage string.
     *
     * Examples:
     *
     * Money(10_000).percent("10")   = Money(1_000)
     * Money(10_000).percent("7.5") = Money(750)
     *
     * The percentage itself is represented as a decimal string so that
     * floating-point arithmetic is never introduced.
     */
    fun percent(
        percentageString: String,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val percentage = try {
            BigDecimal(percentageString.trim())
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Invalid percentage string format: '$percentageString'",
                e
            )
        }

        val result = BigDecimal
            .valueOf(amountMinorUnits)
            .multiply(percentage)
            .movePointLeft(2)
            .setScale(0, roundingMode)

        return try {
            Money(result.longValueExact())
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Percentage calculation result overflows Long minor units."
            )
        }
    }

    /**
     * Calculates a percentage using basis points.
     *
     * 1 basis point = 0.01%
     *
     * Examples:
     *
     * 750 basis points  = 7.50%
     * 1,000 basis points = 10.00%
     */
    fun percentBasisPoints(
        basisPoints: Long,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val result = BigDecimal
            .valueOf(amountMinorUnits)
            .multiply(BigDecimal.valueOf(basisPoints))
            .divide(
                BigDecimal.valueOf(10_000L),
                0,
                roundingMode
            )

        return try {
            Money(result.longValueExact())
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Basis point calculation result overflows Long minor units."
            )
        }
    }

    /**
     * Calculates a percentage using an integer numerator and denominator.
     *
     * Example:
     *
     * 7.5% = 75 / 1000
     */
    fun percent(
        percentageNumerator: Long,
        percentageDenominator: Long = 100L,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        require(percentageDenominator > 0L) {
            "Percentage denominator must be positive, " +
                "but was $percentageDenominator"
        }

        val result = BigDecimal
            .valueOf(amountMinorUnits)
            .multiply(BigDecimal.valueOf(percentageNumerator))
            .divide(
                BigDecimal.valueOf(percentageDenominator),
                0,
                roundingMode
            )

        return try {
            Money(result.longValueExact())
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Percentage calculation result overflows Long minor units."
            )
        }
    }

    /**
     * Formats the monetary amount using the application's fixed
     * two-decimal KES precision.
     *
     * Examples:
     *
     * Money(12_550).toPlainString() -> "125.50"
     * Money(1_000).toPlainString()  -> "10.00"
     * Money(575).toPlainString()    -> "5.75"
     * Money(-125).toPlainString()   -> "-1.25"
     *
     * Precision is intentionally NOT configurable.
     *
     * Money in Phase 1 always means KSh with exactly two decimal places.
     */
    fun toPlainString(): String {
        return BigDecimal
            .valueOf(amountMinorUnits)
            .movePointLeft(FRACTION_DIGITS)
            .setScale(
                FRACTION_DIGITS,
                RoundingMode.UNNECESSARY
            )
            .toPlainString()
    }

    override fun compareTo(other: Money): Int {
        return amountMinorUnits.compareTo(
            other.amountMinorUnits
        )
    }

    companion object {

        /**
         * Phase-1 currency.
         */
        const val CURRENCY_CODE: String = "KES"

        /**
         * Kenyan Shilling display symbol.
         */
        const val CURRENCY_SYMBOL: String = "KSh"

        /**
         * KES has exactly two decimal places in Phase 1.
         */
        const val FRACTION_DIGITS: Int = 2

        /**
         * One Kenyan Shilling contains 100 minor units.
         */
        const val MINOR_UNITS_PER_SHILLING: Long = 100L

        /**
         * Zero monetary amount.
         */
        val ZERO: Money = Money(0L)

        fun zero(): Money = ZERO

        /**
         * Creates Money directly from exact minor units.
         *
         * Example:
         *
         * Money.ofMinor(65_000)
         *
         * represents KSh 650.00.
         */
        fun ofMinor(
            amountMinorUnits: Long
        ): Money {
            return Money(amountMinorUnits)
        }

        /**
         * Parses a KES decimal value using the fixed Phase-1
         * two-decimal precision.
         *
         * Accepted:
         * - "650"
         * - "650.00"
         * - "650.50"
         * - "0.01"
         * - "-10.25"
         *
         * Rejected:
         * - "10.001"
         * - "10.005"
         * - values outside Long minor-unit range
         *
         * No rounding is performed during parsing.
         */
        fun fromDecimalString(
            decimalString: String
        ): Money {
            val normalized = decimalString.trim()

            require(normalized.isNotEmpty()) {
                "Money value cannot be empty."
            }

            val decimal = try {
                BigDecimal(normalized)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException(
                    "Invalid KES decimal value: '$decimalString'",
                    e
                )
            }

            val scaled = decimal.movePointRight(
                FRACTION_DIGITS
            )

            if (scaled.remainder(BigDecimal.ONE).signum() != 0) {
                throw IllegalArgumentException(
                    "KES value '$decimalString' contains more than " +
                        "$FRACTION_DIGITS decimal places."
                )
            }

            val minorUnits = try {
                scaled.longValueExact()
            } catch (e: ArithmeticException) {
                throw ArithmeticException(
                    "KES value '$decimalString' overflows Long minor units."
                )
            }

            return Money(minorUnits)
        }
    }
}
