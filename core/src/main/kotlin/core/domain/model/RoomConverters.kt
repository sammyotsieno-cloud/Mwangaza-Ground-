package core.domain.model

import androidx.room.TypeConverter
import java.math.BigInteger

/**
 * Room persistence converters for authoritative domain value objects.
 *
 * This class is strictly a persistence boundary. It contains no business
 * calculations and performs no display rounding.
 *
 * Persistence representations:
 *
 * 1. [Money] ↔ [Long]
 *    - Money is stored as exact minor currency units.
 *    - No floating-point conversion or formatting is involved.
 *
 * 2. [QuantityScale] ↔ [Int]
 *    - QuantityScale is stored as its exact integer exponent.
 *
 * 3. [RationalCost] ↔ [String]
 *    - RationalCost may require arbitrary-precision numerator and denominator.
 *    - It is therefore persisted as one canonical textual value:
 *        numerator/denominator
 *    - Example:
 *        RationalCost(100, 3) → "100/3"
 *    - String persistence avoids Long overflow and floating-point precision loss.
 *
 * 4. [Quantity] Persistence Boundary
 *    - Quantity consists of storageUnits and scale.
 *    - These are persisted as separate columns when Quantity is embedded or
 *      represented explicitly by the entity schema.
 */
class RoomConverters {

    /**
     * Converts a [Money] domain value object to its exact primitive
     * [Money.amountMinorUnits] representation.
     */
    @TypeConverter
    fun fromMoney(money: Money?): Long? {
        return money?.amountMinorUnits
    }

    /**
     * Reconstructs a [Money] domain value object from its stored minor units.
     */
    @TypeConverter
    fun toMoney(amountMinorUnits: Long?): Money? {
        return amountMinorUnits?.let { Money(it) }
    }

    /**
     * Converts a [QuantityScale] to its exact integer exponent.
     */
    @TypeConverter
    fun fromQuantityScale(scale: QuantityScale?): Int? {
        return scale?.scale
    }

    /**
     * Reconstructs a [QuantityScale] from its persisted integer exponent.
     */
    @TypeConverter
    fun toQuantityScale(scaleValue: Int?): QuantityScale? {
        return scaleValue?.let { QuantityScale.fromInt(it) }
    }

    /**
     * Converts an exact [RationalCost] into its lossless persistence form.
     *
     * The representation is:
     *
     *     numerator/denominator
     *
     * Both components are arbitrary-precision integers.
     */
    @TypeConverter
    fun fromRationalCost(rationalCost: RationalCost?): String? {
        return rationalCost?.let {
            "${it.numerator}/${it.denominator}"
        }
    }

    /**
     * Reconstructs an exact [RationalCost] from its persisted representation.
     *
     * The persisted value must contain exactly one '/' separating the
     * numerator and denominator.
     */
    @TypeConverter
    fun toRationalCost(value: String?): RationalCost? {
        return value?.let {
            val separatorIndex = it.indexOf('/')

            require(separatorIndex > 0 && separatorIndex < it.lastIndex) {
                "Invalid RationalCost persistence value: '$it'"
            }

            require(it.indexOf('/', separatorIndex + 1) == -1) {
                "Invalid RationalCost persistence value: '$it'"
            }

            val numeratorText = it.substring(0, separatorIndex)
            val denominatorText = it.substring(separatorIndex + 1)

            RationalCost(
                numerator = BigInteger(numeratorText),
                denominator = BigInteger(denominatorText)
            )
        }
    }
}
