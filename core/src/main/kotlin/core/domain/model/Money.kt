package core.domain.model
import java.math.BigDecimal
import java.math.RoundingMode
/**
 * Pure domain value object representing an exact monetary amount for the
 * application's fixed Phase-1 currency: Kenyan Shillings (KES).
 *
 * Stored internally as exact [amountMinorUnits] (Long), representing cents
 * (minor currency units with fixed 2 decimal places).
 *
 * Phase 1 Currency Invariant:
 * - Currency: Kenyan Shilling (KES)
 * - Symbol: KSh
 * - Minor unit: Cent (1 KSh = 100 minor units)
 * - Minor-unit fraction digits: 2
 *
 * Examples:
 * - KSh 125.50 = 12,550 minor units
 * - KSh 10.00  = 1,000 minor units
 * - KSh 5.75   = 575 minor units
 * - KSh 0.01   = 1 minor unit
 *
 * Authoritative financial calculations strictly forbid Double and Float.
 * All arithmetic is integer/minor-unit based with overflow detection.
 * Exact total cost basis is preserved without rounded unit-price truncation.
 */
data class Money(
    val amountMinorUnits: Long
) : Comparable<Money> {

    val isZero: Boolean get() = amountMinorUnits == 0L
    val isPositive: Boolean get() = amountMinorUnits > 0L
    val isNegative: Boolean get() = amountMinorUnits < 0L

    operator fun plus(other: Money): Money {
        return Money(Math.addExact(this.amountMinorUnits, other.amountMinorUnits))
    }

    operator fun minus(other: Money): Money {
        return Money(Math.subtractExact(this.amountMinorUnits, other.amountMinorUnits))
    }

    operator fun unaryMinus(): Money {
        return Money(Math.negateExact(this.amountMinorUnits))
    }

    operator fun times(multiplier: Long): Money {
        return Money(Math.multiplyExact(this.amountMinorUnits, multiplier))
    }

    operator fun times(multiplier: Int): Money {
        return times(multiplier.toLong())
    }

    /**
     * Computes the absolute value of this monetary amount.
     * Throws [ArithmeticException] if [amountMinorUnits] is [Long.MIN_VALUE] (overflow).
     */
    fun abs(): Money {
        if (amountMinorUnits == Long.MIN_VALUE) {
            throw ArithmeticException("Overflow computing abs(Long.MIN_VALUE)")
        }
        return if (amountMinorUnits < 0L) Money(-amountMinorUnits) else this
    }

    /**
     * Divides this monetary amount by an exact integer divisor.
     * Throws [ArithmeticException] if division leaves a non-zero remainder,
     * preventing silent truncation of currency subunits.
     */
    fun divideExact(divisor: Long): Money {
        require(divisor != 0L) { "Division by zero" }

        if (amountMinorUnits % divisor != 0L) {
            throw ArithmeticException(
                "Cannot divide $amountMinorUnits minor units by $divisor exactly without remainder."
            )
        }

        return Money(amountMinorUnits / divisor)
    }

    /**
     * Divides this monetary amount by an integer divisor using explicit
     * [RoundingMode.HALF_UP] rounding.
     * Used when an operation produces an unavoidable fractional minor unit.
     */
    fun divideHalfUp(divisor: Long): Money {
        require(divisor != 0L) { "Division by zero" }

        val bd = BigDecimal.valueOf(amountMinorUnits)
            .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP)

        return Money(bd.longValueExact())
    }

    /**
     * Computes a percentage of this monetary amount from a decimal string
     * (e.g. "10", "7.5", "0.5").
     *
     * Uses exact decimal arithmetic and rounds to the nearest minor currency
     * unit using [roundingMode] (default HALF_UP).
     * Strictly avoids Double/Float.
     */
    fun percent(
        percentageString: String,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val pct = try {
            BigDecimal(percentageString.trim()).movePointLeft(2)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Invalid percentage string format: '$percentageString'",
                e
            )
        }

        val result = BigDecimal.valueOf(amountMinorUnits)
            .multiply(pct)
            .setScale(0, roundingMode)

        val minorUnits = try {
            result.longValueExact()
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Percentage calculation result overflows Long minor units"
            )
        }

        return Money(minorUnits)
    }

    /**
     * Computes a percentage of this monetary amount given basis points
     * (1 basis point = 0.01% = 0.0001).
     *
     * For example:
     * - 750 basis points = 7.50%
     * - 1,000 basis points = 10.00%
     *
     * Rounds using [roundingMode] (default HALF_UP).
     */
    fun percentBasisPoints(
        basisPoints: Long,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        val result = BigDecimal.valueOf(amountMinorUnits)
            .multiply(BigDecimal.valueOf(basisPoints))
            .divide(BigDecimal.valueOf(10_000L), 0, roundingMode)

        val minorUnits = try {
            result.longValueExact()
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Basis point calculation result overflows Long minor units"
            )
        }

        return Money(minorUnits)
    }

    /**
     * Computes a percentage of this monetary amount using integer numerator
     * and denominator.
     *
     * For example, 7.5% can be represented as numerator 75,
     * denominator 1000.
     *
     * Rounds using [roundingMode] (default HALF_UP).
     */
    fun percent(
        percentageNumerator: Long,
        percentageDenominator: Long = 100L,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): Money {
        require(percentageDenominator > 0L) {
            "Percentage denominator must be positive, but was $percentageDenominator"
        }

        val result = BigDecimal.valueOf(amountMinorUnits)
            .multiply(BigDecimal.valueOf(percentageNumerator))
            .divide(
                BigDecimal.valueOf(percentageDenominator),
                0,
                roundingMode
            )

        val minorUnits = try {
            result.longValueExact()
        } catch (e: ArithmeticException) {
            throw ArithmeticException(
                "Percentage calculation result overflows Long minor units"
            )
        }

        return Money(minorUnits)
    }

    override fun compareTo(other: Money): Int {
        return this.amountMinorUnits.compareTo(other.amountMinorUnits)
    }

    /**
     * Formats this monetary amount as a standard decimal string
     * (e.g. 12550 minor units -> "125.50").
     *
     * Uses Phase 1 fixed [FRACTION_DIGITS] (2 decimal places) by default.
     * Strictly float-free.
     */
    fun toPlainString(fractionDigits: Int = FRACTION_DIGITS): String {
        require(fractionDigits >= 0) {
            "Fraction digits cannot be negative: $fractionDigits"
        }

        if (fractionDigits == 0) {
            return amountMinorUnits.toString()
        }

        return BigDecimal.valueOf(amountMinorUnits)
            .movePointLeft(fractionDigits)
            .setScale(fractionDigits, RoundingMode.UNNECESSARY)
            .toPlainString()
    }

    companion object {
        const val CURRENCY_CODE: String = "KES"
        const val CURRENCY_SYMBOL: String = "KSh"
        const val FRACTION_DIGITS: Int = 2
        const val MINOR_UNITS_PER_SHILLING: Long = 100L

        val ZERO: Money = Money(0L)

        fun zero(): Money = ZERO

        fun ofMinor(amountMinorUnits: Long): Money = Money(amountMinorUnits)

        /**
         * Parses a decimal string (e.g. "125.50", "5.75", "10.00")
         * into a [Money] value object.
         *
         * Enforces Phase 1 fixed [fractionDigits] (2 decimal places) by
         * default.
         *
         * Rejects any decimal string containing precision beyond
         * [fractionDigits] without silent loss or rounding.
         *
         * Uses BigDecimal.longValueExact() directly so the conversion remains
         * exact while staying compatible with the application's min SDK 24.
         */
        fun fromDecimalString(
            decimalString: String,
            fractionDigits: Int = FRACTION_DIGITS
        ): Money {
            require(fractionDigits >= 0) {
                "Fraction digits cannot be negative: $fractionDigits"
            }

            val bd = try {
                BigDecimal(decimalString.trim())
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException(
                    "Invalid decimal string format: '$decimalString'",
                    e
                )
            }

            val scaled = bd.movePointRight(fractionDigits)

            if (scaled.remainder(BigDecimal.ONE).signum() != 0) {
                throw IllegalArgumentException(
                    "Decimal value '$decimalString' contains fractional precision " +
                        "exceeding $fractionDigits minor unit digits without loss."
                )
            }

            val minorUnits = try {
                scaled.longValueExact()
            } catch (e: ArithmeticException) {
                throw ArithmeticException(
                    "Decimal value '$decimalString' overflows Long minor units."
                )
            }

            return Money(minorUnits)
        }
    }
}
