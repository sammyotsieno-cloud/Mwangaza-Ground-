package core.domain.time

/**
 * Centralized abstraction for time operations across the application.
 *
 * Architectural Purpose:
 * - Eliminates direct, non-deterministic calls to `System.currentTimeMillis()` in business logic.
 * - Enables deterministic unit and integration testing of time-sensitive workflows:
 *     * PriceHistory interval transitions [effectiveFrom, effectiveTo)
 *     * Batch expiry dates and FEFO sequencing
 *     * Daily and monthly reporting cutoffs using half-open day intervals [startOfDay, startOfNextDay)
 *     * Historical audit logging and transaction timestamps
 * - Decouples time operations from database entities: receives the facility's IANA timezone
 *   (e.g., from [FacilityProfile.facilityTimezone]) as an explicit input parameter.
 *
 * Half-Open Day Interval Architecture:
 * - A facility-local calendar day is modeled as the half-open interval:
 *   `[startOfDay, startOfNextDay)`
 *   where the lower bound (startOfDay) is inclusive and the upper bound (startOfNextDay) is exclusive.
 * - Eliminates millisecond-boundary defects (such as 23:59:59.999), leap-second hazards, and DST anomalies.
 * - Does NOT assume a day is always 24 hours (86,400,000 ms); calendar days are resolved natively
 *   within the specified IANA timezone.
 *
 * Offline & Permissions:
 * - In Phase 1, the physical clock source is the local system/device clock.
 * - Requires NO Android Calendar permissions (READ_CALENDAR / WRITE_CALENDAR).
 * - Requires NO mandatory network time synchronization.
 */
interface TimeProvider {

    /**
     * Returns the current absolute point in time as epoch milliseconds.
     */
    fun now(): Long

    /**
     * Converts an absolute epoch millisecond [instant] into a facility-local calendar date
     * using the specified validated IANA [timezoneId] (e.g. "Africa/Nairobi").
     * Defaults to the current instant if none is provided.
     */
    fun localDate(timezoneId: String, instant: Long = now()): LocalDateValue

    /**
     * Calculates the exact start-of-day epoch millisecond instant (00:00:00.000)
     * for the given [instant] in the specified [timezoneId].
     */
    fun startOfDay(timezoneId: String, instant: Long = now()): Long

    /**
     * Calculates the exact start-of-next-day epoch millisecond instant (00:00:00.000 of the following calendar day)
     * in the specified [timezoneId].
     * Together with [startOfDay], defines the half-open interval `[startOfDay, startOfNextDay)`.
     */
    fun startOfNextDay(timezoneId: String, instant: Long = now()): Long
}
