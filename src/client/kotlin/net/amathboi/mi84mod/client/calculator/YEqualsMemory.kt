package net.amathboi.mi84mod.client.calculator

import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Persistent graph equations and their calculator-style forward edit cursors. */
object YEqualsMemory {
    private const val MAX_EXPRESSION_LENGTH = 31
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_y_equals_memory.txt")

    val colors = intArrayOf(
        0xFF55AAFF.toInt(), // Y1 starts blue.
        0xFFFF5555.toInt(), 0xFF55FF55.toInt(), 0xFFFFAA00.toInt(),
        0xFFAA55FF.toInt(), 0xFF55FFFF.toInt(), 0xFFFF55AA.toInt(),
        0xFFFFFF55.toInt(), 0xFFAAAAAA.toInt()
    )
    val subscripts = listOf("₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉")

    private val equations = MutableList(9) { "" }
    private val cursors = MutableList(9) { 0 }
    var selectedIndex = 0
        private set

    init {
        load()
        equations.indices.forEach { cursors[it] = equations[it].length }
    }

    fun equation(index: Int): String = equations[index]

    fun cursor(index: Int): Int = cursors[index]

    fun select(index: Int) {
        selectedIndex = index.coerceIn(equations.indices)
    }

    fun selectPrevious() = select(selectedIndex - 1)

    fun selectNext() = select(selectedIndex + 1)

    fun moveCursorLeft() {
        if (cursors[selectedIndex] > 0) cursors[selectedIndex]--
    }

    fun moveCursorRight() {
        if (cursors[selectedIndex] < equations[selectedIndex].length) cursors[selectedIndex]++
    }

    /** Calculator-style entry overwrites the character beneath the forward cursor. */
    fun append(text: String) {
        val expression = equations[selectedIndex]
        val cursor = cursors[selectedIndex]
        val replacedLength = if (cursor < expression.length) 1 else 0
        if (expression.length - replacedLength + text.length > MAX_EXPRESSION_LENGTH) return

        equations[selectedIndex] = expression.removeRange(cursor, cursor + replacedLength)
            .substring(0, cursor) + text + expression.substring(cursor + replacedLength)
        cursors[selectedIndex] += text.length
        save()
    }

    fun deleteAtCursor() {
        val expression = equations[selectedIndex]
        val cursor = cursors[selectedIndex]
        if (cursor < expression.length) {
            equations[selectedIndex] = expression.removeRange(cursor, cursor + 1)
            save()
        }
    }

    fun clearSelected() {
        equations[selectedIndex] = ""
        cursors[selectedIndex] = 0
        save()
    }

    private fun load() {
        if (!Files.exists(memoryFile)) return
        runCatching {
            Files.readAllLines(memoryFile, StandardCharsets.UTF_8)
                .take(equations.size)
                .forEachIndexed { index, savedEquation ->
                    equations[index] = savedEquation.take(MAX_EXPRESSION_LENGTH)
                }
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(memoryFile.parent)
            Files.write(memoryFile, equations, StandardCharsets.UTF_8)
        }
    }
}
