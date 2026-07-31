package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object GraphNavigationMath {
    fun clampedTraceX(currentX: Double, direction: Int, step: Double, xMin: Double, xMax: Double): Double =
        (currentX + direction * step).coerceIn(xMin, xMax)

    fun integerBound(value: Double, roundingMode: RoundingMode): String =
        BigDecimal.valueOf(value).setScale(0, roundingMode).toPlainString()

    /**
     * Rejects segments whose actual midpoint disagrees sharply with straight-line interpolation.
     * Undefined midpoints and pole-like curvature break the graph instead of drawing an asymptote.
     */
    fun shouldConnectSamples(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        yMin: Double,
        yMax: Double,
        evaluate: (Double) -> Double?
    ): Boolean {
        if (!startX.isFinite() || !startY.isFinite() ||
            !endX.isFinite() || !endY.isFinite() ||
            !yMin.isFinite() || !yMax.isFinite() ||
            endX <= startX || yMax <= yMin
        ) {
            return false
        }

        val midpointX = startX + (endX - startX) / 2.0
        val midpointY = evaluate(midpointX)?.takeIf(Double::isFinite) ?: return false
        val interpolatedMidpointY = startY + (endY - startY) / 2.0
        val allowedDeviation = (yMax - yMin) * MAX_MIDPOINT_DEVIATION_IN_WINDOW_SPANS
        return abs(midpointY - interpolatedMidpointY) <= allowedDeviation
    }

    private const val MAX_MIDPOINT_DEVIATION_IN_WINDOW_SPANS = 0.25
}
