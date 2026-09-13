package core.domain.model

import androidx.room.ColumnInfo
import java.math.BigDecimal

/**
 * Pure domain value object representing an exact quantity value.
 *
 * Representation:
 *
 *     storageUnits + QuantityScale
 *
 * where:
 *
 *     physical quantity = storageUnits / 10^scale
 *
 * Examples:
 *
 *     Quantity(100, SCALE_0) = 100 tablets
 *
 *     Quantity(100_500, SCALE_3) = 100.5 mL
 *
 *     Quantity(250, SCALE_3) = 0.25 mL
 *
 * Quantity is responsible for exact mathematical representation and
 * arithmetic only.
 *
 * Quantity does NOT decide:
 * - what physical unit is being measured;
 * - whether the product is discrete or continuous;
 * - the minimum legal transaction increment;
 * - commercial/package conversion;
 * - whether a particular transaction is permitted.
 *
 * Those policies belong to the product/unit domain.
 *
 * No Double or Float is used for authoritative quantity representation
 * or arithmetic.
 *
 * ---------------------------------------------------------------------------
 * SIGNED VALUES
 * ---------------------------------------------------------------------------
 *
 * Quantity permits negative storageUnits.
 *
 * This is intentional because the same exact value object can represent
 * signed quantity deltas such as:
 *
 *     +100 tablets received
 *     -20 tablets dispensed
 *
 * A business operation that requires a strictly positive quantity must
 * enforce that rule at its domain boundary.
 *
 * ---------------------------------------------------------------------------
 * SCALE
 * ---------------------------------------------------------------------------
 *
 * Scale is part of the value.
 *
 * Quantities at different scales are not silently mixed:
 *
 *     Quantity(1, SCALE_0)
 *
 * is not treated as automatically interchangeable with:
 *
 *     Quantity(1000, SCALE_3)
 *
 * even though both may represent one physical unit.
 *
 * This prevents implicit rescaling from entering arithmetic accidentally.
 *
 * ---------------------------------------------------------------------------
 * MINIMUM TRANSACTION INCREMENT
 * ---------------------------------------------------------------------------
 *
 * Quantity does not own a product's minimum increment.
 *
 * It only provides exact validation against a supplied increment:
 *
 *     quantity.isMultipleOf(minimumIncrement)
 *
 * Both quantities must use the same QuantityScale.
 *
 * ProductMaster owns the product-specific minimum transaction increment.
 */
