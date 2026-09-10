package core.domain.time

/**
 * Value object representing a verified calendar date (year, month, day) in the Gregorian calendar.
 *
 * Characteristics:
 * - Independent of platform date/time desugaring requirements across Android API levels.
 * - Enforces strict Gregorian calendar rules:
 *     * Leap year validation (e.g. 2024-02-29 is valid, 2025-02-29 is rejected).
 *     * Exact month length boundaries (e.g. 2026-04-31 and 2026-02-30 are rejected).
 * - [month] is 1-indexed (1 = January, 12 = December).
 * - [day] is 1-indexed (1..31, constrained by the specific month and year).
 * - Provides natural chronological ordering via [Comparable].
 */
data class LocalDateValue(
    val year: Int,
    val month: Int,
    val day: Int
) : Comparable<LocalDateValue> {

    init {
        require(year in 1900..3000) { "LocalDateValue year must be between 1900 and 3000, got: $year" }
        require(month in 1..12) { "LocalDateValue month must be between 1 and 12, got: $month" }
        val maxDays = maxDaysInMonth(year, month)
        require(day in 1..maxDays) {
            "LocalDateValue day $day is invalid for month $month in year $year (valid range: 1..$maxDays)"
        }
    }

    /**
     * Standard ISO-8601 calendar date representation: "YYYY-MM-DD".
     */
    val isoDateString: String
        get() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    override fun compareTo(other: LocalDateValue): Int {
        val yearDiff = this.year.compareTo(other.year)
        if (yearDiff != 0) return yearDiff
        val monthDiff = this.month.compareTo(other.month)
        if (monthDiff != 0) return monthDiff
        return this.day.compareTo(other.day)
    }

    override fun toString(): String = isoDateString

    companion object {
        private val ISO_DATE_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

        /**
         * Determines whether the specified [year] is a leap year according to the Gregorian calendar.
         */
        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        /**
         * Returns the maximum number of valid calendar days in the given [month] of [year].
         */
        fun maxDaysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }

        /**
         * Parses a standard "YYYY-MM-DD" formatted string into a [LocalDateValue].
         * Fails strictly on malformed strings or invalid calendar dates (no silent normalization).
         */
        fun parseIso(isoString: String): LocalDateValue {
            val match = ISO_DATE_REGEX.matchEntire(isoString.trim())
                ?: throw IllegalArgumentException("Invalid ISO date format, expected YYYY-MM-DD, got: '$isoString'")

            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()

            return LocalDateValue(year = year, month = month, day = day)
        }
    }
}
