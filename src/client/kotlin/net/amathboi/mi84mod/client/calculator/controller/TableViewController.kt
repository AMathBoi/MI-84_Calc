package net.amathboi.mi84mod.client.calculator.controller

import java.math.BigDecimal
import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.ExpressionEditingTokens
import net.amathboi.mi84mod.client.calculator.ModeSettingsMemory
import net.amathboi.mi84mod.client.calculator.TableEntryMode
import net.amathboi.mi84mod.client.calculator.TableSettingsMemory
import net.amathboi.mi84mod.client.calculator.YEqualsMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.ui.TableViewState

/** Input and graph-backed cell values for the approved TABLE view. */
object TableViewController {
    const val VISIBLE_COLUMNS = 3
    const val VISIBLE_ROWS = 6
    private const val MAX_ENTRY_LENGTH = 31
    private const val MAX_AUTO_ROW_INDEX = 9_999

    fun open(state: TableViewState) {
        // Asked dependent cells describe a specific visit and may depend on settings, variables,
        // Ans, or Y= expressions changed in another view.
        state.requestedDependentCells.clear()
        normalizeSelection(state)
    }

    fun handle(command: CalculatorCommand, state: TableViewState, insertMode: Boolean = false) {
        normalizeSelection(state)
        if (editingHeader(state) && !state.headerEntryLocked &&
            command !in setOf(
                CalculatorCommand.Left,
                CalculatorCommand.Right,
                CalculatorCommand.Up,
                CalculatorCommand.Down,
                CalculatorCommand.Enter,
                CalculatorCommand.Clear
            )
        ) return
        if (editingHeader(state) && state.headerEntryLocked) {
            handleLockedHeaderEntry(command, state, insertMode)
            return
        }

        when (command) {
            CalculatorCommand.Left -> moveColumn(state, -1)
            CalculatorCommand.Right -> moveColumn(state, 1)
            CalculatorCommand.Up -> moveRow(state, -1)
            CalculatorCommand.Down -> moveRow(state, 1)
            CalculatorCommand.Enter -> activateSelectedCell(state)
            CalculatorCommand.Clear -> {
                if (editingHeader(state)) state.headerEntryLocked = true
                setEntry(state, "")
            }
            is CalculatorCommand.Digit -> appendAskedX(state, command.value.toString())
            is CalculatorCommand.Operator -> appendAskedX(state, command.value.toString())
            is CalculatorCommand.Function -> appendAskedX(state, "${command.name}(")
            is CalculatorCommand.InsertVariable ->
                appendAskedX(state, command.variable.symbol.toString())
            CalculatorCommand.Decimal -> appendAskedX(state, ".")
            CalculatorCommand.OpenParenthesis -> appendAskedX(state, "(")
            CalculatorCommand.CloseParenthesis -> appendAskedX(state, ")")
            CalculatorCommand.Comma -> appendAskedX(state, ",")
            CalculatorCommand.Variable -> appendAskedX(state, "X")
            CalculatorCommand.Square -> appendAskedX(state, "^2")
            CalculatorCommand.Reciprocal -> appendAskedX(state, "^-1")
            CalculatorCommand.InsertAns -> appendAskedX(state, "Ans")
            CalculatorCommand.InsertPi -> appendAskedX(state, "π")
            CalculatorCommand.InsertEuler -> appendAskedX(state, "e")
            CalculatorCommand.InsertInverseSine -> appendAskedX(state, "sin⁻¹(")
            CalculatorCommand.InsertInverseCosine -> appendAskedX(state, "cos⁻¹(")
            CalculatorCommand.InsertInverseTangent -> appendAskedX(state, "tan⁻¹(")
            CalculatorCommand.InsertTenPower -> appendAskedX(state, "10^(")
            CalculatorCommand.InsertEulerPower -> appendAskedX(state, "e^(")
            CalculatorCommand.InsertSquareRoot -> appendAskedX(state, "sqrt(")
            CalculatorCommand.InsertScientificExponent -> appendAskedX(state, "EE")
            CalculatorCommand.Negative -> if (editingAskedX(state)) {
                state.entry = if (state.entry.startsWith('-')) state.entry.drop(1) else "-${state.entry}"
                state.entryCursor = state.entry.length
            }
            CalculatorCommand.Delete -> if (editingAskedX(state) && state.entry.isNotEmpty()) {
                setEntry(state, state.entry.dropLast(1))
            }
            else -> Unit
        }
    }

