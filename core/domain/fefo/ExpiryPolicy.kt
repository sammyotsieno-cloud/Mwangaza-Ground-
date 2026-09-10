package core.domain.fefo

import core.domain.model.StockBatch
import core.domain.time.LocalDateValue
import java.util.Calendar
import java.util.TimeZone

/**
 * Operational classification of batch expiry status relative to a facility calendar date.
 */
enum class ExpiryStatus {
    /**
     * Product batch has no recorded expiry date, or product is a non-expiring commodity.
     */
    UNKNOWN_OR_NON_EXPIRING,

    /**
     * Batch expiry date precedes the facility calendar date (expiryDate < facilityCalendarDate).
     */
    EXPIRED,

    /**
     * Batch expiry date matches the facility calendar date exactly (expiryDate == facilityCalendarDate).
     */
    EXPIRES_TODAY,

    /**
     * Batch expiry date is strictly in the future but within the configured warning threshold.
     */
    EXPIRING_SOON,

    /**
     * Batch expiry date is safely beyond the configured warning threshold.
     */
    VALID
}

/**
 * Facility-level operational policy configuration governing batch expiry evaluation
 * and dispensing eligibility.
 *
 * Separation of Concerns:
 * - Expiry status evaluation answers: "What is the physical time state of this batch?"
 * - Dispensing eligibility answers: "Under this facility's policy, may this batch be selected for normal dispensing?"
 * - "Expires today is excluded from dispensing" is modeled explicitly as a configurable FACILITY POLICY
 *   ([excludeExpiringTodayFromDispensing]), not an immutable universal legal assertion.
 */
data class ExpiryPolicy(
    /**
     * Number of calendar days ahead to flag stock as [ExpiryStatus.EXPIRING_SOON]. Default: 90 days.
     */
    val warningThresholdDays: Int = DEFAULT_WARNING_THRESHOLD_DAYS,

    /**
     * Facility policy flag determining whether batches expiring on the current calendar day
     * are quarantined / excluded from standard dispensing candidate selection. Default: true.
     */
    val excludeExpiringTodayFromDispensing: Boolean = true
) {
    init {
        require(warningThresholdDays >= 0) {
            "warningThresholdDays must be non-negative, got: $warningThresholdDays"
        }
    }

    /**
     * Evaluates the [ExpiryStatus] of a [StockBatch] given its [expiryDateInt] (YYYYMMDD or -1)
     * against the facility's local calendar date ([facilityCalendarDate]).
     */
    fun evaluate(expiryDateInt: Int, facilityCalendarDate: LocalDateValue): ExpiryStatus {
        if (expiryDateInt == StockBatch.EXPIRY_UNKNOWN_OR_NONE) {
            return ExpiryStatus.UNKNOWN_OR_NON_EXPIRING
        }

        val expiryDate = StockBatch.parseExpiryDateInt(expiryDateInt)
        return when {
            expiryDate < facilityCalendarDate -> ExpiryStatus.EXPIRED
            expiryDate == facilityCalendarDate -> ExpiryStatus.EXPIRES_TODAY
            else -> {
                val thresholdDate = addDays(facilityCalendarDate, warningThresholdDays)
                if (expiryDate <= thresholdDate) {
                    ExpiryStatus.EXPIRING_SOON
                } else {
                    ExpiryStatus.VALID
                }
            }
        }
    }

    /**
     * Determines whether stock with the given [status] is eligible for standard dispensing selection
     * under this policy.
     */
    fun isEligibleForDispensing(status: ExpiryStatus): Boolean {
        return when (status) {
            ExpiryStatus.EXPIRED -> false
            ExpiryStatus.EXPIRES_TODAY -> !excludeExpiringTodayFromDispensing
            ExpiryStatus.EXPIRING_SOON,
            ExpiryStatus.VALID,
            ExpiryStatus.UNKNOWN_OR_NON_EXPIRING -> true
        }
    }

    companion object {
        const val DEFAULT_WARNING_THRESHOLD_DAYS = 90

        val DEFAULT: ExpiryPolicy = ExpiryPolicy()

        /**
         * Adds [days] to a [LocalDateValue] using UTC Gregorian calendar math.
         */
        internal fun addDays(date: LocalDateValue, days: Int): LocalDateValue {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(date.year, date.month - 1, date.day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_MONTH, days)
            return LocalDateValue(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
