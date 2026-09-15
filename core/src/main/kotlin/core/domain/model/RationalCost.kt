package core.domain.model

import java.math.BigInteger

/**
 * Exact mathematical monetary value represented as a rational number.
 *
 * RationalCost deliberately does not represent a settled/display currency
 * amount. It exists to preserve exact arithmetic when a finite currency
 * amount is divided across quantities and produces a fractional result.
 *
 * Example:
 *
 *     KES 100 / 3 units = 100/3
 *
 * Every RationalCost instance is canonical:
 *
 * - denominator must be non-zero;
 * - denominator is always positive;
 * - numerator and denominator are reduced by their greatest common divisor;
 * - zero is represented as 0/1.
 *
 * This means mathematically equivalent values are equal:
 *
 *     100/3 == 200/6
 *
 * No floating-point arithmetic, currency rounding, or UI formatting belongs
 * in this class.
 */
class RationalCost(
    numerator: BigInteger,
    denominator: BigInteger
) {

    val numerator: BigInteger
    val denominator: BigInteger

    init {
        require(denominator != BigInteger.ZERO) {
            "RationalCost denominator must not be zero"
        }

        require(denominator > BigInteger.ZERO) {
            "RationalCost denominator must be positive"
        }

        if (numerator == BigInteger.ZERO) {
            this.numerator = BigInteger.ZERO
            this.denominator = BigInteger.ONE
        } else {
            val gcd = numerator.abs().gcd(denominator)

            this.numerator = numerator.divide(gcd)
            this.denominator = denominator.divide(gcd)
        }
    }

    /**
     * True when this exact value is zero.
     */
    val isZero: Boolean
        get() = numerator == BigInteger.ZERO

    /**
     * True when this exact value is greater than or equal to zero.
     */
    val isNonNegative: Boolean
        get() = numerator >= BigInteger.ZERO

    /**
     * Returns the exact sum of this value and [other].
     *
     * No rounding is performed.
     */
    fun add(other: RationalCost): RationalCost {
        val resultNumerator =
            numerator.multiply(other.denominator)
                .add(other.numerator.multiply(denominator))

        val resultDenominator =
            denominator.multiply(other.denominator)

        return RationalCost(
            numerator = resultNumerator,
            denominator = resultDenominator
        )
    }

    /**
     * Returns the exact difference between this value and [other].
     *
     * No rounding is performed.
     *
     * Negative results are mathematically valid here because subtraction is
     * a general rational operation. Domain objects that prohibit negative
     * costs remain responsible for enforcing that invariant.
     */
    fun subtract(other: RationalCost): RationalCost {
        val resultNumerator =
            numerator.multiply(other.denominator)
                .subtract(other.numerator.multiply(denominator))

        val resultDenominator =
            denominator.multiply(other.denominator)

        return RationalCost(
            numerator = resultNumerator,
            denominator = resultDenominator
        )
    }

    /**
     * Returns the exact product of this value and an integer multiplier.
     *
     * No rounding is performed.
     */
    fun multiply(multiplier: BigInteger): RationalCost {
        return RationalCost(
            numerator = numerator.multiply(multiplier),
            denominator = denominator
        )
    }

    /**
     * Returns the exact product of this value and another rational value
     * represented by [numerator] / [denominator].
     *
     * No rounding is performed.
     *
     * This overload is intentionally shaped to match the existing repository
     * callers, which calculate quantities such as:
     *
     *     acquisitionUnitCost *
     *         remainingQuantity /
     *         initialQuantity
     */
    fun multiply(
        numerator: BigInteger,
        denominator: BigInteger
    ): RationalCost {
        require(denominator != BigInteger.ZERO) {
            "RationalCost multiplication denominator must not be zero"
        }

        return RationalCost(
            numerator = this.numerator.multiply(numerator),
            denominator = this.denominator.multiply(denominator)
        )
    }

    /**
     * Mathematical value equality.
     *
     * Because every instance is canonicalized at construction time,
     * equivalent fractions compare equal by their normalized numerator and
     * denominator.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is RationalCost) {
            return false
        }

        return numerator == other.numerator &&
            denominator == other.denominator
    }

    /**
     * Hash code consistent with mathematical value equality.
     */
    override fun hashCode(): Int {
        var result = numerator.hashCode()
        result = 31 * result + denominator.hashCode()
        return result
    }

    /**
     * Returns the canonical textual representation used by RoomConverters.
     *
     * Examples:
     *
     *     RationalCost(100, 3) -> "100/3"
     *     RationalCost(200, 6) -> "100/3"
     *     RationalCost(0, 50)  -> "0/1"
     */
    override fun toString(): String {
        return "$numerator/$denominator"
    }
}
