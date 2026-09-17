package org.mwangaza.app.ui.formatters

import core.domain.model.RationalCost
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * UI-only formatting for exact monetary values.
 *
 * RationalCost is stored in KES minor units (cents). Conversion to major
 * shillings happens only at this final display boundary.
 */
object MoneyDisplayFormatter {

    fun formatMinorUnits(minorUnits: Long): String {
        return "KES " +
            BigDecimal.valueOf(minorUnits)
                .movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString()
    }

    fun formatRationalCost(cost: RationalCost): String {
        val majorShillings = BigDecimal(cost.numerator)
            .divide(
                BigDecimal(cost.denominator),
                2,
                RoundingMode.HALF_UP
            )
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP)

        return "KES ${majorShillings.toPlainString()}"
    }
}
