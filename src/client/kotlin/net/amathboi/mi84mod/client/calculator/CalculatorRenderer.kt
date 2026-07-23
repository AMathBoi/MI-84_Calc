package net.amathboi.mi84mod.client.calculator

import kotlin.math.max
import kotlin.math.PI
import kotlin.math.roundToInt
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.ZoomTab
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** Draws calculator LCD views. It owns no calculator behavior or persistent state. */
class CalculatorRenderer(private val controller: CalculatorController) {
    companion object {
        private const val TEXTURE_WIDTH = 440
        private const val TEXTURE_HEIGHT = 1024

        // Calculator LCD bounds in source-texture pixels.
        private const val DISPLAY_LEFT = 21
        private const val DISPLAY_TOP = 87
        private const val DISPLAY_RIGHT = 418
        private const val DISPLAY_BOTTOM = 343
        private const val DISPLAY_PADDING = 8
        // Gray mode-indicator strip in source-texture pixels.
        private const val MODE_INDICATOR_LEFT = 21
        private const val MODE_INDICATOR_TOP = 49
        private const val MODE_INDICATOR_RIGHT = 418
        private const val MODE_INDICATOR_BOTTOM = 84
        private const val MODE_INDICATOR_PADDING = 4
        // Keep the LCD font proportional to the calculator's new half-size footprint.
        private const val DISPLAY_TEXT_SCALE = 0.5f
        private const val DISPLAY_LINE_HEIGHT = 6
        private const val DISPLAY_TEXT_COLOR = 0xFF1F1F1F.toInt()
        private const val DISPLAY_DIVIDER_COLOR = 0xFF555555.toInt()
        private const val DISPLAY_HIGHLIGHT_COLOR = 0xFF9A9A9A.toInt()
        private const val DISPLAY_INVERTED_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val MODE_SELECTION_COLOR = 0xFF000000.toInt()
        private const val Y_EQUALS_VISIBLE_ROWS = 9
        private const val MODE_VISIBLE_ROWS = 10
        private const val ZOOM_VISIBLE_ROWS = 9
        // The Mode page can use the final strip of white LCD beneath the shared display bounds.
        private const val MODE_BOTTOM_EXTENSION = 10
        private const val MODE_SELECTION_PADDING = 1

    }

    private val state get() = controller.state
    private val graphRenderer = CalculatorGraphRenderer(controller)
    private var x = 0
    private var y = 0
    private var width = 0
    private var height = 0

    fun render(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        renderModeIndicator(guiGraphics)
        when (state.view) {
            CalculatorView.HOME -> renderDisplay(guiGraphics)
            CalculatorView.Y_EQUALS -> renderYEqualsDisplay(guiGraphics)
            CalculatorView.WINDOW -> renderWindowDisplay(guiGraphics)
            CalculatorView.MODE -> renderModeDisplay(guiGraphics)
            CalculatorView.ZOOM -> renderZoomDisplay(guiGraphics)
            CalculatorView.ZOOM_FACTORS -> renderZoomFactorsDisplay(guiGraphics)
            CalculatorView.GRAPH -> graphRenderer.render(guiGraphics, x, y, width, height)
        }
    }

    /** Shows the five most useful active Mode values in the gray strip above every LCD view. */
    private fun renderModeIndicator(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (MODE_INDICATOR_LEFT + MODE_INDICATOR_PADDING) * scaleX).toInt()
        val right = (x + (MODE_INDICATOR_RIGHT - MODE_INDICATOR_PADDING) * scaleX).toInt()
        val top = y + MODE_INDICATOR_TOP * scaleY
        val bottom = y + MODE_INDICATOR_BOTTOM * scaleY
        val font = Minecraft.getInstance().font
        val modifierText = when (state.modifier) {
            ModifierLayer.NORMAL -> ""
            ModifierLayer.SECOND -> "2nd "
            ModifierLayer.ALPHA -> "Alpha "
        }
        val indicatorText = modifierText + ModeSettingsMemory.indicatorValues().joinToString(" ")
        val lineY = (top + bottom - font.lineHeight * DISPLAY_TEXT_SCALE) / 2.0f