    /** Packed columns: null is X, non-null values are the non-empty Y= indices. */
    fun columns(): List<Int?> = listOf(null) + enabledFunctionIndices()

    /** X is fixed in slot zero; the remaining visible slots scroll through packed Y columns. */
    fun columnIndexAtVisiblePosition(state: TableViewState, visiblePosition: Int): Int? {
        if (visiblePosition !in 0 until VISIBLE_COLUMNS) return null
        val columnIndex = if (visiblePosition == 0) {
            0
        } else {
            state.firstVisibleColumnIndex + visiblePosition - 1
        }
        return columnIndex.takeIf { it in columns().indices }
    }

    fun enabledFunctionIndices(): List<Int> =
        YEqualsMemory.subscripts.indices.filter { YEqualsMemory.equation(it).isNotEmpty() }

    fun hasEnabledFunctions(): Boolean = enabledFunctionIndices().isNotEmpty()

    fun selectedFunctionIndex(state: TableViewState): Int? =
        columns().getOrNull(state.selectedColumnIndex)

    fun editingHeader(state: TableViewState): Boolean = state.headerSelected

    fun editingAskedX(state: TableViewState): Boolean =
        !editingHeader(state) &&
            state.selectedColumnIndex == 0 &&
            TableSettingsMemory.independentMode() == TableEntryMode.ASK &&
            hasEnabledFunctions()

    fun xValueAt(state: TableViewState, row: Int): BigDecimal? {
        if (!hasEnabledFunctions()) return null
        return if (TableSettingsMemory.independentMode() == TableEntryMode.ASK) {
            if (row < 0) return null
            state.askedXValues.getOrNull(row)
        } else {
            val start = CalculatorDisplayMemory.evaluateRealForListEntry(TableSettingsMemory.tblStart())
                ?: return null
            val delta = CalculatorDisplayMemory.evaluateRealForListEntry(TableSettingsMemory.deltaTbl())
                ?: return null
            start.add(delta.multiply(BigDecimal.valueOf(row.toLong())))
        }
    }

    fun xCellText(state: TableViewState, row: Int): String {
        val value = xValueAt(state, row)
        if (value != null) return ModeSettingsMemory.formatNumber(value)
        return if (TableSettingsMemory.independentMode() == TableEntryMode.ASK &&
            row == state.askedXValues.size
        ) "_" else ""
    }

    fun yValueAt(state: TableViewState, functionIndex: Int, row: Int): Double? {
        val x = xValueAt(state, row)?.toDouble() ?: return null
        if (!x.isFinite()) return null
        return CalculatorDisplayMemory.evaluateForGraph(YEqualsMemory.equation(functionIndex), x)
    }

    fun yCellText(state: TableViewState, functionIndex: Int, row: Int): String {
        if (xValueAt(state, row) == null) return ""
        if (TableSettingsMemory.dependentMode() == TableEntryMode.ASK &&
            functionIndex to row !in state.requestedDependentCells
        ) return ""
        return yValueAt(state, functionIndex, row)?.let(ModeSettingsMemory::formatNumber) ?: "ERR"
    }

    fun bottomPrefix(state: TableViewState): String =
        selectedFunctionIndex(state)?.let { "Y${YEqualsMemory.subscripts[it]}=" } ?: "X="

    fun bottomValue(state: TableViewState): String {
        if (editingHeader(state) || editingAskedX(state)) return state.entry
        val functionIndex = selectedFunctionIndex(state)
        return if (functionIndex == null) {
            xValueAt(state, state.selectedRowIndex)?.let(ModeSettingsMemory::formatNumber).orEmpty()
        } else {
            yCellText(state, functionIndex, state.selectedRowIndex)
        }
    }

