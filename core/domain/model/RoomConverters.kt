package core.domain.model

import androidx.room.TypeConverter

/**
 * Room persistence converters for authoritative domain value objects.
 *
 * This class acts strictly as a persistence boundary bridging domain models
 * to Room-supported primitive database columns. It contains zero business logic,
 * zero validation belonging to future entities, and strictly enforces exact numeric representations:
 *
 * 1. [Money] ↔ [Long]
 *    - Money is stored as exact minor currency units ([Money.amountMinorUnits] in Long).
 *    - Preserves cents without floating-point conversion, rounding, or string formatting.
 *    - Respects signed arithmetic (negative monetary amounts are preserved).
 *
 * 2. [QuantityScale] ↔ [Int]
 *    - Persisted as an exact integer exponent (0..6).
 *    - Deserialization delegates exclusively to domain authority [QuantityScale.fromInt].
 *    - Invalid integer scale values fail loudly with [IllegalArgumentException].
 *
 * 3. [Quantity] Persistence Boundary:
 *    - An exact [Quantity] consists of two distinct authoritative state components:
 *      [Quantity.storageUnits] (Long) and [Quantity.scale] (QuantityScale).
 *    - Room's single-column @TypeConverter mechanism maps 1-to-1 between a single database column
 *      and a type.
 *    - In Room entities, [Quantity] is persisted losslessly as a two-column composite, either via
 *      Room's `@Embedded` annotation or as explicit entity columns (e.g. `storage_units` Long and
 *      `scale` Int / QuantityScale).
 *    - When `@Embedded` is used, Room persists `storageUnits` directly as a 64-bit integer (Long)
 *      and uses [fromQuantityScale]/[toQuantityScale] for the `scale` column, guaranteeing zero
 *      loss of precision and zero floating-point corruption.
 */
class RoomConverters {

    /**
     * Converts a [Money] domain value object to its exact primitive [amountMinorUnits] (Long).
     */
    @TypeConverter
    fun fromMoney(money: Money?): Long? {
        return money?.amountMinorUnits
    }

    /**
     * Reconstructs a [Money] domain value object from its stored [amountMinorUnits] (Long).
     */
    @TypeConverter
    fun toMoney(amountMinorUnits: Long?): Money? {
        return amountMinorUnits?.let { Money(it) }
    }

    /**
     * Converts a [QuantityScale] enum to its primitive integer scale exponent (0..6).
     */
    @TypeConverter
    fun fromQuantityScale(scale: QuantityScale?): Int? {
        return scale?.scale
    }

    /**
     * Reconstructs a [QuantityScale] from its stored integer scale exponent (0..6).
     * Fails loudly via [QuantityScale.fromInt] if [scaleValue] is not between 0 and 6.
     */
    @TypeConverter
    fun toQuantityScale(scaleValue: Int?): QuantityScale? {
        return scaleValue?.let { QuantityScale.fromInt(it) }
    }
}
