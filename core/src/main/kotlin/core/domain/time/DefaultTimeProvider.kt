package core.domain.time

import java.util.Calendar
import java.util.TimeZone

/**
 * Validates and caches IANA timezone identifiers to prevent silent fallback to GMT.
 */
object TimezoneValidator {
    private val availableZoneIds: Set<String> by lazy {
        TimeZone.getAvailableIDs().toSet()
    }

    /**
     * Validates that [timezoneId] corresponds to a genuine, recognized IANA timezone identifier.
     * Throws [IllegalArgumentException] for blank, invalid, or unrecognized strings.
     */
    fun getValidatedTimeZone(timezoneId: String): TimeZone {
        require(timezoneId.isNotBlank()) { "Timezone identifier must not be blank" }
        val trimmed = timezoneId.trim()
        require(trimmed == "UTC" || trimmed == "GMT" || availableZoneIds.contains(trimmed)) {
            "Invalid or unsupported IANA timezone identifier: '$timezoneId'"
        }
        return TimeZone.getTimeZone(trimmed)
    }
}

/**
 * Internal helper for timezone-aware calendar conversions.
 * Shared between production and deterministic implementations to eliminate logic drift.
 */
internal object TimeZoneCalendarHelper {

    fun getCalendarFor(timezoneId: String, instant: Long): Calendar {
        val timeZone = TimezoneValidator.getValidatedTimeZone(timezoneId)
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = instant
        return calendar
    }

    fun computeLocalDate(timezoneId: String, instant: Long): LocalDateValue {
        val calendar = getCalendarFor(timezoneId, instant)
        return LocalDateValue(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-indexed (0 = January)
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun computeStartOfDay(timezoneId: String, instant: Long): Long {
        val calendar = getCalendarFor(timezoneId, instant)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun computeStartOfNextDay(timezoneId: String, instant: Long): Long {
        val calendar = getCalendarFor(timezoneId, instant)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Advance by exactly one calendar day in the target timezone (handles DST transitions correctly)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }
}

/**
 * Standard production implementation of [TimeProvider] utilizing the physical device clock.
 *
 * Implementation Details:
 * - Uses `System.currentTimeMillis()` for current absolute instants.
 * - Uses validated IANA timezones and [java.util.Calendar] for timezone-aware date calculations,
 *   guaranteeing full compatibility across all Android API levels without requiring library desugaring.
 */
class DefaultTimeProvider : TimeProvider {

    override fun now(): Long = System.currentTimeMillis()

    override fun localDate(timezoneId: String, instant: Long): LocalDateValue =
        TimeZoneCalendarHelper.computeLocalDate(timezoneId, instant)

    override fun startOfDay(timezoneId: String, instant: Long): Long =
        TimeZoneCalendarHelper.computeStartOfDay(timezoneId, instant)

    override fun startOfNextDay(timezoneId: String, instant: Long): Long =
        TimeZoneCalendarHelper.computeStartOfNextDay(timezoneId, instant)
}

/**
 * Deterministic implementation of [TimeProvider] for unit and integration testing.
 *
 * Features:
 * - Allows fixing and advancing time deterministically.
 * - Uses [Math.addExact] in [advanceByMillis] to detect and prevent arithmetic overflow.
 * - Shares the exact same timezone validation and calendar math as [DefaultTimeProvider].
 */
class DeterministicTimeProvider(
    private var currentInstant: Long
) : TimeProvider {

    init {
        require(currentInstant > 0L) { "Initial instant must be positive, got: $currentInstant" }
    }

    override fun now(): Long = currentInstant

    fun setInstant(newInstant: Long) {
        require(newInstant > 0L) { "Instant must be positive, got: $newInstant" }
        currentInstant = newInstant
    }

    fun advanceByMillis(deltaMillis: Long) {
        require(deltaMillis >= 0L) { "Time delta must be non-negative, got: $deltaMillis" }
        // Overflow-safe addition: throws ArithmeticException on Long overflow
        currentInstant = Math.addExact(currentInstant, deltaMillis)
    }

    override fun localDate(timezoneId: String, instant: Long): LocalDateValue =
        TimeZoneCalendarHelper.computeLocalDate(timezoneId, instant)

    override fun startOfDay(timezoneId: String, instant: Long): Long =
        TimeZoneCalendarHelper.computeStartOfDay(timezoneId, instant)

    override fun startOfNextDay(timezoneId: String, instant: Long): Long =
        TimeZoneCalendarHelper.computeStartOfNextDay(timezoneId, instant)
}
