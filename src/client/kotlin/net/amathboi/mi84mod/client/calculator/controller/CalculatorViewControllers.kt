package net.amathboi.mi84mod.client.calculator.controller

import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.FormatSettingsMemory
import net.amathboi.mi84mod.client.calculator.ModeSettingsMemory
import net.amathboi.mi84mod.client.calculator.TableSettingsMemory
import net.amathboi.mi84mod.client.calculator.WindowSettingsMemory
import net.amathboi.mi84mod.client.calculator.YEqualsMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.ui.CalculatorUiState

/** Input behavior for the Home editor and its history-selection substate. */
object HomeViewController {
    fun handle(command: CalculatorCommand, state: CalculatorUiState) {
        if (command == CalculatorCommand.RecallEntry) {
            recallPreviousEntry(state)
            return
        }
        state.entryRecallPosition = 0

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
            is CalculatorCommand.Digit ->
                CalculatorDisplayMemory.appendDigit(command.value, state.insertMode)
            is CalculatorCommand.Operator ->
                CalculatorDisplayMemory.appendOperator(command.value, state.insertMode)
            is CalculatorCommand.Function ->
                CalculatorDisplayMemory.appendFunction(command.name, state.insertMode)
            is CalculatorCommand.InsertVariable ->
                CalculatorDisplayMemory.appendVariable(command.variable, state.insertMode)
            CalculatorCommand.Decimal -> CalculatorDisplayMemory.appendDecimalPoint(state.insertMode)
            CalculatorCommand.Negative -> CalculatorDisplayMemory.toggleCurrentNumberSign()
            CalculatorCommand.Square -> CalculatorDisplayMemory.squareCurrentOperand(state.insertMode)
            CalculatorCommand.Reciprocal ->
                CalculatorDisplayMemory.reciprocalCurrentOperand(state.insertMode)
            CalculatorCommand.OpenParenthesis ->
                CalculatorDisplayMemory.appendOpenParenthesis(state.insertMode)
            CalculatorCommand.CloseParenthesis ->
                CalculatorDisplayMemory.appendCloseParenthesis(state.insertMode)
            CalculatorCommand.Comma -> CalculatorDisplayMemory.appendComma(state.insertMode)
            CalculatorCommand.Variable -> CalculatorDisplayMemory.appendXVariable(state.insertMode)
            CalculatorCommand.Store -> CalculatorDisplayMemory.appendStoreOperator(state.insertMode)
            CalculatorCommand.Left -> CalculatorDisplayMemory.moveCursorLeft()
            CalculatorCommand.Right -> CalculatorDisplayMemory.moveCursorRight()
            CalculatorCommand.Up -> moveHistoryUp(state)
            CalculatorCommand.Down -> moveHistoryDown(state)
            CalculatorCommand.Delete -> CalculatorDisplayMemory.deleteAtCursor()
            CalculatorCommand.Clear -> CalculatorDisplayMemory.clearCurrent()
            CalculatorCommand.Enter -> CalculatorDisplayMemory.submit()
            CalculatorCommand.InsertAns -> CalculatorDisplayMemory.appendAns(state.insertMode)
            CalculatorCommand.InsertImaginaryUnit ->
                CalculatorDisplayMemory.appendImaginaryUnit(state.insertMode)
            CalculatorCommand.InsertPi -> CalculatorDisplayMemory.appendPi(state.insertMode)
            CalculatorCommand.InsertEuler -> CalculatorDisplayMemory.appendEuler(state.insertMode)
            CalculatorCommand.InsertInverseSine ->
                CalculatorDisplayMemory.appendInverseSine(state.insertMode)
            CalculatorCommand.InsertInverseCosine ->
                CalculatorDisplayMemory.appendInverseCosine(state.insertMode)
            CalculatorCommand.InsertInverseTangent ->
                CalculatorDisplayMemory.appendInverseTangent(state.insertMode)
            CalculatorCommand.InsertTenPower ->
                CalculatorDisplayMemory.appendTenPower(state.insertMode)
            CalculatorCommand.InsertEulerPower ->
                CalculatorDisplayMemory.appendEulerPower(state.insertMode)
            CalculatorCommand.InsertSquareRoot ->
                CalculatorDisplayMemory.appendSquareRoot(state.insertMode)
            CalculatorCommand.InsertScientificExponent ->
                CalculatorDisplayMemory.appendScientificExponent(state.insertMode)
            else -> Unit
        }
    }

    private fun recallPreviousEntry(state: CalculatorUiState) {
        state.historyNavigationPosition = 0
        val nextPosition = state.entryRecallPosition + 1
        val recalled = CalculatorDisplayMemory.submittedInputFromNewest(nextPosition) ?: return
        CalculatorDisplayMemory.replaceCurrentWithSubmittedInput(recalled)
        state.entryRecallPosition = nextPosition
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
        CalculatorDisplayMemory.appendRecalledHistory(
            CalculatorDisplayMemory.editableTextForHistoryLine(selected)
        )
    }
}

