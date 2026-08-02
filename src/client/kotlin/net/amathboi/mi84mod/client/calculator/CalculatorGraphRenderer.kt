package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.ui.GraphWindow
import net.amathboi.mi84mod.client.calculator.ui.ZoomGraphOperation
import net.minecraft.client.gui.GuiGraphics

/** Graph-only renderer, including axes, functions, trace, and interactive zoom markers. */
class CalculatorGraphRenderer(private val controller: CalculatorController) {
    fun render(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt() - 1
        val displayTop = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val displayBottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING) * scaleY).toInt() - 1
        val graphWindow = controller.readGraphWindow()

        if (graphWindow == null) {
            CalculatorTextRenderer.draw(guiGraphics, "INVALID WINDOW", left, displayTop)
            return
        }

        val tracing = controller.state.trace != null
        val top = displayTop + if (tracing && FormatSettingsMemory.showsExpressions()) {
            DISPLAY_LINE_HEIGHT
        } else {
            0
        }
        val bottom = displayBottom - if (tracing && FormatSettingsMemory.showsCoordinates()) {
            DISPLAY_LINE_HEIGHT
        } else {
            0
        }
        if (tracing) renderTraceLabels(guiGraphics, left, right, displayTop, displayBottom, graphWindow)
        renderGraphGrid(guiGraphics, left, right, top, bottom, graphWindow)
        if (FormatSettingsMemory.showsAxes()) {
            renderGraphAxes(guiGraphics, left, right, top, bottom, graphWindow)
        }
        if (FormatSettingsMemory.showsLabels()) {
            renderGraphAxisLabels(guiGraphics, left, right, top, bottom, graphWindow)
        }
        repeat(YEqualsMemory.subscripts.size) { equationIndex ->
            val expression = YEqualsMemory.equation(equationIndex)
            if (expression.isNotEmpty()) {
                renderGraphFunction(
                    guiGraphics,
                    expression,
                    YEqualsMemory.colors[equationIndex],
                    left,
                    right,
                    top,
                    bottom,
                    graphWindow
                )
            }
        }
        renderStatPlots(guiGraphics, left, right, top, bottom, graphWindow)
        renderGraphTrace(guiGraphics, left, right, top, bottom, graphWindow)
        renderZoomGraphSelection(guiGraphics, left, right, top, bottom, graphWindow)
    }

    fun displayAspect(width: Int, height: Int): Double {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val graphWidth = (DISPLAY_RIGHT - DISPLAY_LEFT - DISPLAY_PADDING * 2) * scaleX
        val graphHeight = (DISPLAY_BOTTOM - DISPLAY_TOP - DISPLAY_PADDING * 2) * scaleY
        return (graphWidth / graphHeight).toDouble()
    }

    private fun renderZoomGraphSelection(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val zoom = controller.state.zoomGraph ?: return
        val cursorX = graphXToPixel(zoom.x, left, right, graphWindow)
        val cursorY = graphYToPixel(zoom.y, top, bottom, graphWindow)

        if (zoom.operation == ZoomGraphOperation.BOX && zoom.anchorX != null && zoom.anchorY != null) {
            val anchorX = graphXToPixel(zoom.anchorX!!, left, right, graphWindow)
            val anchorY = graphYToPixel(zoom.anchorY!!, top, bottom, graphWindow)
            val boxLeft = min(anchorX, cursorX)
            val boxRight = max(anchorX, cursorX)
            val boxTop = min(anchorY, cursorY)
            val boxBottom = max(anchorY, cursorY)
            guiGraphics.fill(boxLeft, boxTop, boxRight + 1, boxTop + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxLeft, boxBottom, boxRight + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxLeft, boxTop, boxLeft + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxRight, boxTop, boxRight + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
        }

        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            guiGraphics.fill(cursorX - 3, cursorY, cursorX + 4, cursorY + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(cursorX, cursorY - 3, cursorX + 1, cursorY + 4, DISPLAY_TEXT_COLOR)
        }
    }

    private fun renderTraceLabels(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        displayTop: Int,
        displayBottom: Int,
        graphWindow: GraphWindow
    ) {
        val trace = controller.state.trace ?: return
        val color = YEqualsMemory.colors[trace.functionIndex]
        if (FormatSettingsMemory.showsExpressions()) {
            val functionLabel =
                "Y${YEqualsMemory.subscripts[trace.functionIndex]}=${YEqualsMemory.equation(trace.functionIndex)}"
            CalculatorTextRenderer.draw(guiGraphics, functionLabel, left, displayTop, color)
        }

        if (!FormatSettingsMemory.showsCoordinates()) return

        val footerY = displayBottom - DISPLAY_LINE_HEIGHT + TRACE_FOOTER_OFFSET
        val graphY = CalculatorDisplayMemory.evaluateForGraph(
            YEqualsMemory.equation(trace.functionIndex), trace.x
        )
        val (firstLabel, secondLabel) = if (FormatSettingsMemory.usesPolarCoordinates()) {
            if (graphY == null) {
                "r=ERR" to "θ=ERR"
            } else {
                val radius = hypot(trace.x, graphY)
                val rawAngle = atan2(graphY, trace.x)
                val angle = if (ModeSettingsMemory.usesDegrees()) Math.toDegrees(rawAngle) else rawAngle
                "r=${formatTraceValue(radius)}" to "θ=${formatTraceValue(angle)}"
            }
        } else {
            "X=${formatTraceValue(trace.x)}" to
                (graphY?.let { "Y=${formatTraceValue(it)}" } ?: "Y=ERR")
        }
        CalculatorTextRenderer.draw(guiGraphics, firstLabel, left, footerY, color)
        val yAxisX = if (0.0 in graphWindow.xMin..graphWindow.xMax) {
            graphXToPixel(0.0, left, right, graphWindow)
        } else {
            (left + right) / 2
        }
        CalculatorTextRenderer.draw(guiGraphics, secondLabel, yAxisX, footerY, color)
    }

    private fun formatTraceValue(value: Double): String {
        if (!value.isFinite()) return "ERR"
        val decimal = BigDecimal.valueOf(value)
        for (decimalPlaces in 6 downTo 0) {
            val rounded = decimal.setScale(decimalPlaces, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            if (rounded.length <= TRACE_VALUE_MAX_CHARACTERS) return rounded
        }
        return "OVERFLOW"
    }

    private fun renderGraphTrace(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val trace = controller.state.trace ?: return
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val expression = YEqualsMemory.equation(trace.functionIndex)
        val graphY = CalculatorDisplayMemory.evaluateForGraph(expression, trace.x) ?: return
        if (!graphY.isFinite() || trace.x !in graphWindow.xMin..graphWindow.xMax ||
            graphY !in graphWindow.yMin..graphWindow.yMax
        ) return

        val markerX = graphXToPixel(trace.x, left, right, graphWindow)
        val markerY = graphYToPixel(graphY, top, bottom, graphWindow)
        for (offset in -2..2) {
            guiGraphics.fill(
                markerX + offset,
                markerY + offset,
                markerX + offset + 1,
                markerY + offset + 1,
                DISPLAY_TEXT_COLOR
            )
            guiGraphics.fill(
                markerX + offset,
                markerY - offset,
                markerX + offset + 1,
                markerY - offset + 1,
                DISPLAY_TEXT_COLOR
            )
        }
    }

    private fun renderGraphAxes(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val xAxisY = if (0.0 in graphWindow.yMin..graphWindow.yMax) {
            graphYToPixel(0.0, top, bottom, graphWindow)
        } else {
            null
        }
        val yAxisX = if (0.0 in graphWindow.xMin..graphWindow.xMax) {
            graphXToPixel(0.0, left, right, graphWindow)
        } else {
            null
        }
        xAxisY?.let { guiGraphics.fill(left, it, right + 1, it + 1, GRAPH_AXIS_COLOR) }
        yAxisX?.let { guiGraphics.fill(it, top, it + 1, bottom + 1, GRAPH_AXIS_COLOR) }

        if (xAxisY != null) {
            forEachTick(graphWindow.xMin, graphWindow.xMax, graphWindow.xScale) { tick ->
                val tickX = graphXToPixel(tick, left, right, graphWindow)
                guiGraphics.fill(tickX, xAxisY - 1, tickX + 1, xAxisY + 2, GRAPH_AXIS_COLOR)
            }
        }
        if (yAxisX != null) {
            forEachTick(graphWindow.yMin, graphWindow.yMax, graphWindow.yScale) { tick ->
                val tickY = graphYToPixel(tick, top, bottom, graphWindow)
                guiGraphics.fill(yAxisX - 1, tickY, yAxisX + 2, tickY + 1, GRAPH_AXIS_COLOR)
            }
        }
    }

    private fun renderGraphGrid(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val style = FormatSettingsMemory.gridStyle()
        if (style == "GridOff") return
        val xTicks = tickValues(graphWindow.xMin, graphWindow.xMax, graphWindow.xScale)
        val yTicks = tickValues(graphWindow.yMin, graphWindow.yMax, graphWindow.yScale)
        if (xTicks.size.toLong() * yTicks.size.toLong() > MAX_GRID_POINTS) return

        if (style == "GridLine") {
            xTicks.forEach { tick ->
                val tickX = graphXToPixel(tick, left, right, graphWindow)
                guiGraphics.fill(tickX, top, tickX + 1, bottom + 1, GRAPH_GRID_COLOR)
            }
            yTicks.forEach { tick ->
                val tickY = graphYToPixel(tick, top, bottom, graphWindow)
                guiGraphics.fill(left, tickY, right + 1, tickY + 1, GRAPH_GRID_COLOR)
            }
        } else {
            xTicks.forEach { xTick ->
                val tickX = graphXToPixel(xTick, left, right, graphWindow)
                yTicks.forEach { yTick ->
                    val tickY = graphYToPixel(yTick, top, bottom, graphWindow)
                    guiGraphics.fill(tickX, tickY, tickX + 1, tickY + 1, GRAPH_GRID_COLOR)
                }
            }
        }
    }

    private fun renderGraphAxisLabels(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val xAxisY = if (0.0 in graphWindow.yMin..graphWindow.yMax) {
            graphYToPixel(0.0, top, bottom, graphWindow)
        } else {
            bottom - DISPLAY_LINE_HEIGHT
        }
        val yAxisX = if (0.0 in graphWindow.xMin..graphWindow.xMax) {
            graphXToPixel(0.0, left, right, graphWindow)
        } else {
            left
        }
        CalculatorTextRenderer.draw(guiGraphics, "X", right - 4, xAxisY, GRAPH_AXIS_COLOR)
        CalculatorTextRenderer.draw(guiGraphics, "Y", yAxisX + 1, top, GRAPH_AXIS_COLOR)
    }

    private fun renderGraphFunction(
        guiGraphics: GuiGraphics,
        expression: String,
        color: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val plotWidth = (right - left).coerceAtLeast(1)
        val sampleColumns =
            (0..plotWidth step graphWindow.xResolution.coerceAtMost(plotWidth)).toMutableList()
        if (sampleColumns.lastOrNull() != plotWidth) sampleColumns += plotWidth

        var previous: GraphSample? = null
        sampleColumns.forEach { column ->
            val graphX =
                graphWindow.xMin + column.toDouble() / plotWidth * (graphWindow.xMax - graphWindow.xMin)
            val graphY = CalculatorDisplayMemory.evaluateForGraph(expression, graphX)
            val current = graphY?.let { GraphSample(left + column, graphX, it) }
            if (current != null) {
                previous?.let { prior ->
                    if (GraphNavigationMath.shouldConnectSamples(
                            prior.x,
                            prior.y,
                            current.x,
                            current.y,
                            graphWindow.yMin,
                            graphWindow.yMax
                        ) { midpointX ->
                            CalculatorDisplayMemory.evaluateForGraph(expression, midpointX)
                        }
                    ) {
                        drawGraphSegment(guiGraphics, prior, current, color, top, bottom, graphWindow)
                    }
                }
                if (graphY in graphWindow.yMin..graphWindow.yMax) {
                    val pixelY = graphYToPixel(graphY, top, bottom, graphWindow)
                    guiGraphics.fill(current.pixelX, pixelY, current.pixelX + 1, pixelY + 1, color)
                }
            }
            previous = current
        }
    }

    /** Phase 6 graph integration begins with the point-based Scatter and Line types. */
    private fun renderStatPlots(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        guiGraphics.enableScissor(left, top, right + 1, bottom + 1)
        repeat(StatPlotSettingsMemory.size()) { plotIndex ->
            val plot = StatPlotSettingsMemory.plot(plotIndex)
            if (!plot.enabled || !StatPlotSettingsMemory.typeRendersOnGraph(plot.type)) {
                return@repeat
            }
            val points = StatPlotGraphData.points(plotIndex) ?: return@repeat
            var previous: StatPlotPoint? = null
            points.forEach { point ->
                if (point == null) {
                    previous = null
                    return@forEach
                }
                if (plot.type == StatPlotType.LINE) {
                    previous?.let { prior ->
                        StatPlotGraphData.clipSegment(
                            prior,
                            point,
                            graphWindow.xMin,
                            graphWindow.xMax,
                            graphWindow.yMin,
                            graphWindow.yMax
                        )?.let { segment ->
                            drawStatPlotSegment(
                                guiGraphics,
                                segment,
                                plot.color,
                                left,
                                right,
                                top,
                                bottom,
                                graphWindow
                            )
                        }
                    }
                }
                if (point.x in graphWindow.xMin..graphWindow.xMax &&
                    point.y in graphWindow.yMin..graphWindow.yMax
                ) {
                    drawStatPlotMark(
                        guiGraphics,
                        graphXToPixel(point.x, left, right, graphWindow),
                        graphYToPixel(point.y, top, bottom, graphWindow),
                        plot.mark,
                        plot.color
                    )
                }
                previous = point
            }
        }
        guiGraphics.disableScissor()
    }

    private fun drawStatPlotSegment(
        guiGraphics: GuiGraphics,
        segment: StatPlotSegment,
        color: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val startX = graphXToPixel(segment.start.x, left, right, graphWindow)
        val startY = graphYToPixel(segment.start.y, top, bottom, graphWindow)
        val endX = graphXToPixel(segment.end.x, left, right, graphWindow)
        val endY = graphYToPixel(segment.end.y, top, bottom, graphWindow)
        val steps = max(abs(endX - startX), abs(endY - startY)).coerceAtLeast(1)
        repeat(steps + 1) { step ->
            val progress = step.toDouble() / steps
            val pixelX = (startX + (endX - startX) * progress).roundToInt()
            val pixelY = (startY + (endY - startY) * progress).roundToInt()
            guiGraphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color)
        }
    }

    private fun drawStatPlotMark(
        guiGraphics: GuiGraphics,
        pixelX: Int,
        pixelY: Int,
        mark: StatPlotMark,
        color: Int
    ) {
        when (mark) {
            StatPlotMark.OPEN_SQUARE -> {
                guiGraphics.fill(pixelX - 2, pixelY - 2, pixelX + 3, pixelY - 1, color)
                guiGraphics.fill(pixelX - 2, pixelY + 2, pixelX + 3, pixelY + 3, color)
                guiGraphics.fill(pixelX - 2, pixelY - 1, pixelX - 1, pixelY + 2, color)
                guiGraphics.fill(pixelX + 2, pixelY - 1, pixelX + 3, pixelY + 2, color)
            }
            StatPlotMark.PLUS -> {
                guiGraphics.fill(pixelX - 2, pixelY, pixelX + 3, pixelY + 1, color)
                guiGraphics.fill(pixelX, pixelY - 2, pixelX + 1, pixelY + 3, color)
            }
            StatPlotMark.DOT ->
                guiGraphics.fill(pixelX - 1, pixelY - 1, pixelX + 2, pixelY + 2, color)
            StatPlotMark.SMALL_DOT ->
                guiGraphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color)
        }
    }

    private fun drawGraphSegment(
        guiGraphics: GuiGraphics,
        start: GraphSample,
        end: GraphSample,
        color: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val pixelDistance = (end.pixelX - start.pixelX).coerceAtLeast(1)
        for (pixelX in start.pixelX..end.pixelX) {
            val progress = (pixelX - start.pixelX).toDouble() / pixelDistance
            val graphY = start.y + (end.y - start.y) * progress
            if (graphY in graphWindow.yMin..graphWindow.yMax) {
                val pixelY = graphYToPixel(graphY, top, bottom, graphWindow)
                guiGraphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color)
            }
        }
    }

    private fun graphXToPixel(value: Double, left: Int, right: Int, graphWindow: GraphWindow): Int =
        (left + (value - graphWindow.xMin) / (graphWindow.xMax - graphWindow.xMin) * (right - left))
            .roundToInt()

    private fun graphYToPixel(value: Double, top: Int, bottom: Int, graphWindow: GraphWindow): Int =
        (bottom - (value - graphWindow.yMin) / (graphWindow.yMax - graphWindow.yMin) * (bottom - top))
            .roundToInt()

    private fun forEachTick(
        minimum: Double,
        maximum: Double,
        spacing: Double,
        action: (Double) -> Unit
    ) {
        val firstTickValue = ceil(minimum / spacing)
        val lastTickValue = floor(maximum / spacing)
        val tickCount = lastTickValue - firstTickValue
        if (!firstTickValue.isFinite() || !lastTickValue.isFinite() ||
            tickCount !in 0.0..1_000.0
        ) return
        for (multiple in firstTickValue.toLong()..lastTickValue.toLong()) {
            action(multiple * spacing)
        }
    }

    private fun tickValues(minimum: Double, maximum: Double, spacing: Double): List<Double> =
        buildList {
            forEachTick(minimum, maximum, spacing, ::add)
        }

    private data class GraphSample(val pixelX: Int, val x: Double, val y: Double)

    companion object {
        private const val TEXTURE_WIDTH = 440
        private const val TEXTURE_HEIGHT = 1024
        private const val DISPLAY_LEFT = 21
        private const val DISPLAY_TOP = 87
        private const val DISPLAY_RIGHT = 418
        private const val DISPLAY_BOTTOM = 343
        private const val DISPLAY_PADDING = 8
        private const val DISPLAY_LINE_HEIGHT = 6
        private const val TRACE_FOOTER_OFFSET = 6
        private const val TRACE_VALUE_MAX_CHARACTERS = 10
        private const val DISPLAY_TEXT_COLOR = 0xFF1F1F1F.toInt()
        private const val GRAPH_AXIS_COLOR = 0xFF555555.toInt()
        private const val GRAPH_GRID_COLOR = 0xFFBBBBBB.toInt()
        private const val MAX_GRID_POINTS = 5_000L
    }
}

/** Shared half-scale text primitive used by the LCD and graph renderers. */
object CalculatorTextRenderer {
    private const val DISPLAY_TEXT_SCALE = 0.5f

    fun draw(
        guiGraphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        color: Int = 0xFF1F1F1F.toInt()
    ) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().scale(DISPLAY_TEXT_SCALE, DISPLAY_TEXT_SCALE, 1f)
        guiGraphics.drawString(
            net.minecraft.client.Minecraft.getInstance().font,
            text,
            (x / DISPLAY_TEXT_SCALE).roundToInt(),
            (y / DISPLAY_TEXT_SCALE).roundToInt(),
            color,
            false
        )
        guiGraphics.pose().popPose()
    }
}