    private fun moveColumn(state: TableViewState, delta: Int) {
        val availableColumns = columns()
        val minimum = if (editingHeader(state)) 1 else 0
        if (availableColumns.lastIndex < minimum) return
        state.selectedColumnIndex =
            (state.selectedColumnIndex + delta).coerceIn(minimum, availableColumns.lastIndex)
        val visibleYColumns = VISIBLE_COLUMNS - 1
        val maximumFirstVisible = (availableColumns.size - visibleYColumns).coerceAtLeast(1)
        state.firstVisibleColumnIndex = when {
            state.selectedColumnIndex == 0 -> state.firstVisibleColumnIndex
            state.selectedColumnIndex < state.firstVisibleColumnIndex -> state.selectedColumnIndex
            state.selectedColumnIndex >= state.firstVisibleColumnIndex + visibleYColumns ->
                state.selectedColumnIndex - visibleYColumns + 1
            else -> state.firstVisibleColumnIndex
        }.coerceIn(1, maximumFirstVisible)
        if (editingHeader(state)) loadHeaderExpression(state)
        clampAskedRowForSelectedColumn(state)
    }

    private fun moveRow(state: TableViewState, delta: Int) {
        if (!hasEnabledFunctions()) return
        if (editingHeader(state)) {
            if (delta > 0) {
                state.headerSelected = false
                state.headerEntryLocked = false
                setEntry(state, "")
            }
            return
        }
        if (state.selectedRowIndex == 0 && delta < 0) {
            if (state.selectedColumnIndex > 0) {
                state.headerSelected = true
                loadHeaderExpression(state)
                return
            }
        }
        val maximumRow = if (TableSettingsMemory.independentMode() == TableEntryMode.ASK) {
            if (state.selectedColumnIndex == 0) state.askedXValues.size
            else (state.askedXValues.lastIndex).coerceAtLeast(0)
        } else {
            MAX_AUTO_ROW_INDEX
        }
        val minimumRow = if (TableSettingsMemory.independentMode() == TableEntryMode.AUTO) {
            -MAX_AUTO_ROW_INDEX
        } else {
            0
        }
        state.selectedRowIndex =
            (state.selectedRowIndex + delta).coerceIn(minimumRow, maximumRow)
        state.firstVisibleRowIndex = when {
            state.selectedRowIndex < state.firstVisibleRowIndex -> state.selectedRowIndex
            state.selectedRowIndex >= state.firstVisibleRowIndex + VISIBLE_ROWS ->
                state.selectedRowIndex - VISIBLE_ROWS + 1
            else -> state.firstVisibleRowIndex
        }
    }

    private fun activateSelectedCell(state: TableViewState) {
        when {
            editingHeader(state) -> {
                state.headerEntryLocked = true
                state.entryCursor = state.entry.length
            }
            editingAskedX(state) -> commitAskedX(state)
            selectedFunctionIndex(state) != null &&
                TableSettingsMemory.dependentMode() == TableEntryMode.ASK -> {
                val functionIndex = selectedFunctionIndex(state) ?: return
                if (xValueAt(state, state.selectedRowIndex) != null) {
                    state.requestedDependentCells += functionIndex to state.selectedRowIndex
                }
            }
        }
    }

    private fun commitAskedX(state: TableViewState) {
        val value = CalculatorDisplayMemory.evaluateRealForListEntry(state.entry) ?: return
        while (state.askedXValues.size <= state.selectedRowIndex) {
            state.askedXValues += BigDecimal.ZERO
        }
        state.askedXValues[state.selectedRowIndex] = value
        state.requestedDependentCells.removeAll { (_, row) -> row == state.selectedRowIndex }
        setEntry(state, "")
        moveRow(state, 1)
    }

    private fun appendAskedX(state: TableViewState, text: String) {
        if (!editingAskedX(state) || state.entry.length + text.length > MAX_ENTRY_LENGTH) return
        setEntry(state, state.entry + text)
    }