object YEqualsViewController {
    fun handle(command: CalculatorCommand, state: CalculatorUiState) {
        when (command) {
            is CalculatorCommand.Digit ->
                YEqualsMemory.appendDigit(command.value, state.insertMode)
            is CalculatorCommand.Operator ->
                YEqualsMemory.append(command.value.toString(), state.insertMode)
            is CalculatorCommand.Function ->
                YEqualsMemory.append("${command.name}(", state.insertMode)
            is CalculatorCommand.InsertVariable ->
                YEqualsMemory.append(command.variable.symbol.toString(), state.insertMode)
            CalculatorCommand.Decimal -> YEqualsMemory.append(".", state.insertMode)
            CalculatorCommand.Variable -> YEqualsMemory.append("X", state.insertMode)
            CalculatorCommand.Square -> YEqualsMemory.append("^2", state.insertMode)
            CalculatorCommand.Reciprocal -> YEqualsMemory.append("^-1", state.insertMode)
            CalculatorCommand.OpenParenthesis -> YEqualsMemory.append("(", state.insertMode)
            CalculatorCommand.CloseParenthesis -> YEqualsMemory.append(")", state.insertMode)
            CalculatorCommand.Comma -> YEqualsMemory.append(",", state.insertMode)
            CalculatorCommand.Negative -> YEqualsMemory.toggleCurrentOperandSign()
            CalculatorCommand.Left -> YEqualsMemory.moveCursorLeft()
            CalculatorCommand.Right -> YEqualsMemory.moveCursorRight()
            CalculatorCommand.Up -> YEqualsMemory.selectPrevious()
            CalculatorCommand.Down, CalculatorCommand.Enter -> YEqualsMemory.selectNext()
            CalculatorCommand.Delete -> YEqualsMemory.deleteAtCursor()
            CalculatorCommand.Clear -> YEqualsMemory.clearSelected()
            else -> phaseOneScalarText(command)?.let { YEqualsMemory.append(it, state.insertMode) }
        }
    }
}

