package net.amathboi.mi84mod.client.calculator.controller

import java.math.BigDecimal
import net.amathboi.mi84mod.client.calculator.CalculatorListMemory
import net.amathboi.mi84mod.client.calculator.CalculatorListLiteral
import net.amathboi.mi84mod.client.calculator.CalculatorListValue
import net.amathboi.mi84mod.client.calculator.CalculatorScalarValue
import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.ui.ListEditorState

/** Input for the approved list table; it deliberately accepts only real numeric cell entries. */
object ListEditorController {
    private const val VISIBLE_COLUMNS = 3
    // Matches the renderer after reserving clear space above the bottom cell-entry line.
    private const val VISIBLE_ROWS = 6
    private const val MAX_ENTRY_LENGTH = 31

    fun handle(command: CalculatorCommand, state: ListEditorState, insertMode: Boolean = false) {
        if (state.creatingNamedList) {
            handleNamedListCreation(command, state)
            return
        }
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
            is CalculatorCommand.Digit -> append(state, command.value.toString())
            is CalculatorCommand.Operator -> append(state, command.value.toString())
            is CalculatorCommand.Function -> append(state, "${command.name}(")
            is CalculatorCommand.InsertVariable -> append(state, command.variable.symbol.toString())
            CalculatorCommand.Decimal -> append(state, ".")
            CalculatorCommand.OpenParenthesis -> append(state, "(")
            CalculatorCommand.CloseParenthesis -> append(state, ")")
            CalculatorCommand.OpenListLiteral -> append(state, "{")
            CalculatorCommand.CloseListLiteral -> append(state, "}")
            CalculatorCommand.Comma -> append(state, ",")
            CalculatorCommand.Variable -> append(state, "X")
            CalculatorCommand.Square -> append(state, "^2")
            CalculatorCommand.Reciprocal -> append(state, "^-1")
            CalculatorCommand.InsertAns -> append(state, "Ans")
            CalculatorCommand.InsertPi -> append(state, "π")
            CalculatorCommand.InsertEuler -> append(state, "e")
            CalculatorCommand.InsertInverseSine -> append(state, "sin⁻¹(")
            CalculatorCommand.InsertInverseCosine -> append(state, "cos⁻¹(")
            CalculatorCommand.InsertInverseTangent -> append(state, "tan⁻¹(")
            CalculatorCommand.InsertTenPower -> append(state, "10^(")
            CalculatorCommand.InsertEulerPower -> append(state, "e^(")
            CalculatorCommand.InsertSquareRoot -> append(state, "sqrt(")
            CalculatorCommand.InsertScientificExponent -> append(state, "EE")
            CalculatorCommand.Negative -> {
                state.entry = if (state.entry.startsWith('-')) state.entry.drop(1) else "-${state.entry}"
            }
            CalculatorCommand.Delete -> if (state.entry.isNotEmpty()) setEntry(state, state.entry.dropLast(1))
            CalculatorCommand.Clear -> {
                if (editingHeader(state)) state.headerEntryLocked = true
                setEntry(state, "")
            }
            CalculatorCommand.Enter -> commit(state)
            else -> Unit
        }
    }

    fun selectedName(state: ListEditorState): String = names(state)[state.selectedListIndex]

    fun valueAt(name: String, row: Int): CalculatorScalarValue? =
        CalculatorListMemory.value(name)?.values?.getOrNull(row)

    fun listLiteral(name: String): String =
        CalculatorListMemory.value(name)?.values.orEmpty().joinToString(separator = ",", prefix = "{", postfix = "}") { value ->
            value.real.toPlainString()
        }

    fun names(state: ListEditorState): List<String> =
        CalculatorListMemory.names().toMutableList().also { names ->
            if (state.creatingNamedList) names.add(state.selectedListIndex, "_")
        }

    fun editingHeader(state: ListEditorState): Boolean = state.selectedRowIndex < 0

    private fun append(state: ListEditorState, text: String) {
        if (state.entry.length + text.length <= MAX_ENTRY_LENGTH) setEntry(state, state.entry + text)
    }

    private fun moveColumn(state: ListEditorState, delta: Int) {
        state.selectedListIndex = (state.selectedListIndex + delta).coerceIn(0, names(state).lastIndex)
        val selected = state.selectedListIndex
        val maximumFirstVisible = (names(state).size - VISIBLE_COLUMNS).coerceAtLeast(0)
        state.firstVisibleListIndex = when {
            selected < state.firstVisibleListIndex -> selected
            selected >= state.firstVisibleListIndex + VISIBLE_COLUMNS -> selected - VISIBLE_COLUMNS + 1
            else -> state.firstVisibleListIndex
        }.coerceIn(0, maximumFirstVisible)
        if (editingHeader(state)) loadHeaderLiteral(state)
    }

    private fun moveRow(state: ListEditorState, delta: Int) {
        if (editingHeader(state)) {
            if (delta > 0) {
                state.selectedRowIndex = 0
                state.headerEntryLocked = false
                setEntry(state, "")
            }
            return
        }
        if (state.selectedRowIndex == 0 && delta < 0) {
            state.selectedRowIndex = -1
            loadHeaderLiteral(state)
            return
        }
        val dimension = CalculatorListMemory.value(selectedName(state))?.dimension ?: 0
        val lastRow = if (dimension < CalculatorListValue.MAX_LIST_LENGTH) {
            dimension
        } else {
            (dimension - 1).coerceAtLeast(0)
        }
        state.selectedRowIndex = state.selectedRowIndex.coerceIn(0, lastRow)
        state.selectedRowIndex = (state.selectedRowIndex + delta).coerceIn(0, lastRow)
        state.firstVisibleRowIndex = when {
            state.selectedRowIndex < state.firstVisibleRowIndex -> state.selectedRowIndex
            state.selectedRowIndex >= state.firstVisibleRowIndex + VISIBLE_ROWS -> state.selectedRowIndex - VISIBLE_ROWS + 1
            else -> state.firstVisibleRowIndex
        }
    }

    private fun commit(state: ListEditorState) {
        if (editingHeader(state)) {
            state.headerEntryLocked = true
            state.entryCursor = state.entry.length
            return
        }
        val value = CalculatorDisplayMemory.evaluateRealForListEntry(state.entry) ?: return
        val name = selectedName(state)
        val current = CalculatorListMemory.value(name)?.values?.toMutableList() ?: return
        if (state.selectedRowIndex !in 0 until CalculatorListValue.MAX_LIST_LENGTH ||
            (state.selectedRowIndex >= current.size &&
                current.size >= CalculatorListValue.MAX_LIST_LENGTH)
        ) return
        while (current.size <= state.selectedRowIndex) current += CalculatorScalarValue(BigDecimal.ZERO)
        current[state.selectedRowIndex] = CalculatorScalarValue(value)
        CalculatorListMemory.set(name, CalculatorListValue(current))
        setEntry(state, "")
        moveRow(state, 1)
    }

    private fun commitListLiteral(state: ListEditorState): Boolean {
        val name = selectedName(state)
        if (state.entry.isEmpty()) {
            CalculatorListMemory.clear(name)
            setEntry(state, "")
            return true
        }
        val list = runCatching {
            CalculatorListLiteral.parse(state.entry) { element ->
                val value = CalculatorDisplayMemory.evaluateRealForListEntry(element)
                    ?: error("A real scalar list element is required")
                CalculatorScalarValue(value)
            }
        }.getOrNull()
        if (list == null) {
            // Keep the stored list intact and return the editor to its previous valid literal.
            setEntry(state, listLiteral(name))
            return false
        }
        CalculatorListMemory.set(name, list)
        setEntry(state, listLiteral(name))
        return true
    }

    private fun handleLockedHeaderEntry(
        command: CalculatorCommand,
        state: ListEditorState,
        insertMode: Boolean
    ) {
        when (command) {
            CalculatorCommand.Left -> state.entryCursor = (state.entryCursor - 1).coerceAtLeast(0)
            CalculatorCommand.Right -> state.entryCursor = (state.entryCursor + 1).coerceAtMost(state.entry.length)
            CalculatorCommand.Delete -> if (state.entryCursor < state.entry.length) {
                state.entry = state.entry.removeRange(state.entryCursor, state.entryCursor + 1)
            }
            CalculatorCommand.Clear -> setEntry(state, "")
            CalculatorCommand.Enter -> if (commitListLiteral(state)) state.headerEntryLocked = false
            is CalculatorCommand.Digit -> editAtCursor(state, command.value.toString(), insertMode)
            CalculatorCommand.Decimal -> editAtCursor(state, ".", insertMode)
            CalculatorCommand.OpenListLiteral -> editAtCursor(state, "{", insertMode)
            CalculatorCommand.CloseListLiteral -> editAtCursor(state, "}", insertMode)
            CalculatorCommand.Comma -> editAtCursor(state, ",", insertMode)
            is CalculatorCommand.Operator -> if (command.value == '-') editAtCursor(state, "-", insertMode)
            CalculatorCommand.Negative -> editAtCursor(state, "-", insertMode)
            else -> Unit
        }
    }

    private fun editAtCursor(state: ListEditorState, text: String, insertMode: Boolean) {
        val replaced = if (!insertMode && state.entryCursor < state.entry.length) 1 else 0
        if (state.entry.length - replaced + text.length > MAX_ENTRY_LENGTH) return
        state.entry = state.entry.removeRange(state.entryCursor, state.entryCursor + replaced)
            .let { it.substring(0, state.entryCursor) + text + it.substring(state.entryCursor) }
        state.entryCursor += text.length
    }

    private fun loadHeaderLiteral(state: ListEditorState) {
        state.headerEntryLocked = false
        setEntry(state, listLiteral(selectedName(state)))
    }

    private fun setEntry(state: ListEditorState, text: String) {
        state.entry = text
        state.entryCursor = text.length
    }

    fun beginNamedListCreation(state: ListEditorState) {
        if (!editingHeader(state) || state.creatingNamedList) return
        state.creatingNamedList = true
        state.pendingListName = ""
        state.headerEntryLocked = false
        setEntry(state, "")
    }

    private fun handleNamedListCreation(command: CalculatorCommand, state: ListEditorState) {
        when (command) {
            is CalculatorCommand.InsertVariable -> if (state.pendingListName.length < 5) {
                state.pendingListName += command.variable.symbol
            }
            CalculatorCommand.Delete -> if (state.pendingListName.isNotEmpty()) {
                state.pendingListName = state.pendingListName.dropLast(1)
            }
            CalculatorCommand.Clear -> {
                state.creatingNamedList = false
                state.pendingListName = ""
            }
            CalculatorCommand.Enter -> {
                val before = CalculatorListMemory.names().getOrNull(state.selectedListIndex)
                if (CalculatorListMemory.createNamed(state.pendingListName, before)) {
                    state.creatingNamedList = false
                    state.pendingListName = ""
                    state.selectedRowIndex = -1
                    loadHeaderLiteral(state)
                }
            }
            else -> Unit
        }
    }
}
