package net.amathboi.mi84mod.client.calculator

import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

enum class TableEntryMode(val displayName: String) {
    AUTO("Auto"),
    ASK("Ask")
}

/** Persistent settings used by TABLE to choose and evaluate its X and Y values. */
object TableSettingsMemory {
    private const val MAX_VALUE_LENGTH = 16
    private const val TBL_START_INDEX = 0
    private const val DELTA_TBL_INDEX = 1
    private const val INDEPENDENT_INDEX = 2
    private const val DEPENDENT_INDEX = 3

    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_table_settings.txt")
    private val labels = listOf("TblStart", "ΔTbl", "Indpnt", "Depend")
    private val values = mutableListOf("0", "1")
    private val cursors = MutableList(values.size) { index -> values[index].length }
    private var independentMode = TableEntryMode.AUTO
    private var dependentMode = TableEntryMode.AUTO

    var selectedIndex = 0
        private set

    init {
        load()
        values.indices.forEach { cursors[it] = values[it].length }
    }

    fun size(): Int = labels.size

    fun label(index: Int): String = labels[index]

    fun isValueSelected(): Boolean = selectedIndex <= DELTA_TBL_INDEX

    fun value(index: Int): String = values[index]

    fun cursor(index: Int): Int = cursors[index]

    fun tblStart(): String = values[TBL_START_INDEX]

    fun deltaTbl(): String = values[DELTA_TBL_INDEX]

    fun independentMode(): TableEntryMode = independentMode

    fun dependentMode(): TableEntryMode = dependentMode

    fun mode(index: Int): TableEntryMode = when (index) {
        INDEPENDENT_INDEX -> independentMode
        DEPENDENT_INDEX -> dependentMode
        else -> error("Table row $index does not contain an Auto/Ask choice")
    }

    fun selectPrevious() = select(selectedIndex - 1)

    fun selectNext() = select(selectedIndex + 1)

    fun moveLeft() {
        if (isValueSelected()) moveCursorLeft() else selectMode(TableEntryMode.AUTO)
    }

    fun moveRight() {
        if (isValueSelected()) moveCursorRight() else selectMode(TableEntryMode.ASK)
    }

    fun append(text: String, insertMode: Boolean = false) {
        if (!isValueSelected()) return
        val value = values[selectedIndex]
        val cursor = cursors[selectedIndex]
        val replacedLength = if (!insertMode && cursor < value.length) 1 else 0
        if (value.length - replacedLength + text.length > MAX_VALUE_LENGTH) return

        values[selectedIndex] =
            value.substring(0, cursor) + text + value.substring(cursor + replacedLength)
        cursors[selectedIndex] += text.length
        save()
    }

    fun appendDigit(digit: Char, insertMode: Boolean = false) {
        require(digit.isDigit())
        if (!isValueSelected()) return
        append(
            ExpressionEditingTokens.digitEntryText(
                values[selectedIndex],
                cursors[selectedIndex],
                digit
            ),
            insertMode
        )
    }

    fun toggleCurrentOperandSign() {
        if (!isValueSelected()) return
        val edit = ExpressionEditingTokens.toggleOperandSign(
            values[selectedIndex],
            cursors[selectedIndex],
            MAX_VALUE_LENGTH
        ) ?: return
        values[selectedIndex] = edit.text
        cursors[selectedIndex] = edit.cursor
        save()
    }

    fun deleteAtCursor() {
        if (!isValueSelected()) return
        val value = values[selectedIndex]
        val cursor = cursors[selectedIndex]
        if (cursor < value.length) {
            values[selectedIndex] = value.removeRange(cursor, cursor + 1)
            save()
        }
    }

    fun clearSelected() {
        if (!isValueSelected()) return
        values[selectedIndex] = ""
        cursors[selectedIndex] = 0
        save()
    }

    /** Replaces all settings together; used by tests and future table presets. */
    fun restore(
        tblStart: String,
        deltaTbl: String,
        independent: TableEntryMode,
        dependent: TableEntryMode
    ) {
        values[TBL_START_INDEX] = tblStart.take(MAX_VALUE_LENGTH)
        values[DELTA_TBL_INDEX] = deltaTbl.take(MAX_VALUE_LENGTH)
        values.indices.forEach { cursors[it] = values[it].length }
        independentMode = independent
        dependentMode = dependent
        save()
    }

    private fun moveCursorLeft() {
        val cursor = cursors[selectedIndex]
        if (cursor > 0) cursors[selectedIndex] = cursor - 1
    }

    private fun moveCursorRight() {
        val cursor = cursors[selectedIndex]
        if (cursor < values[selectedIndex].length) cursors[selectedIndex] = cursor + 1
    }

    private fun selectMode(mode: TableEntryMode) {
        when (selectedIndex) {
            INDEPENDENT_INDEX -> independentMode = mode
            DEPENDENT_INDEX -> dependentMode = mode
            else -> return
        }
        save()
    }

    private fun select(index: Int) {
        selectedIndex = index.coerceIn(labels.indices)
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines.forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) return@forEach
                when (parts[0]) {
                    "TblStart" -> values[TBL_START_INDEX] = parts[1].take(MAX_VALUE_LENGTH)
                    "ΔTbl" -> values[DELTA_TBL_INDEX] = parts[1].take(MAX_VALUE_LENGTH)
                    "Indpnt" -> parseMode(parts[1])?.let { independentMode = it }
                    "Depend" -> parseMode(parts[1])?.let { dependentMode = it }
                }
            }
        }
    }

    private fun parseMode(value: String): TableEntryMode? =
        TableEntryMode.entries.firstOrNull { it.displayName == value }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            listOf(
                "TblStart\t${values[TBL_START_INDEX]}",
                "ΔTbl\t${values[DELTA_TBL_INDEX]}",
                "Indpnt\t${independentMode.displayName}",
                "Depend\t${dependentMode.displayName}"
            )
        }
    }
}