        guiGraphics.enableScissor(left, top.toInt(), right, bottom.toInt())
        drawDisplayText(guiGraphics, indicatorText, left, lineY, DISPLAY_INVERTED_TEXT_COLOR)
        guiGraphics.disableScissor()
    }

    /** Draws submitted numbers on the right and the number currently being entered on the left. */
    private fun renderDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val bottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING) * scaleY).toInt()
        val font = Minecraft.getInstance().font

        var lineY = top
        val selectedLine =
            CalculatorDisplayMemory.historyLineFromNewest(state.historyNavigationPosition)
        val submitted = if (state.historyNavigationPosition > 0 && selectedLine != null) {
            // When browsing older history, newer rows fall off the bottom of the LCD.
            CalculatorDisplayMemory.allSubmitted().take(selectedLine.entryIndex + 1).toMutableList()
        } else {
            CalculatorDisplayMemory.submitted().toMutableList()
        }

        // Each completed number keeps its original left-aligned input, then gets a submitted
        // right-aligned copy and divider before the next input begins.
        while (
            submitted.isNotEmpty() &&
                top + submitted.size * DISPLAY_LINE_HEIGHT * 3 + DISPLAY_LINE_HEIGHT > bottom
        ) {
            submitted.removeAt(0)
            if (state.historyNavigationPosition == 0) CalculatorDisplayMemory.discardOldestSubmitted()
        }

        val firstEntryIndex = if (state.historyNavigationPosition > 0) {
            selectedLine!!.entryIndex + 1 - submitted.size
        } else {
            CalculatorDisplayMemory.allSubmitted().size - CalculatorDisplayMemory.submitted().size
        }
        submitted.forEachIndexed { visibleIndex, entry ->
            val entryIndex = firstEntryIndex + visibleIndex
            if (selectedLine?.entryIndex == entryIndex && !selectedLine.isResult) {
                guiGraphics.fill(left, lineY, right, lineY + DISPLAY_LINE_HEIGHT, DISPLAY_HIGHLIGHT_COLOR)
            }
            drawDisplayText(guiGraphics, entry.input, left, lineY)
            lineY += DISPLAY_LINE_HEIGHT
            if (selectedLine?.entryIndex == entryIndex && selectedLine.isResult) {
                guiGraphics.fill(left, lineY, right, lineY + DISPLAY_LINE_HEIGHT, DISPLAY_HIGHLIGHT_COLOR)
            }
            drawDisplayText(
                guiGraphics,
                entry.result,
                right - (font.width(entry.result) * DISPLAY_TEXT_SCALE).toInt(),
                lineY
            )
            lineY += DISPLAY_LINE_HEIGHT
            guiGraphics.fill(left, lineY, right, lineY + 1, DISPLAY_DIVIDER_COLOR)
            lineY += DISPLAY_LINE_HEIGHT
        }

        // The new entry always starts at the left edge beneath the most recent divider.
        val currentEntry = CalculatorDisplayMemory.current()
        drawDisplayText(guiGraphics, currentEntry, left, lineY)
        if (state.historyNavigationPosition == 0) renderCursor(guiGraphics, currentEntry, left, lineY)
    }

    /** Draws all nine Y= equations inside the calculator LCD. */
    private fun renderYEqualsDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val font = Minecraft.getInstance().font

        repeat(Y_EQUALS_VISIBLE_ROWS) { visibleRow ->
            val equationIndex = visibleRow

            val lineY = top + visibleRow * DISPLAY_LINE_HEIGHT
            guiGraphics.fill(left, lineY + 1, left + 4, lineY + 5, YEqualsMemory.colors[equationIndex])
            val label = "Y${YEqualsMemory.subscripts[equationIndex]}="
            val labelLeft = left + 6
            drawDisplayText(guiGraphics, label, labelLeft, lineY)
            val expressionLeft = labelLeft + (font.width(label) * DISPLAY_TEXT_SCALE).toInt() + 3
            val expression = YEqualsMemory.equation(equationIndex)
            drawDisplayText(guiGraphics, expression, expressionLeft, lineY + 0.5f)
            if (equationIndex == YEqualsMemory.selectedIndex) {
                renderYEqualsCursor(guiGraphics, expression, expressionLeft, lineY)
            }
        }
    }

    /** Draws the graph-window configuration view inside the calculator LCD. */
    private fun renderWindowDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val font = Minecraft.getInstance().font

        drawDisplayText(guiGraphics, "WINDOW", left, top)
        repeat(WindowSettingsMemory.size()) { settingIndex ->
            val lineY = top + (settingIndex + 1) * DISPLAY_LINE_HEIGHT
            val label = "${WindowSettingsMemory.label(settingIndex)}="
            drawDisplayText(guiGraphics, label, left, lineY)
            val valueLeft = left + (font.width(label) * DISPLAY_TEXT_SCALE).toInt() + 3
            val value = WindowSettingsMemory.value(settingIndex)
            drawDisplayText(guiGraphics, value, valueLeft, lineY + 0.5f)
            if (settingIndex == WindowSettingsMemory.selectedIndex) {
                renderWindowCursor(guiGraphics, value, valueLeft, lineY)
            }
        }
    }

    /** Draws the scrollable Mode list with inverted chosen options and a blinking active choice. */
    private fun renderModeDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val bottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING + MODE_BOTTOM_EXTENSION) * scaleY).toInt()
        val font = Minecraft.getInstance().font
        val selectedCategory = ModeSettingsMemory.selectedCategoryIndex
        val firstVisibleCategory = (selectedCategory - MODE_VISIBLE_ROWS / 2)
            .coerceIn(0, (ModeSettingsMemory.size() - MODE_VISIBLE_ROWS).coerceAtLeast(0))

        guiGraphics.enableScissor(left, top, right, bottom)
        repeat(MODE_VISIBLE_ROWS) { visibleRow ->
            val categoryIndex = firstVisibleCategory + visibleRow
            if (categoryIndex >= ModeSettingsMemory.size()) return@repeat

            val categoryText = "${ModeSettingsMemory.category(categoryIndex)}: "
            val options = ModeSettingsMemory.options(categoryIndex)
            val selectedOptionIndex = ModeSettingsMemory.selectedOptionIndex(categoryIndex)
            val optionStarts = mutableListOf<Int>()
            val rowText = buildString {
                append(categoryText)
                options.forEachIndexed { optionIndex, option ->
                    if (optionIndex > 0) append(' ')
                    optionStarts += length
                    append(option)
                }
            }
            val selectedStart = optionStarts[selectedOptionIndex]
            val selectedEnd = selectedStart + options[selectedOptionIndex].length
            val rowWidth = (font.width(rowText) * DISPLAY_TEXT_SCALE).toInt()
            val selectedLeft = (font.width(rowText.substring(0, selectedStart)) * DISPLAY_TEXT_SCALE).toInt()
            val selectedRight = (font.width(rowText.substring(0, selectedEnd)) * DISPLAY_TEXT_SCALE).toInt()
            val availableWidth = right - left
            val horizontalOffset = when {
                categoryIndex != selectedCategory || rowWidth <= availableWidth -> 0
                selectedRight + MODE_SELECTION_PADDING > availableWidth ->
                    selectedRight + MODE_SELECTION_PADDING - availableWidth
                selectedLeft < 0 -> selectedLeft
                else -> 0
            }
            val rowLeft = left - horizontalOffset
            val lineY = top + visibleRow * DISPLAY_LINE_HEIGHT

            drawDisplayText(guiGraphics, rowText, rowLeft, lineY)
            renderModeSelection(
                guiGraphics,
                rowText,
                options[selectedOptionIndex],
                selectedStart,
                selectedEnd,
                rowLeft,
                lineY,
                blink = categoryIndex == selectedCategory
            )
        }
        guiGraphics.disableScissor()
    }

    /** Draws the two-tab Zoom/Memory menu with a scrolling, inverted active entry. */
    private fun renderZoomDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val font = Minecraft.getInstance().font
        val memoryLeft = left + (font.width("ZOOM   ") * DISPLAY_TEXT_SCALE).toInt()

        drawZoomTab(guiGraphics, "ZOOM", left, top, state.zoomTab == ZoomTab.ZOOM)
        drawZoomTab(guiGraphics, "MEMORY", memoryLeft, top, state.zoomTab == ZoomTab.MEMORY)

        val options = controller.currentZoomOptions()
        val selectedIndex = controller.currentZoomSelectedIndex()
        val firstVisibleIndex = (selectedIndex - ZOOM_VISIBLE_ROWS / 2)
            .coerceIn(0, (options.size - ZOOM_VISIBLE_ROWS).coerceAtLeast(0))
        guiGraphics.enableScissor(left, top + DISPLAY_LINE_HEIGHT, right, zoomDisplayBottom())
        repeat(ZOOM_VISIBLE_ROWS) { visibleRow ->
            val optionIndex = firstVisibleIndex + visibleRow
            if (optionIndex >= options.size) return@repeat
            val option = options[optionIndex]
            val rowText = "${option.hotkey}: ${option.label}"
            val lineY = top + (visibleRow + 1) * DISPLAY_LINE_HEIGHT
            if (optionIndex == selectedIndex) {
                renderInvertedRow(guiGraphics, rowText, left, lineY)
            } else {
                drawDisplayText(guiGraphics, rowText, left, lineY)
            }
        }
        guiGraphics.disableScissor()
    }

    private fun drawZoomTab(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int,
        selected: Boolean
    ) {
        if (selected) {
            renderInvertedRow(guiGraphics, text, left, lineY)
        } else {
            drawDisplayText(guiGraphics, text, left, lineY)
        }
    }

    private fun renderInvertedRow(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        val textWidth =
            (Minecraft.getInstance().font.width(text) * DISPLAY_TEXT_SCALE).toInt().coerceAtLeast(2)
        guiGraphics.fill(
            left - MODE_SELECTION_PADDING,
            lineY - 1,
            left + textWidth + MODE_SELECTION_PADDING,
            lineY + DISPLAY_LINE_HEIGHT,
            MODE_SELECTION_COLOR
        )
        drawDisplayText(guiGraphics, text, left, lineY, DISPLAY_INVERTED_TEXT_COLOR)
    }

    private fun zoomDisplayBottom(): Int {
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        return (y + (DISPLAY_BOTTOM - DISPLAY_PADDING + MODE_BOTTOM_EXTENSION) * scaleY).toInt()
    }

    /** Draws the denominator picker opened by MEMORY > SetFactors. */
    private fun renderZoomFactorsDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()

        drawDisplayText(guiGraphics, "SET FACTORS", left, top)
        ZoomMemory.denominators().forEachIndexed { index, denominator ->
            val rowText = "${index + 1}: 1/$denominator"
            val lineY = top + (index + 1) * DISPLAY_LINE_HEIGHT
            if (index == state.factorSelectedIndex) {
                renderInvertedRow(guiGraphics, rowText, left, lineY)
            } else {
                drawDisplayText(guiGraphics, rowText, left, lineY)
            }
        }
    }

    fun graphDisplayAspect(width: Int, height: Int): Double =
        graphRenderer.displayAspect(width, height)

    /** Draws a blinking block cursor over the next editable token, or after the final token. */
    private fun renderCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = CalculatorDisplayMemory.cursorPosition()
        val cursorLeft = left + (font.width(text.substring(0, cursorPosition)) * DISPLAY_TEXT_SCALE).toInt()
        val tokenWidth = if (cursorPosition == text.length) font.width("0") else {
            font.width(text[cursorPosition].toString())
        }
        val cursorWidth = maxOf(2, (tokenWidth * DISPLAY_TEXT_SCALE).toInt())
        guiGraphics.fill(
            cursorLeft,
            lineY - 1,
            cursorLeft + cursorWidth,
            lineY + DISPLAY_LINE_HEIGHT - 1,
            MODE_SELECTION_COLOR
        )
    }

    /** Draws the forward-edit cursor for the selected Y= expression. */
    private fun renderYEqualsCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = YEqualsMemory.cursor(YEqualsMemory.selectedIndex)
        val cursorLeft = left + (font.width(text.substring(0, cursorPosition)) * DISPLAY_TEXT_SCALE).toInt()
        val tokenWidth = if (cursorPosition == text.length) font.width("0") else font.width(text[cursorPosition].toString())
        guiGraphics.fill(
            cursorLeft,
            lineY - 1,
            cursorLeft + maxOf(2, (tokenWidth * DISPLAY_TEXT_SCALE).toInt()),
            lineY + DISPLAY_LINE_HEIGHT - 1,
            DISPLAY_TEXT_COLOR
        )
    }

    private fun renderWindowCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = WindowSettingsMemory.cursor(WindowSettingsMemory.selectedIndex)
        val cursorLeft = left + (font.width(text.substring(0, cursorPosition)) * DISPLAY_TEXT_SCALE).toInt()
        val tokenWidth = if (cursorPosition == text.length) font.width("0") else font.width(text[cursorPosition].toString())
        guiGraphics.fill(
            cursorLeft,
            lineY - 1,
            cursorLeft + maxOf(2, (tokenWidth * DISPLAY_TEXT_SCALE).toInt()),
            lineY + DISPLAY_LINE_HEIGHT - 1,
            DISPLAY_TEXT_COLOR
        )
    }

    /** Inverts a chosen Mode option while leaving its category and other options untouched. */
    private fun renderModeSelection(
        guiGraphics: GuiGraphics,
        rowText: String,
        optionText: String,
        optionStart: Int,
        optionEnd: Int,
        left: Int,
        lineY: Int,
        blink: Boolean
    ) {
        if (blink && (System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorLeft = left + (font.width(rowText.substring(0, optionStart)) * DISPLAY_TEXT_SCALE).toInt()
        val cursorRight = left + (font.width(rowText.substring(0, optionEnd)) * DISPLAY_TEXT_SCALE).toInt()
        guiGraphics.fill(
            cursorLeft - MODE_SELECTION_PADDING,
            lineY - 1,
            maxOf(cursorLeft + 2, cursorRight + MODE_SELECTION_PADDING),
            lineY + DISPLAY_LINE_HEIGHT,
            DISPLAY_TEXT_COLOR
        )
        drawDisplayText(guiGraphics, optionText, cursorLeft, lineY, DISPLAY_INVERTED_TEXT_COLOR)
    }

    /** Draws Minecraft's built-in font at half size without requiring a separate font texture. */
    private fun drawDisplayText(
        guiGraphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        color: Int = DISPLAY_TEXT_COLOR
    ) {
        drawDisplayText(guiGraphics, text, x, y.toFloat(), color)
    }

    /** Draws Minecraft's built-in font at half size with optional sub-pixel LCD placement. */
    private fun drawDisplayText(
        guiGraphics: GuiGraphics,
        text: String,
        x: Int,
        y: Float,
        color: Int = DISPLAY_TEXT_COLOR
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y, 0f)
        pose.scale(DISPLAY_TEXT_SCALE, DISPLAY_TEXT_SCALE, 1f)
        guiGraphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false)
        pose.popPose()
    }

}