data class Quantity(
    @ColumnInfo(name = "storage_units")
    val storageUnits: Long,
    val scale: QuantityScale
) : Comparable<Quantity> {

    val isZero: Boolean
        get() = storageUnits == 0L

    val isPositive: Boolean
        get() = storageUnits > 0L

    val isNegative: Boolean
        get() = storageUnits < 0L

    /**
     * Adds two quantities only when their scales are identical.
     *
     * Overflow is rejected rather than wrapping around Long.
     */
    operator fun plus(other: Quantity): Quantity {
        requireSameScale(other, "addition")

        return Quantity(
            storageUnits = Math.addExact(
                this.storageUnits,
                other.storageUnits
            ),
            scale = this.scale
        )
    }

    /**
     * Subtracts two quantities only when their scales are identical.
     *
     * Overflow is rejected rather than wrapping around Long.
     */
    operator fun minus(other: Quantity): Quantity {
        requireSameScale(other, "subtraction")

        return Quantity(
            storageUnits = Math.subtractExact(
                this.storageUnits,
                other.storageUnits
            ),
            scale = this.scale
        )
    }

    /**
     * Negates the quantity exactly.
     *
     * Long.MIN_VALUE cannot be negated safely and is therefore rejected by
     * Math.negateExact().
     */
    operator fun unaryMinus(): Quantity {
        return Quantity(
            storageUnits = Math.negateExact(storageUnits),
            scale = scale
        )
    }

    /**
     * Multiplies the quantity by an exact integer multiplier.
     *
     * No floating-point multiplication is permitted.
     */
    operator fun times(multiplier: Long): Quantity {
        return Quantity(
            storageUnits = Math.multiplyExact(
                storageUnits,
                multiplier
            ),
            scale = scale
        )
    }

    operator fun times(multiplier: Int): Quantity =
        times(multiplier.toLong())

    /**
     * Divides this quantity by an exact integer divisor.
     *
     * The result must be exactly representable in the same scale.
     *
     * Example:
     *
     *     Quantity(100, SCALE_0).divideExact(4)
     *         -> Quantity(25, SCALE_0)
     *
     * But:
     *
     *     Quantity(100, SCALE_0).divideExact(3)
     *
     * is rejected because 100 / 3 would require a remainder.
     *
     * This method deliberately does not truncate.
     */
    fun divideExact(divisor: Long): Quantity {
        require(divisor != 0L) {
            "Division by zero"
        }

        if (storageUnits % divisor != 0L) {
            throw ArithmeticException(
                "Cannot divide quantity $storageUnits by $divisor exactly " +
                    "without remainder."
            )
        }

        return Quantity(
            storageUnits = storageUnits / divisor,
            scale = scale
        )
    }

    /**
     * Returns whether this quantity is an exact integer multiple of the
     * supplied minimum transaction increment.
     *
     * The increment is itself a Quantity so that both:
     *
     * - scale
     * - storage representation
     *
     * remain explicit.
     *
     * Example:
     *
     *     quantity = 1.5 mL
     *     increment = 0.5 mL
     *
     * represented at SCALE_3 as:
     *
     *     quantity   = 1500
     *     increment  = 500
     *
     * 1500 % 500 == 0
     *
     * therefore the quantity is valid.
     */
    fun isMultipleOf(
        minimumIncrement: Quantity
    ): Boolean {
        requireSameScale(
            minimumIncrement,
            "minimum-increment validation"
        )

        require(minimumIncrement.storageUnits > 0L) {
            "Minimum transaction increment must be strictly positive, " +
                "but was ${minimumIncrement.storageUnits}."
        }

        return storageUnits % minimumIncrement.storageUnits == 0L
    }

    /**
     * Enforces exact compliance with a minimum transaction increment.
     *
     * This is a mathematical validation only.
     *
     * It does not determine whether the transaction itself is allowed.
     */
    fun validateMultipleOf(
        minimumIncrement: Quantity
    ) {
        require(isMultipleOf(minimumIncrement)) {
            "Quantity $this is not a valid multiple of minimum " +
                "transaction increment $minimumIncrement."
        }
    }

    /**
     * Compares quantities only when their scales are identical.
     *
     * No implicit rescaling is performed.
     */
    override fun compareTo(
        other: Quantity
    ): Int {
        requireSameScale(other, "comparison")

        return storageUnits.compareTo(other.storageUnits)
    }

    /**
     * Formats the exact quantity as a human-readable decimal string.
     *
     * No Double or Float conversion occurs.
     *
     * Examples:
     *
     *     Quantity(100, SCALE_0)
     *         -> "100"
     *
     *     Quantity(500, SCALE_3)
     *         -> "0.5"
     *
     *     Quantity(100_500, SCALE_3)
     *         -> "100.5"
     */
    fun toPlainString(): String {
        if (storageUnits == 0L || scale.scale == 0) {
            return storageUnits.toString()
        }

        return BigDecimal
            .valueOf(storageUnits)
            .movePointLeft(scale.scale)
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * Requires both quantities to use the same exact QuantityScale.
     *
     * This deliberately prevents accidental comparison or arithmetic between
     * differently scaled representations.
     */
    private fun requireSameScale(
        other: Quantity,
        operation: String
    ) {
        require(this.scale == other.scale) {
            "Cannot perform $operation between incompatible scales: " +
                "${this.scale} and ${other.scale}."
        }
    }

    companion object {

        val ZERO_SCALE_0: Quantity =
            Quantity(
                storageUnits = 0L,
                scale = QuantityScale.SCALE_0
            )

        fun zero(
            scale: QuantityScale
        ): Quantity =
            Quantity(
                storageUnits = 0L,
                scale = scale
            )

        fun of(
            storageUnits: Long,
            scale: QuantityScale
        ): Quantity =
            Quantity(
                storageUnits = storageUnits,
                scale = scale
            )

        /**
         * Parses a decimal quantity exactly at the requested scale.
         *
         * Examples:
         *
         *     fromDecimalString("10", SCALE_0)
         *         -> 10 storage units
         *
         *     fromDecimalString("100.5", SCALE_3)
         *         -> 100_500 storage units
         *
         *     fromDecimalString("0.25", SCALE_3)
         *         -> 250 storage units
         *
         * A value that cannot be represented exactly at the requested scale
         * is rejected.
         *
         * Example:
         *
         *     fromDecimalString("0.3333", SCALE_3)
         *
         * is rejected rather than rounded to 0.333.
         */
        fun fromDecimalString(
            decimalString: String,
            scale: QuantityScale
        ): Quantity {

            val bd = try {
                BigDecimal(decimalString.trim())
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException(
                    "Invalid decimal string format: '$decimalString'",
                    e
                )
            }

            val scaled = bd.movePointRight(scale.scale)

            if (scaled.remainder(BigDecimal.ONE).signum() != 0) {
                throw IllegalArgumentException(
                    "Decimal value '$decimalString' cannot be represented " +
                        "exactly at scale ${scale.scale} without precision loss."
                )
            }

            val storageUnits = try {
                scaled.longValueExact()
            } catch (e: ArithmeticException) {
                throw ArithmeticException(
                    "Decimal value '$decimalString' at scale " +
                        "${scale.scale} overflows Long storage units."
                )
            }

            return Quantity(
                storageUnits = storageUnits,
                scale = scale
            )
        }
    }
}
