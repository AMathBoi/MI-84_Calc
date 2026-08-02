package net.amathboi.mi84mod.client.calculator.ui

enum class StatPlotScreen {
    MAIN,
    EDITOR
}

/** Transient selection state for the STAT PLOT main page and nested Plot1/2/3 editor tabs. */
class StatPlotViewState {
    var screen = StatPlotScreen.MAIN
    var selectedMainItem = 0
    var selectedPlotIndex = 0
    var selectedEditorRow = 0
}
