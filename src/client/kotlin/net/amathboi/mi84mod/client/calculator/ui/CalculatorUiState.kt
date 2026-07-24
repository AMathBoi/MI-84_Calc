package net.amathboi.mi84mod.client.calculator.ui

import net.amathboi.mi84mod.client.calculator.ZoomMemory
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer

enum class CalculatorView {
    HOME,
    Y_EQUALS,
    WINDOW,
    MODE,
    ZOOM,
    ZOOM_FACTORS,
    GRAPH
}

enum class ZoomTab {
    ZOOM,
    MEMORY
}

enum class ZoomGraphOperation {
    BOX,
    IN,
    OUT
}

data class ZoomMenuOption(val hotkey: String, val label: String)

data class ZoomGraphState(
    val operation: ZoomGraphOperation,
    var x: Double,
    var y: Double,
    var anchorX: Double? = null,
    var anchorY: Double? = null
)

data class TraceState(var functionIndex: Int, var x: Double)

data class GraphWindow(
    val xMin: Double,
    val xMax: Double,
    val xScale: Double,
    val yMin: Double,
    val yMax: Double,
    val yScale: Double,
    val xResolution: Int
)

/** Transient calculator UI state. Persistent calculator data remains in the memory stores. */
class CalculatorUiState {
    var view = CalculatorView.HOME
    var modifier = ModifierLayer.NORMAL
    var historyNavigationPosition = 0
    var entryRecallPosition = 0
    var insertMode = false
    var trace: TraceState? = null
    var zoomGraph: ZoomGraphState? = null
    var zoomTab = ZoomTab.ZOOM
    var zoomSelectedIndex = 0
    var memorySelectedIndex = 0
    var factorSelectedIndex = ZoomMemory.selectedDenominatorIndex()
}