object WindowViewController {
    fun handle(command: CalculatorCommand, state: CalculatorUiState) {
        when (command) {
            is CalculatorCommand.Digit ->
                WindowSettingsMemory.appendDigit(command.value, state.insertMode)
            is CalculatorCommand.Operator ->
                WindowSettingsMemory.append(command.value.toString(), state.insertMode)
            is CalculatorCommand.InsertVariable ->
                WindowSettingsMemory.append(command.variable.symbol.toString(), state.insertMode)
            CalculatorCommand.Decimal -> WindowSettingsMemory.append(".", state.insertMode)
            CalculatorCommand.OpenParenthesis -> WindowSettingsMemory.append("(", state.insertMode)
            CalculatorCommand.CloseParenthesis -> WindowSettingsMemory.append(")", state.insertMode)
            CalculatorCommand.Comma -> WindowSettingsMemory.append(",", state.insertMode)
            CalculatorCommand.Negative -> WindowSettingsMemory.toggleCurrentOperandSign()
            CalculatorCommand.Left -> WindowSettingsMemory.moveCursorLeft()
            CalculatorCommand.Right -> WindowSettingsMemory.moveCursorRight()
            CalculatorCommand.Up -> WindowSettingsMemory.selectPrevious()
            CalculatorCommand.Down, CalculatorCommand.Enter -> WindowSettingsMemory.selectNext()
            CalculatorCommand.Delete -> WindowSettingsMemory.deleteAtCursor()
            CalculatorCommand.Clear -> WindowSettingsMemory.clearSelected()
            else ->
                phaseOneScalarText(command)?.let { WindowSettingsMemory.append(it, state.insertMode) }
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

object FormatViewController {
    fun handle(command: CalculatorCommand) {
        when (command) {
            CalculatorCommand.Left -> FormatSettingsMemory.selectPreviousOption()
            CalculatorCommand.Right -> FormatSettingsMemory.selectNextOption()
            CalculatorCommand.Up -> FormatSettingsMemory.selectPreviousSetting()
            CalculatorCommand.Down, CalculatorCommand.Enter ->
                FormatSettingsMemory.selectNextSetting()
            else -> Unit
        }
    }
}

object TableSetupViewController {
    fun handle(command: CalculatorCommand, state: CalculatorUiState) {
        when (command) {
            is CalculatorCommand.Digit ->
                TableSettingsMemory.appendDigit(command.value, state.insertMode)
            is CalculatorCommand.Operator ->
                TableSettingsMemory.append(command.value.toString(), state.insertMode)
            is CalculatorCommand.Function ->
                TableSettingsMemory.append("${command.name}(", state.insertMode)
            is CalculatorCommand.InsertVariable ->
                TableSettingsMemory.append(command.variable.symbol.toString(), state.insertMode)
            CalculatorCommand.Decimal -> TableSettingsMemory.append(".", state.insertMode)
            CalculatorCommand.Variable -> TableSettingsMemory.append("X", state.insertMode)
            CalculatorCommand.Square -> TableSettingsMemory.append("^2", state.insertMode)
            CalculatorCommand.Reciprocal -> TableSettingsMemory.append("^-1", state.insertMode)
            CalculatorCommand.OpenParenthesis -> TableSettingsMemory.append("(", state.insertMode)
            CalculatorCommand.CloseParenthesis -> TableSettingsMemory.append(")", state.insertMode)
            CalculatorCommand.Comma -> TableSettingsMemory.append(",", state.insertMode)
            CalculatorCommand.Negative -> TableSettingsMemory.toggleCurrentOperandSign()
            CalculatorCommand.Left -> TableSettingsMemory.moveLeft()
            CalculatorCommand.Right -> TableSettingsMemory.moveRight()
            CalculatorCommand.Up -> TableSettingsMemory.selectPrevious()
            CalculatorCommand.Down, CalculatorCommand.Enter -> TableSettingsMemory.selectNext()
            CalculatorCommand.Delete -> TableSettingsMemory.deleteAtCursor()
            CalculatorCommand.Clear -> TableSettingsMemory.clearSelected()
            else -> phaseOneScalarText(command)?.let {
                TableSettingsMemory.append(it, state.insertMode)
            }
        }
    }
}

private fun phaseOneScalarText(command: CalculatorCommand): String? = when (command) {
    CalculatorCommand.InsertAns -> "Ans"
    CalculatorCommand.InsertImaginaryUnit -> "i"
    CalculatorCommand.InsertPi -> "π"
    CalculatorCommand.InsertEuler -> "e"
    CalculatorCommand.InsertInverseSine -> "sin⁻¹("
    CalculatorCommand.InsertInverseCosine -> "cos⁻¹("
    CalculatorCommand.InsertInverseTangent -> "tan⁻¹("
    CalculatorCommand.InsertTenPower -> "10^("
    CalculatorCommand.InsertEulerPower -> "e^("
    CalculatorCommand.InsertSquareRoot -> "sqrt("
    CalculatorCommand.InsertScientificExponent -> "EE"
    else -> null
}