    private fun handleLockedHeaderEntry(
        command: CalculatorCommand,
        state: TableViewState,
        insertMode: Boolean
    ) {
        when (command) {
            CalculatorCommand.Left -> moveHeaderCursorLeft(state)
            CalculatorCommand.Right -> moveHeaderCursorRight(state)
            CalculatorCommand.Delete -> deleteHeaderToken(state)
            CalculatorCommand.Clear -> setEntry(state, "")
            CalculatorCommand.Enter -> commitHeaderExpression(state)
            is CalculatorCommand.Digit -> editHeaderAtCursor(
                state,
                ExpressionEditingTokens.digitEntryText(state.entry, state.entryCursor, command.value),
                insertMode
            )
            is CalculatorCommand.Operator -> editHeaderAtCursor(state, command.value.toString(), insertMode)
            is CalculatorCommand.Function -> editHeaderAtCursor(state, "${command.name}(", insertMode)
            is CalculatorCommand.InsertVariable ->
                editHeaderAtCursor(state, command.variable.symbol.toString(), insertMode)
            CalculatorCommand.Decimal -> editHeaderAtCursor(state, ".", insertMode)
            CalculatorCommand.OpenParenthesis -> editHeaderAtCursor(state, "(", insertMode)
            CalculatorCommand.CloseParenthesis -> editHeaderAtCursor(state, ")", insertMode)
            CalculatorCommand.Comma -> editHeaderAtCursor(state, ",", insertMode)
            CalculatorCommand.Variable -> editHeaderAtCursor(state, "X", insertMode)
            CalculatorCommand.Square -> editHeaderAtCursor(state, "^2", insertMode)
            CalculatorCommand.Reciprocal -> editHeaderAtCursor(state, "^-1", insertMode)
            CalculatorCommand.InsertAns -> editHeaderAtCursor(state, "Ans", insertMode)
            CalculatorCommand.InsertPi -> editHeaderAtCursor(state, "π", insertMode)
            CalculatorCommand.InsertEuler -> editHeaderAtCursor(state, "e", insertMode)
            CalculatorCommand.InsertInverseSine -> editHeaderAtCursor(state, "sin⁻¹(", insertMode)
            CalculatorCommand.InsertInverseCosine -> editHeaderAtCursor(state, "cos⁻¹(", insertMode)
            CalculatorCommand.InsertInverseTangent -> editHeaderAtCursor(state, "tan⁻¹(", insertMode)
            CalculatorCommand.InsertTenPower -> editHeaderAtCursor(state, "10^(", insertMode)
            CalculatorCommand.InsertEulerPower -> editHeaderAtCursor(state, "e^(", insertMode)
            CalculatorCommand.InsertSquareRoot -> editHeaderAtCursor(state, "sqrt(", insertMode)
            CalculatorCommand.InsertScientificExponent -> editHeaderAtCursor(state, "EE", insertMode)
            CalculatorCommand.Negative -> editHeaderAtCursor(state, "-", insertMode)
            else -> Unit
        }
    }

    private fun commitHeaderExpression(state: TableViewState) {
        val functionIndex = selectedFunctionIndex(state) ?: return
        YEqualsMemory.setEquation(functionIndex, state.entry)
        state.requestedDependentCells.removeAll { (index, _) -> index == functionIndex }
        state.headerEntryLocked = false
        normalizeSelection(state)
        if (state.selectedColumnIndex == 0) {
            state.headerSelected = false
            setEntry(state, "")
        } else {
            loadHeaderExpression(state)
        }
    }

    private fun editHeaderAtCursor(
        state: TableViewState,
        text: String,
        insertMode: Boolean
    ) {
        val replaced = if (!insertMode && state.entryCursor < state.entry.length) {
            ExpressionEditingTokens.structuredFractionStartingAt(state.entry, state.entryCursor)?.length
                ?: ExpressionEditingTokens.namedVariableStartingAt(state.entry, state.entryCursor)?.length
                ?: 1
        } else {
            0
        }
        if (state.entry.length - replaced + text.length > MAX_ENTRY_LENGTH) return
        state.entry = state.entry.removeRange(state.entryCursor, state.entryCursor + replaced)
            .let { it.substring(0, state.entryCursor) + text + it.substring(state.entryCursor) }
        state.entryCursor += text.length
    }

