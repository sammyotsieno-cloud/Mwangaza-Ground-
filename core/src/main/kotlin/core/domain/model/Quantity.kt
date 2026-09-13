package core.domain.model
import androidx.room.ColumnInfo
import java.math.BigDecimal
/**
 * Pure domain value object representing an exact physical quantity.
 *
 * Stored internally as exact [storageUnits] (Long) together with a [scale]
 * (QuantityScale). Authoritative quantity arithmetic is strictly integer-based
 * with overflow checking. No Double or Float is used for authoritative
 * representation or calculation.
 *
 * When embedded by Room, [storageUnits] is persisted as the explicit
 * `storage_units` column. This gives embedded quantities deterministic column
 * names such as `quantity_storage_units` and
 * `remaining_quantity_storage_units` when prefixes are used.
 */
data class Quantity(
    @ColumnInfo(name = "storage_units")
    val storageUnits: Long,
    val scale: QuantityScale
) : Comparable<Quantity> {

    val isZero: Boolean get() = storageUnits == 0L
    val isPositive: Boolean get() = storageUnits > 0L
    val isNegative: Boolean get() = storageUnits < 0L

    operator fun plus(other: Quantity): Quantity {
        requireSameScale(other, "addition")
        return Quantity(
            storageUnits = Math.addExact(this.storageUnits, other.storageUnits),
            scale = this.scale
        )
    }

    operator fun minus(other: Quantity): Quantity {
        requireSameScale(other, "subtraction")
        return Quantity(
            storageUnits = Math.subtractExact(this.storageUnits, other.storageUnits),
            scale = this.scale
        )
    }

    operator fun unaryMinus(): Quantity {
        return Quantity(
            storageUnits = Math.negateExact(this.storageUnits),
            scale = this.scale
        )
    }

    operator fun times(multiplier: Long): Quantity {
        return Quantity(
            storageUnits = Math.multiplyExact(this.storageUnits, multiplier),
            scale = this.scale
        )
    }

    operator fun times(multiplier: Int): Quantity {
        return times(multiplier.toLong())
    }

    /**
     * Divides this quantity by an exact integer divisor.
     * Throws [ArithmeticException] if division leaves a non-zero remainder.
     * Never silently truncates a physical quantity.
     */
    fun divideExact(divisor: Long): Quantity {
        require(divisor != 0L) { "Division by zero" }

        if (storageUnits % divisor != 0L) {
            throw ArithmeticException(
                "Cannot divide quantity $storageUnits by $divisor exactly without remainder."
            )
        }

        return Quantity(storageUnits / divisor, scale)
    }

    /**
     * Validates whether this quantity's storageUnits is an exact integer multiple
     * of the specified [minimumIncrement].
     *
     * @param minimumIncrement Must be a positive integer in the same scaled
     * storage-unit representation.
     */
    fun isMultipleOf(minimumIncrement: Long): Boolean {
        require(minimumIncrement > 0L) {
            "Minimum increment must be positive, but was $minimumIncrement"
        }

        return storageUnits % minimumIncrement == 0L
    }

    /**
     * Enforces that this quantity's storageUnits is an exact integer multiple
     * of [minimumIncrement].
     */
    fun validateMultipleOf(minimumIncrement: Long) {
        require(isMultipleOf(minimumIncrement)) {
            "Quantity storageUnits $storageUnits is not a valid multiple " +
                "of minimum increment $minimumIncrement at scale ${scale.scale}."
        }
    }

    override fun compareTo(other: Quantity): Int {
        requireSameScale(other, "comparison")
        return this.storageUnits.compareTo(other.storageUnits)
    }

    /**
     * Formats this quantity as a human-readable decimal string without using
     * Double/Float.
     *
     * Example:
     * 500 storage units at scale 3 -> "0.5"
     */
    fun toPlainString(): String {
        if (storageUnits == 0L || scale.scale == 0) {
            return storageUnits.toString()
        }

        val bd = BigDecimal
            .valueOf(storageUnits)
            .movePointLeft(scale.scale)

        return bd.stripTrailingZeros().toPlainString()
    }

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

        val ZERO_SCALE_0 = Quantity(
            0L,
            QuantityScale.SCALE_0
        )

        fun zero(scale: QuantityScale): Quantity =
            Quantity(0L, scale)

        fun of(
            storageUnits: Long,
            scale: QuantityScale
        ): Quantity =
            Quantity(storageUnits, scale)

        /**
         * Parses a decimal string such as "0.5", "1.25", or "10"
         * into a Quantity at the specified scale.
         *
         * Fails deterministically if the value cannot be represented exactly
         * at the requested scale or if Long storage would overflow.
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

            /*
             * Use BigDecimal.longValueExact() directly.
             *
             * This preserves exactness and Long overflow detection while
             * avoiding BigInteger.longValueExact(), which requires API 31
             * on the Android API surface used by this project.
             */
            val storageUnits = try {
                scaled.longValueExact()
            } catch (e: ArithmeticException) {
                throw ArithmeticException(
                    "Decimal value '$decimalString' at scale ${scale.scale} " +
                        "overflows Long storage units."
                )
            }

            return Quantity(
                storageUnits,
                scale
            )
        }
    }
}
