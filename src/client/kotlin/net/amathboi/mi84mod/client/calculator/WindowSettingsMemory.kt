package net.amathboi.mi84mod.client.calculator

import net.fabricmc.loader.api.FabricLoader
import kotlin.math.abs
import java.nio.file.Path

/** Persistent graph-window settings. They are stored now for use by graphing later. */
object WindowSettingsMemory {
    private const val MAX_VALUE_LENGTH = 16
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_window_settings.txt")
    private val labels = listOf("Xmin", "Xmax", "Xscl", "Ymin", "Ymax", "Yscl", "Xres", "ΔX", "TraceStep")
    private val defaults = listOf("-10", "10", "1", "-10", "10", "1", "1", "5/66", "5/33")
    private val values = defaults.toMutableList()
    private val cursors = MutableList(labels.size) { 0 }

    var selectedIndex = 0
        private set

    init {
        load()
        values.indices.forEach { cursors[it] = values[it].length }
    }

    fun size(): Int = labels.size

    fun label(index: Int): String = labels[index]

    fun value(index: Int): String = values[index]

    fun cursor(index: Int): Int = cursors[index]

    /** Returns an immutable copy suitable for Zoom Previous and Zoom Memory. */
    fun snapshot(): List<String> = values.toList()

    /** Replaces every Window field as one saved operation. */
    fun restore(snapshot: List<String>) {
        if (snapshot.size < values.size) return
        values.indices.forEach { index ->
            values[index] = snapshot[index].take(MAX_VALUE_LENGTH)
            cursors[index] = values[index].length
        }
        save()
    }

    /**
     * Replaces the seven plotted-graph fields while retaining ΔX and TraceStep. Invalid,
     * unrepresentable, or excessively large bounds leave the previous window untouched.
     */
    fun setGraphWindow(
        xMin: String,
        xMax: String,
        xScale: String,
        yMin: String,
        yMax: String,
        yScale: String,
        xResolution: String = "1"
    ): Boolean {
        val graphValues = listOf(xMin, xMax, xScale, yMin, yMax, yScale, xResolution)
        if (graphValues.any { it.length > MAX_VALUE_LENGTH }) return false

        val numericBounds = listOf(xMin, xMax, yMin, yMax).map { bound ->
            bound.toDoubleOrNull() ?: CalculatorDisplayMemory.evaluateForGraph(bound, 0.0) ?: return false
        }
        if (!supportsGraphBounds(numericBounds[0], numericBounds[1], numericBounds[2], numericBounds[3])) return false

        graphValues.forEachIndexed { index, value ->
            values[index] = value
            cursors[index] = values[index].length
        }
        save()
        return true
    }

    fun supportsGraphBounds(xMin: Double, xMax: Double, yMin: Double, yMax: Double): Boolean {
        val bounds = listOf(xMin, xMax, yMin, yMax)
        return bounds.all { it.isFinite() && abs(it) <= MAX_ABSOLUTE_BOUND } &&
            xMax - xMin >= MINIMUM_BOUND_SPAN &&
            yMax - yMin >= MINIMUM_BOUND_SPAN
    }

    fun selectPrevious() = select(selectedIndex - 1)

    fun selectNext() = select(selectedIndex + 1)

    fun moveCursorLeft() {
        if (cursors[selectedIndex] > 0) cursors[selectedIndex]--
    }

    fun moveCursorRight() {
        if (cursors[selectedIndex] < values[selectedIndex].length) cursors[selectedIndex]++
    }

    /** Calculator-style entry replaces the character beneath the forward cursor. */
    fun append(text: String) {
        val value = values[selectedIndex]
        val cursor = cursors[selectedIndex]
        val replacedLength = if (cursor < value.length) 1 else 0
        if (value.length - replacedLength + text.length > MAX_VALUE_LENGTH) return

        values[selectedIndex] = value.removeRange(cursor, cursor + replacedLength)
            .substring(0, cursor) + text + value.substring(cursor + replacedLength)
        cursors[selectedIndex] += text.length
        save()
    }

    fun deleteAtCursor() {
        val value = values[selectedIndex]
        val cursor = cursors[selectedIndex]
        if (cursor < value.length) {
            values[selectedIndex] = value.removeRange(cursor, cursor + 1)
            save()
        }
    }

    fun clearSelected() {
        values[selectedIndex] = ""
        cursors[selectedIndex] = 0
        save()
    }

    private fun select(index: Int) {
        selectedIndex = index.coerceIn(values.indices)
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines
                .take(labels.size)
                .forEachIndexed { index, savedValue -> values[index] = savedValue.take(MAX_VALUE_LENGTH) }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) { values.toList() }
    }

    private const val MAX_ABSOLUTE_BOUND = 1.0e12
    private const val MINIMUM_BOUND_SPAN = 1.0e-12
}
