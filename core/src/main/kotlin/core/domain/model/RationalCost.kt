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
 * The value is always normalized:
 *
 * - denominator must be non-zero;
 * - denominator is always positive;
 * - numerator and denominator are reduced by their greatest common divisor;
 * - zero is represented canonically as 0/1.
 *
 * No floating-point arithmetic, currency rounding, or UI formatting belongs
 * in this class.
 */
data class RationalCost(
    val numerator: BigInteger,
    val denominator: BigInteger
) {

    init {
        require(denominator != BigInteger.ZERO) {
            "RationalCost denominator must not be zero"
        }

        require(denominator > BigInteger.ZERO) {
            "RationalCost denominator must be positive"
        }
    }

    /**
     * Returns the canonical mathematical representation of this value.
     *
     * Because the primary constructor is intentionally kept compatible with
     * the existing repository call sites, normalization is performed through
     * this factory before constructing derived values.
     */
    private fun normalized(): RationalCost {
        if (numerator == BigInteger.ZERO) {
            return RationalCost(
                numerator = BigInteger.ZERO,
                denominator = BigInteger.ONE
            )
        }

        val gcd = numerator.abs().gcd(denominator)

        return if (gcd == BigInteger.ONE) {
            this
        } else {
            RationalCost(
                numerator = numerator.divide(gcd),
                denominator = denominator.divide(gcd)
            )
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

        return canonical(
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

        return canonical(
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
        return canonical(
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
     * This overload is intentionally named and shaped to match the existing
     * repository callers, which calculate quantities such as:
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

        return canonical(
            numerator = this.numerator.multiply(numerator),
            denominator = this.denominator.multiply(denominator)
        )
    }

    /**
     * Returns a canonical RationalCost.
     *
     * The canonical form guarantees:
     *
     *     denominator > 0
     *
     * and:
     *
     *     gcd(abs(numerator), denominator) == 1
     *
     * with zero represented as:
     *
     *     0/1
     */
    private fun canonical(
        numerator: BigInteger,
        denominator: BigInteger
    ): RationalCost {
        require(denominator != BigInteger.ZERO) {
            "RationalCost denominator must not be zero"
        }

        if (numerator == BigInteger.ZERO) {
            return RationalCost(
                numerator = BigInteger.ZERO,
                denominator = BigInteger.ONE
            )
        }

        val positiveNumerator =
            if (denominator < BigInteger.ZERO) {
                numerator.negate()
            } else {
                numerator
            }

        val positiveDenominator =
            denominator.abs()

        val gcd =
            positiveNumerator.abs().gcd(positiveDenominator)

        return RationalCost(
            numerator = positiveNumerator.divide(gcd),
            denominator = positiveDenominator.divide(gcd)
        )
    }

    /**
     * Returns the canonical textual representation used by RoomConverters.
     *
     * Example:
     *
     *     RationalCost(100, 3) -> "100/3"
     */
    override fun toString(): String {
        val canonicalValue = normalized()

        return "${canonicalValue.numerator}/${canonicalValue.denominator}"
    }
}
