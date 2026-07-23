package net.amathboi.mi84mod.client.calculator.controller

import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.ModeSettingsMemory
import net.amathboi.mi84mod.client.calculator.WindowSettingsMemory
import net.amathboi.mi84mod.client.calculator.YEqualsMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.ui.CalculatorUiState

/** Input behavior for the Home editor and its history-selection substate. */
object HomeViewController {
    fun handle(command: CalculatorCommand, state: CalculatorUiState) {
        if (state.historyNavigationPosition > 0) {
            when (command) {
                CalculatorCommand.Up -> moveHistoryUp(state)
                CalculatorCommand.Down -> moveHistoryDown(state)
                CalculatorCommand.Enter -> acceptHistorySelection(state)
                else -> Unit
            }
            return
        }

        when (command) {
            is CalculatorCommand.Digit -> CalculatorDisplayMemory.appendDigit(command.value)
            is CalculatorCommand.Operator -> CalculatorDisplayMemory.appendOperator(command.value)
            is CalculatorCommand.Function -> CalculatorDisplayMemory.appendFunction(command.name)
            CalculatorCommand.Decimal -> CalculatorDisplayMemory.appendDecimalPoint()
            CalculatorCommand.Negative -> CalculatorDisplayMemory.toggleCurrentNumberSign()
            CalculatorCommand.Square -> CalculatorDisplayMemory.squareCurrentOperand()
            CalculatorCommand.Reciprocal -> CalculatorDisplayMemory.reciprocalCurrentOperand()
            CalculatorCommand.OpenParenthesis -> CalculatorDisplayMemory.appendOpenParenthesis()
            CalculatorCommand.CloseParenthesis -> CalculatorDisplayMemory.appendCloseParenthesis()
            CalculatorCommand.Comma -> CalculatorDisplayMemory.appendComma()
            CalculatorCommand.Variable -> CalculatorDisplayMemory.appendXVariable()
            CalculatorCommand.Store -> CalculatorDisplayMemory.appendStoreOperator()
            CalculatorCommand.Left -> CalculatorDisplayMemory.moveCursorLeft()
            CalculatorCommand.Right -> CalculatorDisplayMemory.moveCursorRight()
            CalculatorCommand.Up -> moveHistoryUp(state)
            CalculatorCommand.Down -> moveHistoryDown(state)
            CalculatorCommand.Delete -> CalculatorDisplayMemory.deleteAtCursor()
            CalculatorCommand.Clear -> CalculatorDisplayMemory.clearCurrent()
            CalculatorCommand.Enter -> CalculatorDisplayMemory.submit()
            else -> Unit
        }
    }

    private fun moveHistoryUp(state: CalculatorUiState) {
        val nextPosition = state.historyNavigationPosition + 1
        if (CalculatorDisplayMemory.historyLineFromNewest(nextPosition) != null) {
            state.historyNavigationPosition = nextPosition
        }
    }

    private fun moveHistoryDown(state: CalculatorUiState) {
        state.historyNavigationPosition = (state.historyNavigationPosition - 1).coerceAtLeast(0)
    }

    private fun acceptHistorySelection(state: CalculatorUiState) {
        val selected = CalculatorDisplayMemory.historyLineFromNewest(state.historyNavigationPosition)
        state.historyNavigationPosition = 0
        if (selected == null || selected.text.startsWith("Error:")) return
        CalculatorDisplayMemory.appendRecalledHistory(selected.text)
    }
}

object YEqualsViewController {
    fun handle(command: CalculatorCommand) {
        when (command) {
            is CalculatorCommand.Digit -> YEqualsMemory.append(command.value.toString())
            is CalculatorCommand.Operator -> YEqualsMemory.append(command.value.toString())
            is CalculatorCommand.Function -> YEqualsMemory.append("${command.name}(")
            CalculatorCommand.Decimal -> YEqualsMemory.append(".")
            CalculatorCommand.Variable -> YEqualsMemory.append("X")
            CalculatorCommand.Square -> YEqualsMemory.append("^2")
            CalculatorCommand.Reciprocal -> YEqualsMemory.append("^-1")
            CalculatorCommand.OpenParenthesis -> YEqualsMemory.append("(")
            CalculatorCommand.CloseParenthesis -> YEqualsMemory.append(")")
            CalculatorCommand.Left -> YEqualsMemory.moveCursorLeft()
            CalculatorCommand.Right -> YEqualsMemory.moveCursorRight()
            CalculatorCommand.Up -> YEqualsMemory.selectPrevious()
            CalculatorCommand.Down, CalculatorCommand.Enter -> YEqualsMemory.selectNext()
            CalculatorCommand.Delete -> YEqualsMemory.deleteAtCursor()
            CalculatorCommand.Clear -> YEqualsMemory.clearSelected()
            else -> Unit
        }
    }
}

object WindowViewController {
    fun handle(command: CalculatorCommand) {
        when (command) {
            is CalculatorCommand.Digit -> WindowSettingsMemory.append(command.value.toString())
            is CalculatorCommand.Operator -> WindowSettingsMemory.append(command.value.toString())
            CalculatorCommand.Decimal -> WindowSettingsMemory.append(".")
            CalculatorCommand.OpenParenthesis -> WindowSettingsMemory.append("(")
            CalculatorCommand.CloseParenthesis -> WindowSettingsMemory.append(")")
            CalculatorCommand.Left -> WindowSettingsMemory.moveCursorLeft()
            CalculatorCommand.Right -> WindowSettingsMemory.moveCursorRight()
            CalculatorCommand.Up -> WindowSettingsMemory.selectPrevious()
            CalculatorCommand.Down, CalculatorCommand.Enter -> WindowSettingsMemory.selectNext()
            CalculatorCommand.Delete -> WindowSettingsMemory.deleteAtCursor()
            CalculatorCommand.Clear -> WindowSettingsMemory.clearSelected()
            else -> Unit
        }
    }
}

object ModeViewController {
    fun handle(command: CalculatorCommand) {
        when (command) {
            CalculatorCommand.Left -> ModeSettingsMemory.selectPreviousOption()
            CalculatorCommand.Right -> ModeSettingsMemory.selectNextOption()
            CalculatorCommand.Up -> ModeSettingsMemory.selectPreviousCategory()
            CalculatorCommand.Down, CalculatorCommand.Enter -> ModeSettingsMemory.selectNextCategory()
            else -> Unit
        }
    }
}
