package net.amathboi.mi84mod.client.calculator.controller

import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.GraphNavigationMath
import net.amathboi.mi84mod.client.calculator.ModeSettingsMemory
import net.amathboi.mi84mod.client.calculator.WindowSettingsMemory
import net.amathboi.mi84mod.client.calculator.YEqualsMemory
import net.amathboi.mi84mod.client.calculator.ZoomMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.input.CalculatorKeyBindings
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorUiState
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.GraphWindow
import net.amathboi.mi84mod.client.calculator.ui.TraceState
import net.amathboi.mi84mod.client.calculator.ui.ZoomGraphOperation
import net.amathboi.mi84mod.client.calculator.ui.ZoomGraphState
import net.amathboi.mi84mod.client.calculator.ui.ZoomMenuOption
import net.amathboi.mi84mod.client.calculator.ui.ZoomTab

/**
 * Owns calculator input routing and transient UI state without depending on Minecraft classes.
 * Rendering reads [state], while calculator mouse clicks enter through [dispatch].
 */
class CalculatorController(
    val state: CalculatorUiState = CalculatorUiState()
) {
    val zoomOptions = listOf(
        ZoomMenuOption("1", "ZBox"),
        ZoomMenuOption("2", "Zoom In"),
        ZoomMenuOption("3", "Zoom Out"),
        ZoomMenuOption("4", "ZDecimal"),
        ZoomMenuOption("5", "ZSquare"),
        ZoomMenuOption("6", "ZStandard"),
        ZoomMenuOption("7", "ZTrig"),
        ZoomMenuOption("8", "ZInteger"),
        ZoomMenuOption("9", "ZoomStat"),
        ZoomMenuOption("0", "ZoomFit"),
        ZoomMenuOption("A", "ZQuadrant1"),
        ZoomMenuOption("B", "ZFrac1/2"),
        ZoomMenuOption("C", "ZFrac1/3"),
        ZoomMenuOption("D", "ZFrac1/4"),
        ZoomMenuOption("E", "ZFrac1/5"),
        ZoomMenuOption("F", "ZFrac1/8"),
        ZoomMenuOption("G", "ZFrac1/10")
    )
    val memoryOptions = listOf(
        ZoomMenuOption("1", "ZPrevious"),
        ZoomMenuOption("2", "ZoomSto"),
        ZoomMenuOption("3", "ZoomRcl"),
        ZoomMenuOption("4", "SetFactors...")
    )

    fun dispatch(
        event: CalculatorInputEvent,
        graphAspect: Double = DEFAULT_GRAPH_ASPECT
    ): DispatchResult {
        if (event.key == CalculatorKey.SECOND) {
            state.modifier = if (state.modifier == ModifierLayer.SECOND) {
                ModifierLayer.NORMAL
            } else {
                ModifierLayer.SECOND
            }
            return DispatchResult.Handled
        }
        if (event.key == CalculatorKey.ALPHA) {
            state.modifier = if (state.modifier == ModifierLayer.ALPHA) {
                ModifierLayer.NORMAL
            } else {
                ModifierLayer.ALPHA
            }
            return DispatchResult.Handled
        }

        if (state.view == CalculatorView.ZOOM && state.modifier == ModifierLayer.ALPHA) {
            val alphaHotkey = alphaHotkeyFor(event.key)
            state.modifier = ModifierLayer.NORMAL
            if (alphaHotkey != null) {
                activateZoomHotkey(alphaHotkey, graphAspect)
                return DispatchResult.Handled
            }
            return DispatchResult.Placeholder(event.key, ModifierLayer.ALPHA)
        }

        val activeLayer = state.modifier
        val command = CalculatorKeyBindings.resolve(event.key, activeLayer)
        if (activeLayer != ModifierLayer.NORMAL) state.modifier = ModifierLayer.NORMAL
        when (command) {
            is CalculatorCommand.Placeholder ->
                return DispatchResult.Placeholder(command.key, command.layer)
            is CalculatorCommand.Unsupported -> return DispatchResult.Unsupported(command.key)
            else -> Unit
        }

        when (command) {
            is CalculatorCommand.OpenView -> {
                switchView(command.view)
                return DispatchResult.Handled
            }
            CalculatorCommand.QuitToHome -> {
                switchView(CalculatorView.HOME)
                return DispatchResult.Handled
            }
            CalculatorCommand.BeginTrace -> {
                beginTrace()
                return DispatchResult.Handled
            }
            else -> Unit
        }

        when (state.view) {
            CalculatorView.HOME -> HomeViewController.handle(command, state)
            CalculatorView.Y_EQUALS -> YEqualsViewController.handle(command)
            CalculatorView.WINDOW -> WindowViewController.handle(command)
            CalculatorView.MODE -> ModeViewController.handle(command)
            CalculatorView.ZOOM -> handleZoom(command, graphAspect)
            CalculatorView.ZOOM_FACTORS -> handleZoomFactors(command)
            CalculatorView.GRAPH -> handleGraph(command, graphAspect)
        }
        return DispatchResult.Handled
    }

    fun currentZoomOptions(): List<ZoomMenuOption> =
        if (state.zoomTab == ZoomTab.ZOOM) zoomOptions else memoryOptions

    fun currentZoomSelectedIndex(): Int =
        if (state.zoomTab == ZoomTab.ZOOM) state.zoomSelectedIndex else state.memorySelectedIndex

    fun readGraphWindow(): GraphWindow? {
        val values = (0..6).map { index ->
            CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(index), 0.0)
                ?: return null
        }
        val xMin = values[0]
        val xMax = values[1]
        val xScale = values[2]
        val yMin = values[3]
        val yMax = values[4]
        val yScale = values[5]
        val xResolutionDouble = values[6]
        if (!WindowSettingsMemory.supportsGraphBounds(xMin, xMax, yMin, yMax) ||
            xScale <= 0.0 || yScale <= 0.0 || xResolutionDouble <= 0.0
        ) {
            return null
        }
        return GraphWindow(
            xMin,
            xMax,
            xScale,
            yMin,
            yMax,
            yScale,
            xResolutionDouble.roundToInt().coerceAtLeast(1)
        )
    }

    private fun handleZoom(command: CalculatorCommand, graphAspect: Double) {
        when (command) {
            CalculatorCommand.Left -> state.zoomTab = ZoomTab.ZOOM
            CalculatorCommand.Right -> state.zoomTab = ZoomTab.MEMORY
            CalculatorCommand.Up -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() - 1)
            CalculatorCommand.Down -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() + 1)
            CalculatorCommand.Enter -> activateCurrentZoomOption(graphAspect)
            is CalculatorCommand.Digit -> activateZoomHotkey(command.value.toString(), graphAspect)
            else -> Unit
        }
    }

    private fun handleZoomFactors(command: CalculatorCommand) {
        when (command) {
            CalculatorCommand.Up ->
                state.factorSelectedIndex = (state.factorSelectedIndex - 1).coerceAtLeast(0)
            CalculatorCommand.Down ->
                state.factorSelectedIndex =
                    (state.factorSelectedIndex + 1).coerceAtMost(ZoomMemory.denominators().lastIndex)
            CalculatorCommand.Enter -> selectCurrentFactor()
            is CalculatorCommand.Digit -> {
                val index = command.value.digitToInt() - 1
                if (index in ZoomMemory.denominators().indices) {
                    state.factorSelectedIndex = index
                    selectCurrentFactor()
                }
            }
            else -> Unit
        }
    }

    private fun handleGraph(command: CalculatorCommand, graphAspect: Double) {
        state.zoomGraph?.let { zoom ->
            when (command) {
                CalculatorCommand.Left -> moveZoomGraphCursor(zoom, -1, 0)
                CalculatorCommand.Right -> moveZoomGraphCursor(zoom, 1, 0)
                CalculatorCommand.Up -> moveZoomGraphCursor(zoom, 0, 1)
                CalculatorCommand.Down -> moveZoomGraphCursor(zoom, 0, -1)
                CalculatorCommand.Enter -> acceptZoomGraphSelection(zoom, graphAspect)
                else -> Unit
            }
            return
        }

        val trace = state.trace ?: return
        when (command) {
            CalculatorCommand.Left -> moveTrace(trace, -1)
            CalculatorCommand.Right -> moveTrace(trace, 1)
            CalculatorCommand.Up -> moveTraceFunction(trace, -1)
            CalculatorCommand.Down -> moveTraceFunction(trace, 1)
            else -> Unit
        }
    }

    private fun switchView(view: CalculatorView) {
        state.view = view
        state.trace = null
        state.zoomGraph = null
        state.modifier = ModifierLayer.NORMAL
        state.historyNavigationPosition = 0
    }

    private fun beginTrace() {
        val graphWindow = readGraphWindow()
        state.trace = graphWindow?.let { TraceState(0, (it.xMin + it.xMax) / 2.0) }
        state.view = CalculatorView.GRAPH
        state.zoomGraph = null
        state.modifier = ModifierLayer.NORMAL
        state.historyNavigationPosition = 0
    }

    private fun setCurrentZoomSelectedIndex(index: Int) {
        if (state.zoomTab == ZoomTab.ZOOM) {
            state.zoomSelectedIndex = index.coerceIn(zoomOptions.indices)
        } else {
            state.memorySelectedIndex = index.coerceIn(memoryOptions.indices)
        }
    }

    private fun activateZoomHotkey(hotkey: String, graphAspect: Double) {
        val optionIndex = currentZoomOptions().indexOfFirst { it.hotkey == hotkey }
        if (optionIndex < 0) return
        setCurrentZoomSelectedIndex(optionIndex)
        activateCurrentZoomOption(graphAspect)
    }

    private fun activateCurrentZoomOption(graphAspect: Double) {
        if (state.zoomTab == ZoomTab.ZOOM) {
            activateZoomOption(state.zoomSelectedIndex, graphAspect)
        } else {
            activateMemoryOption(state.memorySelectedIndex)
        }
    }

    private fun activateZoomOption(index: Int, graphAspect: Double) {
        when (index) {
            0 -> beginZoomGraphOperation(ZoomGraphOperation.BOX)
            1 -> beginZoomGraphOperation(ZoomGraphOperation.IN)
            2 -> beginZoomGraphOperation(ZoomGraphOperation.OUT)
            3 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-4.7", "4.7", "1", "-3.1", "3.1", "1")
            }
            4 -> applySquareWindow(graphAspect)
            5 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-10", "10", "1", "-10", "10", "1")
            }
            6 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow(
                    "-6.28318530718",
                    "6.28318530718",
                    "1.57079632679",
                    "-4",
                    "4",
                    "1"
                )
            }
            7 -> applyIntegerWindow()
            8, 9 -> applyZoomFit()
            10 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("0", "10", "1", "0", "10", "1")
            }
            in 11..16 -> {
                val denominator = listOf(2, 3, 4, 5, 8, 10)[index - 11]
                applyFractionalWindow(denominator)
            }
        }
    }

    private fun activateMemoryOption(index: Int) {
        when (index) {
            0 -> if (ZoomMemory.restorePrevious()) openGraph()
            1 -> {
                ZoomMemory.storeCurrent()
                state.view = CalculatorView.GRAPH
            }
            2 -> if (ZoomMemory.recallStored()) openGraph()
            3 -> {
                state.factorSelectedIndex = ZoomMemory.selectedDenominatorIndex()
                state.view = CalculatorView.ZOOM_FACTORS
            }
        }
    }

    private fun selectCurrentFactor() {
        ZoomMemory.selectDenominator(state.factorSelectedIndex)
        state.view = CalculatorView.ZOOM
        state.zoomTab = ZoomTab.MEMORY
    }

    private fun applyImmediateZoom(change: () -> Boolean) {
        val previousWindow = WindowSettingsMemory.snapshot()
        if (!change()) return
        ZoomMemory.rememberPrevious(previousWindow)
        state.trace = null
        state.zoomGraph = null
        state.view = CalculatorView.GRAPH
    }

    private fun applySquareWindow(graphAspect: Double) {
        val graphWindow = readGraphWindow() ?: return
        val xCenter = (graphWindow.xMin + graphWindow.xMax) / 2.0
        val yCenter = (graphWindow.yMin + graphWindow.yMax) / 2.0
        val xSpan = graphWindow.xMax - graphWindow.xMin
        val ySpan = graphWindow.yMax - graphWindow.yMin
        val squareXSpan = max(xSpan, ySpan * graphAspect)
        val squareYSpan = max(ySpan, xSpan / graphAspect)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(xCenter - squareXSpan / 2.0),
                formatWindowValue(xCenter + squareXSpan / 2.0),
                WindowSettingsMemory.value(2),
                formatWindowValue(yCenter - squareYSpan / 2.0),
                formatWindowValue(yCenter + squareYSpan / 2.0),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyIntegerWindow() {
        val graphWindow = readGraphWindow() ?: return
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                GraphNavigationMath.integerBound(graphWindow.xMin, RoundingMode.FLOOR),
                GraphNavigationMath.integerBound(graphWindow.xMax, RoundingMode.CEILING),
                "1",
                GraphNavigationMath.integerBound(graphWindow.yMin, RoundingMode.FLOOR),
                GraphNavigationMath.integerBound(graphWindow.yMax, RoundingMode.CEILING),
                "1",
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyFractionalWindow(denominator: Int) {
        val graphWindow = readGraphWindow() ?: return
        val spacing = formatWindowValue(1.0 / denominator)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(graphWindow.xMin),
                formatWindowValue(graphWindow.xMax),
                spacing,
                formatWindowValue(graphWindow.yMin),
                formatWindowValue(graphWindow.yMax),
                spacing,
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyZoomFit() {
        val graphWindow = readGraphWindow() ?: return
        val expressions = YEqualsMemory.subscripts.indices
            .map(YEqualsMemory::equation)
            .filter(String::isNotEmpty)
        if (expressions.isEmpty()) return

        val values = mutableListOf<Double>()
        repeat(101) { sampleIndex ->
            val graphX =
                graphWindow.xMin + sampleIndex / 100.0 * (graphWindow.xMax - graphWindow.xMin)
            expressions.forEach { expression ->
                CalculatorDisplayMemory.evaluateForGraph(expression, graphX)
                    ?.takeIf(Double::isFinite)
                    ?.let(values::add)
            }
        }
        if (values.isEmpty()) return
        var yMin = values.minOrNull() ?: return
        var yMax = values.maxOrNull() ?: return
        val padding = if (yMin == yMax) max(1.0, abs(yMin) * 0.1) else (yMax - yMin) * 0.05
        yMin -= padding
        yMax += padding
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                WindowSettingsMemory.value(0),
                WindowSettingsMemory.value(1),
                WindowSettingsMemory.value(2),
                formatWindowValue(yMin),
                formatWindowValue(yMax),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun beginZoomGraphOperation(operation: ZoomGraphOperation) {
        val graphWindow = readGraphWindow() ?: return
        state.zoomGraph = ZoomGraphState(
            operation,
            (graphWindow.xMin + graphWindow.xMax) / 2.0,
            (graphWindow.yMin + graphWindow.yMax) / 2.0
        )
        state.trace = null
        state.view = CalculatorView.GRAPH
    }

    private fun moveTraceFunction(trace: TraceState, direction: Int) {
        var candidate = trace.functionIndex + direction
        while (candidate in YEqualsMemory.subscripts.indices) {
            if (YEqualsMemory.equation(candidate).isNotEmpty()) {
                trace.functionIndex = candidate
                return
            }
            candidate += direction
        }
    }

    private fun moveTrace(trace: TraceState, direction: Int) {
        val graphWindow = readGraphWindow() ?: return
        val step = CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(8), 0.0)
        if (step != null && step.isFinite() && step > 0.0) {
            trace.x = GraphNavigationMath.clampedTraceX(
                trace.x,
                direction,
                step,
                graphWindow.xMin,
                graphWindow.xMax
            )
        }
    }

    private fun moveZoomGraphCursor(zoom: ZoomGraphState, xDirection: Int, yDirection: Int) {
        val graphWindow = readGraphWindow() ?: return
        val xStep = (graphWindow.xMax - graphWindow.xMin) / 40.0
        val yStep = (graphWindow.yMax - graphWindow.yMin) / 26.0
        zoom.x = (zoom.x + xDirection * xStep).coerceIn(graphWindow.xMin, graphWindow.xMax)
        zoom.y = (zoom.y + yDirection * yStep).coerceIn(graphWindow.yMin, graphWindow.yMax)
    }

    private fun acceptZoomGraphSelection(zoom: ZoomGraphState, graphAspect: Double) {
        val graphWindow = readGraphWindow() ?: return
        if (zoom.operation == ZoomGraphOperation.BOX && zoom.anchorX == null) {
            zoom.anchorX = zoom.x
            zoom.anchorY = zoom.y
            return
        }
        val newBounds = when (zoom.operation) {
            ZoomGraphOperation.BOX -> {
                val anchorX = zoom.anchorX ?: return
                val anchorY = zoom.anchorY ?: return
                val xMin = min(anchorX, zoom.x)
                val xMax = max(anchorX, zoom.x)
                val yMin = min(anchorY, zoom.y)
                val yMax = max(anchorY, zoom.y)
                if (xMin == xMax || yMin == yMax) return
                listOf(xMin, xMax, yMin, yMax)
            }
            ZoomGraphOperation.IN, ZoomGraphOperation.OUT -> {
                val factor = ZoomMemory.selectedDenominator().toDouble()
                val directionFactor =
                    if (zoom.operation == ZoomGraphOperation.IN) 1.0 / factor else factor
                val halfWidth = (graphWindow.xMax - graphWindow.xMin) * directionFactor / 2.0
                val halfHeight = (graphWindow.yMax - graphWindow.yMin) * directionFactor / 2.0
                listOf(
                    zoom.x - halfWidth,
                    zoom.x + halfWidth,
                    zoom.y - halfHeight,
                    zoom.y + halfHeight
                )
            }
        }
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(newBounds[0]),
                formatWindowValue(newBounds[1]),
                WindowSettingsMemory.value(2),
                formatWindowValue(newBounds[2]),
                formatWindowValue(newBounds[3]),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun openGraph() {
        state.view = CalculatorView.GRAPH
        state.zoomGraph = null
    }

    private fun alphaHotkeyFor(key: CalculatorKey): String? = when (key) {
        CalculatorKey.MATH -> "A"
        CalculatorKey.APPS -> "B"
        CalculatorKey.PROGRAM -> "C"
        CalculatorKey.RECIPROCAL -> "D"
        CalculatorKey.SIN -> "E"
        CalculatorKey.COS -> "F"
        CalculatorKey.TAN -> "G"
        else -> null
    }

    private fun formatWindowValue(value: Double): String =
        java.math.BigDecimal.valueOf(value)
            .round(java.math.MathContext(12))
            .stripTrailingZeros()
            .toPlainString()

    companion object {
        private const val DEFAULT_GRAPH_ASPECT = 397.0 / 240.0
    }
}