    private fun moveHeaderCursorLeft(state: TableViewState) {
        val cursor = state.entryCursor
        if (cursor <= 0) return
        state.entryCursor =
            ExpressionEditingTokens.structuredFractionEndingAt(state.entry, cursor)
                ?.let { cursor - it.length }
                ?: ExpressionEditingTokens.namedVariableEndingAt(state.entry, cursor)
                    ?.let { cursor - it.length }
                ?: cursor - 1
    }

    private fun moveHeaderCursorRight(state: TableViewState) {
        val cursor = state.entryCursor
        if (cursor >= state.entry.length) return
        state.entryCursor =
            ExpressionEditingTokens.structuredFractionStartingAt(state.entry, cursor)
                ?.let { cursor + it.length }
                ?: ExpressionEditingTokens.namedVariableStartingAt(state.entry, cursor)
                    ?.let { cursor + it.length }
                ?: cursor + 1
    }

    private fun deleteHeaderToken(state: TableViewState) {
        if (state.entryCursor >= state.entry.length) return
        val length =
            ExpressionEditingTokens.structuredFractionStartingAt(state.entry, state.entryCursor)?.length
                ?: ExpressionEditingTokens.namedVariableStartingAt(state.entry, state.entryCursor)?.length
                ?: 1
        state.entry = state.entry.removeRange(state.entryCursor, state.entryCursor + length)
    }

    private fun loadHeaderExpression(state: TableViewState) {
        val functionIndex = selectedFunctionIndex(state) ?: return
        state.headerEntryLocked = false
        setEntry(state, YEqualsMemory.equation(functionIndex))
    }

    private fun clampAskedRowForSelectedColumn(state: TableViewState) {
        if (TableSettingsMemory.independentMode() != TableEntryMode.ASK ||
            state.selectedColumnIndex == 0 || editingHeader(state)
        ) return
        state.selectedRowIndex = state.selectedRowIndex.coerceAtMost(
            state.askedXValues.lastIndex.coerceAtLeast(0)
        )
    }

    private fun normalizeSelection(state: TableViewState) {
        val availableColumns = columns()
        state.selectedColumnIndex = state.selectedColumnIndex.coerceIn(0, availableColumns.lastIndex)
        if (editingHeader(state) && state.selectedColumnIndex == 0) state.headerSelected = false
        if (!hasEnabledFunctions()) {
            state.headerSelected = false
            state.selectedRowIndex = 0
            state.firstVisibleRowIndex = 0
        } else if (!editingHeader(state) &&
            TableSettingsMemory.independentMode() == TableEntryMode.ASK
        ) {
            val maximumRow = if (state.selectedColumnIndex == 0) {
                state.askedXValues.size
            } else {
                state.askedXValues.lastIndex.coerceAtLeast(0)
            }
            state.selectedRowIndex = state.selectedRowIndex.coerceIn(0, maximumRow)
            state.firstVisibleRowIndex = when {
                state.selectedRowIndex < state.firstVisibleRowIndex -> state.selectedRowIndex
                state.selectedRowIndex >= state.firstVisibleRowIndex + VISIBLE_ROWS ->
                    state.selectedRowIndex - VISIBLE_ROWS + 1
                else -> state.firstVisibleRowIndex
            }
        }
        val visibleYColumns = VISIBLE_COLUMNS - 1
        val maximumFirstVisible = (availableColumns.size - visibleYColumns).coerceAtLeast(1)
        state.firstVisibleColumnIndex = when {
            state.selectedColumnIndex == 0 -> state.firstVisibleColumnIndex
            state.selectedColumnIndex < state.firstVisibleColumnIndex -> state.selectedColumnIndex
            state.selectedColumnIndex >= state.firstVisibleColumnIndex + visibleYColumns ->
                state.selectedColumnIndex - visibleYColumns + 1
            else -> state.firstVisibleColumnIndex
        }.coerceIn(1, maximumFirstVisible)
    }

    private fun setEntry(state: TableViewState, text: String) {
        state.entry = text
        state.entryCursor = text.length
    }
}
