package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.RoundingMode

object GraphNavigationMath {
    fun clampedTraceX(currentX: Double, direction: Int, step: Double, xMin: Double, xMax: Double): Double =
        (currentX + direction * step).coerceIn(xMin, xMax)

    fun integerBound(value: Double, roundingMode: RoundingMode): String =
        BigDecimal.valueOf(value).setScale(0, roundingMode).toPlainString()
}
