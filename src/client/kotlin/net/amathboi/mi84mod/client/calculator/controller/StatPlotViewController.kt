package net.amathboi.mi84mod.client.calculator.controller

import net.amathboi.mi84mod.client.calculator.StatPlotSettingsMemory
import net.amathboi.mi84mod.client.calculator.StatPlotType
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.ui.StatPlotScreen
import net.amathboi.mi84mod.client.calculator.ui.StatPlotViewState

/** Navigation and mutation for STAT PLOT configuration; rendering support is type-specific. */
object StatPlotViewController {
    const val MAIN_ITEM_COUNT = 5
    const val EDITOR_ROW_COUNT = 6

    fun handle(command: CalculatorCommand, state: StatPlotViewState) {
        when (state.screen) {
            StatPlotScreen.MAIN -> handleMain(command, state)
            StatPlotScreen.EDITOR -> handleEditor(command, state)
        }
    }

    private fun handleMain(command: CalculatorCommand, state: StatPlotViewState) {
        when (command) {
            CalculatorCommand.Up ->
                state.selectedMainItem = (state.selectedMainItem - 1).coerceAtLeast(0)
            CalculatorCommand.Down ->
                state.selectedMainItem = (state.selectedMainItem + 1).coerceAtMost(MAIN_ITEM_COUNT - 1)
            CalculatorCommand.Enter -> activateMainItem(state, state.selectedMainItem)
            is CalculatorCommand.Digit -> {
                val item = when (command.value) {
                    '1' -> 0
                    '2' -> 1
                    '3' -> 2
                    '4' -> 3
                    '5' -> 4
                    else -> return
                }
                state.selectedMainItem = item
                activateMainItem(state, item)
            }
            else -> Unit
        }
    }

    private fun activateMainItem(state: StatPlotViewState, item: Int) {
        when (item) {
            in 0..2 -> {
                state.selectedPlotIndex = item
                state.selectedEditorRow = 0
                state.screen = StatPlotScreen.EDITOR
            }
            3 -> StatPlotSettingsMemory.setAllEnabled(true)
            4 -> StatPlotSettingsMemory.setAllEnabled(false)
        }
    }

    private fun handleEditor(command: CalculatorCommand, state: StatPlotViewState) {
        when (command) {
            CalculatorCommand.Up ->
                state.selectedEditorRow = (state.selectedEditorRow - 1).coerceAtLeast(0)
            CalculatorCommand.Down, CalculatorCommand.Enter ->
                state.selectedEditorRow =
                    (state.selectedEditorRow + 1).coerceAtMost(editorRowCount(state) - 1)
            CalculatorCommand.Left -> changeEditorValue(state, -1)
            CalculatorCommand.Right -> changeEditorValue(state, 1)
            CalculatorCommand.Clear -> {
                state.screen = StatPlotScreen.MAIN
                state.selectedMainItem = state.selectedPlotIndex
            }
            else -> Unit
        }
    }

    private fun changeEditorValue(state: StatPlotViewState, direction: Int) {
        val index = state.selectedPlotIndex
        when (state.selectedEditorRow) {
            0 -> state.selectedPlotIndex =
                (state.selectedPlotIndex + direction).coerceIn(0, StatPlotSettingsMemory.size() - 1)
            1 -> StatPlotSettingsMemory.setEnabled(index, direction > 0)
            2 -> StatPlotSettingsMemory.cycleType(index, direction)
            3 -> StatPlotSettingsMemory.cycleXList(index, direction)
            4 -> if (StatPlotSettingsMemory.plot(index).type == StatPlotType.RELATIVE_FREQUENCY) {
                StatPlotSettingsMemory.cycleDataAxis(index, direction)
            } else {
                StatPlotSettingsMemory.cycleYList(index, direction)
            }
            5 -> StatPlotSettingsMemory.cycleMark(index, direction)
        }
    }

    private fun editorRowCount(state: StatPlotViewState): Int =
        if (StatPlotSettingsMemory.plot(state.selectedPlotIndex).type == StatPlotType.BOX) {
            EDITOR_ROW_COUNT - 1
        } else {
            EDITOR_ROW_COUNT
        }
}
