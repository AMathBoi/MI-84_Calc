package net.amathboi.mi84mod.client.calculator.input

/** Texture-relative physical layout. This file deliberately contains no calculator behavior. */
object CalculatorKeyLayout {
    const val SOURCE_WIDTH = 440
    const val SOURCE_HEIGHT = 1024
    const val KEY_WIDTH = 72
    const val KEY_HEIGHT = 55
    private val X_POSITIONS = intArrayOf(19, 102, 184, 266, 349)

    data class KeyHitbox(
        val key: CalculatorKey,
        val sourceX: Int,
        val sourceY: Int
    ) {
        fun contains(
            mouseX: Double,
            mouseY: Double,
            widgetX: Int,
            widgetY: Int,
            widgetWidth: Int,
            widgetHeight: Int
        ): Boolean {
            val scaleX = widgetWidth.toDouble() / SOURCE_WIDTH
            val scaleY = widgetHeight.toDouble() / SOURCE_HEIGHT
            val left = widgetX + sourceX * scaleX
            val top = widgetY + sourceY * scaleY
            return mouseX >= left && mouseX < left + KEY_WIDTH * scaleX &&
                mouseY >= top && mouseY < top + KEY_HEIGHT * scaleY
        }
    }

    val keys: List<KeyHitbox> = listOf(
        row(359, CalculatorKey.Y_EQUALS, CalculatorKey.WINDOW, CalculatorKey.ZOOM, CalculatorKey.TRACE, CalculatorKey.GRAPH),
        row(437, CalculatorKey.SECOND, CalculatorKey.MODE, CalculatorKey.DELETE, CalculatorKey.DOWN, CalculatorKey.UP),
        row(502, CalculatorKey.ALPHA, CalculatorKey.VARIABLE, CalculatorKey.STAT, CalculatorKey.LEFT, CalculatorKey.RIGHT),
        row(567, CalculatorKey.MATH, CalculatorKey.APPS, CalculatorKey.PROGRAM, CalculatorKey.VARS, CalculatorKey.CLEAR),
        row(632, CalculatorKey.RECIPROCAL, CalculatorKey.SIN, CalculatorKey.COS, CalculatorKey.TAN, CalculatorKey.POWER),
        row(698, CalculatorKey.SQUARE, CalculatorKey.COMMA, CalculatorKey.OPEN_PARENTHESIS, CalculatorKey.CLOSE_PARENTHESIS, CalculatorKey.DIVIDE),
        row(763, CalculatorKey.LOG, CalculatorKey.DIGIT_7, CalculatorKey.DIGIT_8, CalculatorKey.DIGIT_9, CalculatorKey.MULTIPLY),
        row(828, CalculatorKey.LN, CalculatorKey.DIGIT_4, CalculatorKey.DIGIT_5, CalculatorKey.DIGIT_6, CalculatorKey.SUBTRACT),
        row(893, CalculatorKey.STORE, CalculatorKey.DIGIT_1, CalculatorKey.DIGIT_2, CalculatorKey.DIGIT_3, CalculatorKey.ADD),
        row(958, CalculatorKey.ON, CalculatorKey.DIGIT_0, CalculatorKey.DECIMAL, CalculatorKey.NEGATIVE, CalculatorKey.ENTER)
    ).flatten()

    fun hitTest(
        mouseX: Double,
        mouseY: Double,
        widgetX: Int,
        widgetY: Int,
        widgetWidth: Int,
        widgetHeight: Int
    ): CalculatorKey? = keys.firstOrNull {
        it.contains(mouseX, mouseY, widgetX, widgetY, widgetWidth, widgetHeight)
    }?.key

    private fun row(y: Int, vararg keys: CalculatorKey): List<KeyHitbox> {
        require(keys.size == X_POSITIONS.size)
        return keys.mapIndexed { index, key -> KeyHitbox(key, X_POSITIONS[index], y) }
    }
}
