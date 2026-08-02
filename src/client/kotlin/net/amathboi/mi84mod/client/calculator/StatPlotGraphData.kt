package net.amathboi.mi84mod.client.calculator

import kotlin.math.max
import kotlin.math.min

data class StatPlotPoint(val x: Double, val y: Double)

data class StatPlotSegment(val start: StatPlotPoint, val end: StatPlotPoint)

/** Pure list pairing and window clipping used by Scatter and Line graph rendering. */
object StatPlotGraphData {
    fun points(plotIndex: Int): List<StatPlotPoint?>? {
        val plot = StatPlotSettingsMemory.plot(plotIndex)
        val xValues = CalculatorListMemory.value(plot.xList)?.values ?: return null
        val yValues = CalculatorListMemory.value(plot.yList)?.values ?: return null
        if (xValues.size != yValues.size) return null
        return xValues.indices.map { index ->
            val x = xValues[index].takeIf { it.imaginary == null }?.real?.toDouble()
            val y = yValues[index].takeIf { it.imaginary == null }?.real?.toDouble()
            if (x?.isFinite() == true && y?.isFinite() == true) StatPlotPoint(x, y) else null
        }
    }

    /** Liang-Barsky clipping keeps arbitrary list coordinates from creating huge pixel loops. */
    fun clipSegment(
        start: StatPlotPoint,
        end: StatPlotPoint,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double
    ): StatPlotSegment? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        var lower = 0.0
        var upper = 1.0
        val boundaries = listOf(
            -dx to start.x - xMin,
            dx to xMax - start.x,
            -dy to start.y - yMin,
            dy to yMax - start.y
        )
        boundaries.forEach { (direction, distance) ->
            if (direction == 0.0) {
                if (distance < 0.0) return null
            } else {
                val ratio = distance / direction
                if (direction < 0.0) lower = max(lower, ratio)
                else upper = min(upper, ratio)
                if (lower > upper) return null
            }
        }
        return StatPlotSegment(
            StatPlotPoint(start.x + lower * dx, start.y + lower * dy),
            StatPlotPoint(start.x + upper * dx, start.y + upper * dy)
        )
    }
}
