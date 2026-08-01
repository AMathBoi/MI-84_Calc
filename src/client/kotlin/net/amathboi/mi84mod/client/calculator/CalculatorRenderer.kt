package net.amathboi.mi84mod.client.calculator

import kotlin.math.max
import kotlin.math.PI
import kotlin.math.roundToInt
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.controller.ListEditorController
import net.amathboi.mi84mod.client.calculator.controller.TableViewController
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.FractionTemplateState
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuTab
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
        // Fractions and indexed roots extend three logical pixels above their text baseline.
        // Reserve one additional source-texture pixel at the normal 0.25 render scale so the
        // first display row remains below the gray mode strip.
        private const val STRUCTURED_SYMBOL_TOP_INSET = 4
        private val Y_FUNCTION_TOKEN_PATTERN = Regex("Y([0-9])")
        private val Y_FUNCTION_SUBSCRIPTS =
            mapOf(
                "0" to "₀",
                "1" to "₁",
                "2" to "₂",
                "3" to "₃",
                "4" to "₄",
                "5" to "₅",
                "6" to "₆",
                "7" to "₇",
                "8" to "₈",
                "9" to "₉"
            )
        // Gray mode-indicator strip in source-texture pixels.
        private const val MODE_INDICATOR_LEFT = 21
        private const val MODE_INDICATOR_TOP = 49
        private const val MODE_INDICATOR_RIGHT = 418
        private const val MODE_INDICATOR_BOTTOM = 84
        private const val MODE_INDICATOR_PADDING = 4
        // Keep the LCD font proportional to the calculator's new half-size footprint.
        private const val DISPLAY_TEXT_SCALE = 0.5f
        private const val FRACTION_TEXT_SCALE = 0.28f
        private const val ROOT_SYMBOL_SCALE = 0.58f
        private const val ROOT_INDEX_SCALE = 0.32f
        private const val ROOT_CONTENT_GAP = 1
        private const val ROOT_EMPTY_INDEX_WIDTH = 3
        private const val ROOT_EMPTY_RADICAND_WIDTH = 4
        private const val COMBINATORIC_OPERATOR_SCALE = 0.58f
        private const val COMBINATORIC_OPERAND_SCALE = 0.34f
        private const val COMBINATORIC_EMPTY_OPERAND_WIDTH = 2
        private const val COMBINATORIC_OPERATOR_GAP = 1
        private const val DISPLAY_LINE_HEIGHT = 6
        private const val DISPLAY_TEXT_COLOR = 0xFF1F1F1F.toInt()
        private const val DISPLAY_DIVIDER_COLOR = 0xFF555555.toInt()
        private const val DISPLAY_HIGHLIGHT_COLOR = 0xFF9A9A9A.toInt()
        private const val DISPLAY_INVERTED_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val DISPLAY_MENU_BACKGROUND_COLOR = 0xFFFFFFFF.toInt()
        private const val MODE_SELECTION_COLOR = 0xFF000000.toInt()
        private const val Y_EQUALS_VISIBLE_ROWS = 9
        private const val MODE_VISIBLE_ROWS = 10
        private const val ZOOM_VISIBLE_ROWS = 9
        private const val COMPACT_MENU_VISIBLE_ROWS = 9
        private const val FUNCTION_MENU_LINE_HEIGHT = 5
        // The Mode page can use the final strip of white LCD beneath the shared display bounds.
        private const val MODE_BOTTOM_EXTENSION = 10
        private const val MODE_SELECTION_PADDING = 1
        private val RESULT_FRACTION_PATTERN = Regex("-?\\d+/-?\\d+")

    }

    private val state get() = controller.state
    private val graphRenderer = CalculatorGraphRenderer(controller)
    private data class FractionAnchor(val x: Int, val lineY: Int, val suffix: String)
    private data class EditorCursorBounds(
        val left: Int,
        val width: Int,
        val topOffset: Int,
        val bottomOffset: Int
    )
    private var fractionAnchor: FractionAnchor? = null
    private var x = 0
    private var y = 0
    private var width = 0
    private var height = 0

    fun render(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        fractionAnchor = null
        renderModeIndicator(guiGraphics)
        when (state.view) {
            CalculatorView.HOME -> renderDisplay(guiGraphics)
            CalculatorView.Y_EQUALS -> renderYEqualsDisplay(guiGraphics)
            CalculatorView.WINDOW -> renderWindowDisplay(guiGraphics)
            CalculatorView.TABLE_SETUP -> renderTableSetupDisplay(guiGraphics)
            CalculatorView.FORMAT -> renderFormatDisplay(guiGraphics)
            CalculatorView.MODE -> renderModeDisplay(guiGraphics)
            CalculatorView.ZOOM -> renderZoomDisplay(guiGraphics)
            CalculatorView.ZOOM_FACTORS -> renderZoomFactorsDisplay(guiGraphics)
            CalculatorView.COMPACT_MENU -> renderCompactMenuDisplay(guiGraphics)
            CalculatorView.LIST_EDITOR -> renderListEditorDisplay(guiGraphics)
            CalculatorView.TABLE -> renderTableDisplay(guiGraphics)
            CalculatorView.GRAPH -> graphRenderer.render(guiGraphics, x, y, width, height)
        }
        state.functionMenu?.let { renderFunctionMenuOverlay(guiGraphics) }
        state.fractionTemplate?.let { renderFractionTemplateEditor(guiGraphics, it) }
    }

    /** Approved TI-84-style STAT→Edit table for the built-in lists. */
    private fun renderListEditorDisplay(guiGraphics: GuiGraphics) {
        val editor = state.listEditor ?: return
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)
        val bottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING) * scaleY).toInt()
        val columnWidth = (right - left) / 3
        val headerY = top
        val tableTop = headerY + DISPLAY_LINE_HEIGHT + 2
        val entryY = bottom - DISPLAY_LINE_HEIGHT
        // Separate the first row's glyphs from the gray header divider without changing the
        // approved header layout itself.
        val tableRowsTop = tableTop + 2
        // Reserve a complete row-height above the input divider: a highlighted bottom table cell
        // must never overlap the `Ln(row)=` entry area.
        val visibleRows = ((entryY - DISPLAY_LINE_HEIGHT - tableRowsTop) / DISPLAY_LINE_HEIGHT)
            .coerceAtLeast(1)

        (0 until 3).forEach { column ->
            val listIndex = editor.firstVisibleListIndex + column
            val name = ListEditorController.names(editor).getOrNull(listIndex) ?: return@forEach
            val columnLeft = left + column * columnWidth
            val header = if (editor.creatingNamedList && name == "_") {
                if (editor.pendingListName.isEmpty()) "_" else editor.pendingListName
            } else name
            if (listIndex == editor.selectedListIndex) {
                guiGraphics.fill(columnLeft, headerY - 1, columnLeft + columnWidth - 2, headerY + DISPLAY_LINE_HEIGHT, MODE_SELECTION_COLOR)
                drawDisplayText(guiGraphics, header, columnLeft + 2, headerY, DISPLAY_INVERTED_TEXT_COLOR)
            } else {
                drawDisplayText(guiGraphics, header, columnLeft + 2, headerY)
            }
        }
        guiGraphics.fill(left, tableTop - 1, right, tableTop, DISPLAY_DIVIDER_COLOR)
        (1 until 3).forEach { column ->
            val dividerX = left + column * columnWidth - 1
            guiGraphics.fill(dividerX, tableTop, dividerX + 1, entryY - 2, DISPLAY_DIVIDER_COLOR)
        }

        (0 until visibleRows).forEach { visibleRow ->
            val row = editor.firstVisibleRowIndex + visibleRow
            val lineY = tableRowsTop + visibleRow * DISPLAY_LINE_HEIGHT
            (0 until 3).forEach { column ->
                val listIndex = editor.firstVisibleListIndex + column
                val name = ListEditorController.names(editor).getOrNull(listIndex) ?: return@forEach
                val columnLeft = left + column * columnWidth
                val value = ListEditorController.valueAt(name, row)
                val text = when {
                    value != null -> listCellText(value)
                    row == (CalculatorListMemory.value(name)?.dimension ?: -1) -> "_"
                    else -> ""
                }
                if (!ListEditorController.editingHeader(editor) &&
                    listIndex == editor.selectedListIndex && row == editor.selectedRowIndex
                ) {
                    guiGraphics.fill(columnLeft, lineY - 1, columnLeft + columnWidth - 2, lineY + DISPLAY_LINE_HEIGHT, DISPLAY_HIGHLIGHT_COLOR)
                }
                drawDisplayText(guiGraphics, text, columnLeft + 2, lineY)
            }
        }
        guiGraphics.fill(left, entryY - 2, right, entryY - 1, DISPLAY_DIVIDER_COLOR)
        val entryPrefix = if (ListEditorController.editingHeader(editor)) {
            "${ListEditorController.selectedName(editor)}="
        } else {
            "${ListEditorController.selectedName(editor)}(${editor.selectedRowNumber()})="
        }
        val entryText = entryPrefix + editor.entry
        drawDisplayText(guiGraphics, entryText, left, entryY)
        if ((!ListEditorController.editingHeader(editor) || editor.headerEntryLocked) &&
            (System.currentTimeMillis() / 500L) % 2L == 0L
        ) {
            val cursorText = entryPrefix + if (editor.headerEntryLocked) {
                editor.entry.take(editor.entryCursor)
            } else {
                editor.entry
            }
            val cursorLeft = left + measureDisplayText(cursorText)
            guiGraphics.fill(cursorLeft, entryY, cursorLeft + 1, entryY + DISPLAY_LINE_HEIGHT - 1, DISPLAY_TEXT_COLOR)
        }
    }

    private fun listCellText(value: CalculatorScalarValue): String =
        if (value.imaginary == null) ModeSettingsMemory.formatNumber(value.real)
        else "${ModeSettingsMemory.formatNumber(value.real)}+${ModeSettingsMemory.formatNumber(value.imaginary)}i"

    /** Draws the graph TABLE with packed non-empty Y columns and a list-style entry footer. */
    private fun renderTableDisplay(guiGraphics: GuiGraphics) {
        val table = state.table ?: return
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)
        val bottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING) * scaleY).toInt()
        val columnWidth = (right - left) / TableViewController.VISIBLE_COLUMNS
        val headerY = top
        val tableTop = headerY + DISPLAY_LINE_HEIGHT + 2
        val entryY = bottom - DISPLAY_LINE_HEIGHT
        val tableRowsTop = tableTop + 2
        val columns = TableViewController.columns()

        repeat(TableViewController.VISIBLE_COLUMNS) { visibleColumn ->
            val columnIndex =
                TableViewController.columnIndexAtVisiblePosition(table, visibleColumn)
                    ?: return@repeat
            val functionIndex = columns[columnIndex]
            val header = functionIndex?.let { "Y${YEqualsMemory.subscripts[it]}" } ?: "X"
            val columnLeft = left + visibleColumn * columnWidth
            if (TableViewController.editingHeader(table) &&
                columnIndex == table.selectedColumnIndex
            ) {
                guiGraphics.fill(
                    columnLeft,
                    headerY - 1,
                    columnLeft + columnWidth - 2,
                    headerY + DISPLAY_LINE_HEIGHT,
                    MODE_SELECTION_COLOR
                )
                drawDisplayText(
                    guiGraphics,
                    header,
                    columnLeft + 2,
                    headerY,
                    DISPLAY_INVERTED_TEXT_COLOR
                )
            } else {
                drawDisplayText(guiGraphics, header, columnLeft + 2, headerY)
            }
        }

        guiGraphics.fill(left, tableTop - 1, right, tableTop, DISPLAY_DIVIDER_COLOR)
        (1 until TableViewController.VISIBLE_COLUMNS).forEach { column ->
            val dividerX = left + column * columnWidth - 1
            guiGraphics.fill(dividerX, tableTop, dividerX + 1, entryY - 2, DISPLAY_DIVIDER_COLOR)
        }

        if (TableViewController.hasEnabledFunctions()) {
            repeat(TableViewController.VISIBLE_ROWS) { visibleRow ->
                val row = table.firstVisibleRowIndex + visibleRow
                val lineY = tableRowsTop + visibleRow * DISPLAY_LINE_HEIGHT
                repeat(TableViewController.VISIBLE_COLUMNS) { visibleColumn ->
                    val columnIndex =
                        TableViewController.columnIndexAtVisiblePosition(table, visibleColumn)
                            ?: return@repeat
                    val functionIndex = columns[columnIndex]
                    val columnLeft = left + visibleColumn * columnWidth
                    val text = if (functionIndex == null) {
                        TableViewController.xCellText(table, row)
                    } else {
                        TableViewController.yCellText(table, functionIndex, row)
                    }
                    if (!TableViewController.editingHeader(table) &&
                        columnIndex == table.selectedColumnIndex &&
                        row == table.selectedRowIndex
                    ) {
                        guiGraphics.fill(
                            columnLeft,
                            lineY - 1,
                            columnLeft + columnWidth - 2,
                            lineY + DISPLAY_LINE_HEIGHT,
                            DISPLAY_HIGHLIGHT_COLOR
                        )
                    }
                    drawDisplayText(guiGraphics, text, columnLeft + 2, lineY)
                }
            }
        }

        guiGraphics.fill(left, entryY - 2, right, entryY - 1, DISPLAY_DIVIDER_COLOR)
        val entryPrefix = TableViewController.bottomPrefix(table)
        val entryValue = TableViewController.bottomValue(table)
        drawDisplayText(guiGraphics, entryPrefix, left, entryY)
        val entryValueLeft = left + measureDisplayText(entryPrefix)
        drawMathExpression(guiGraphics, entryValue, entryValueLeft, entryY)
        if ((TableViewController.editingHeader(table) && table.headerEntryLocked) ||
            TableViewController.editingAskedX(table)
        ) {
            renderTableEntryCursor(guiGraphics, table.entry, entryValueLeft, entryY)
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
            ModifierLayer.NORMAL -> if (state.alphaLocked) "A-LOCK " else ""
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
        val top = editorDisplayTop(scaleY)
        // Home uses the otherwise unused bottom LCD padding so three complete history entries and
        // the active entry still fit after reserving room above for structured notation.
        val bottom = (y + DISPLAY_BOTTOM * scaleY).toInt()

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
                renderHistoryHighlight(guiGraphics, entry.input, left, right, lineY)
            }
            drawMathExpression(guiGraphics, entry.input, left, lineY)
            lineY += DISPLAY_LINE_HEIGHT
            if (selectedLine?.entryIndex == entryIndex && selectedLine.isResult) {
                renderHistoryHighlight(guiGraphics, mathResultText(entry.result), left, right, lineY)
            }
            val resultText = mathResultText(entry.result)
            drawMathExpression(
                guiGraphics,
                resultText,
                right - measureMathExpression(resultText),
                lineY
            )
            lineY += DISPLAY_LINE_HEIGHT
            guiGraphics.fill(left, lineY, right, lineY + 1, DISPLAY_DIVIDER_COLOR)
            lineY += DISPLAY_LINE_HEIGHT
        }

        // The new entry always starts at the left edge beneath the most recent divider.
        val currentEntry = CalculatorDisplayMemory.current()
        val homeTemplate = state.fractionTemplate?.takeIf {
            it.targetView == CalculatorView.HOME
        }
        if (homeTemplate != null) {
            val cursor = CalculatorDisplayMemory.cursorPosition()
            val start = homeTemplate.replacementStart ?: cursor
            val end = homeTemplate.originalToken?.let { start + it.length } ?: cursor
            val prefix = currentEntry.substring(0, start)
            drawMathExpression(guiGraphics, prefix, left, lineY)
            fractionAnchor =
                FractionAnchor(left + measureMathExpression(prefix), lineY, currentEntry.substring(end))
        } else {
            drawMathExpression(guiGraphics, currentEntry, left, lineY)
            if (state.historyNavigationPosition == 0) {
                renderCursor(guiGraphics, currentEntry, left, lineY)
            }
        }
    }

    /** Draws all nine Y= equations inside the calculator LCD. */
    private fun renderYEqualsDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)
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
            val yEqualsTemplate = state.fractionTemplate?.takeIf {
                it.targetView == CalculatorView.Y_EQUALS &&
                    equationIndex == YEqualsMemory.selectedIndex
            }
            if (yEqualsTemplate != null) {
                val cursor = YEqualsMemory.cursor(equationIndex)
                val start = yEqualsTemplate.replacementStart ?: cursor
                val end = yEqualsTemplate.originalToken?.let { start + it.length } ?: cursor
                val prefix = expression.substring(0, start)
                drawMathExpression(guiGraphics, prefix, expressionLeft, lineY)
                fractionAnchor = FractionAnchor(
                    expressionLeft + measureMathExpression(prefix),
                    lineY,
                    expression.substring(end)
                )
            } else {
                drawMathExpression(guiGraphics, expression, expressionLeft, lineY)
            }
            if (equationIndex == YEqualsMemory.selectedIndex &&
                state.fractionTemplate?.targetView != CalculatorView.Y_EQUALS
            ) {
                renderYEqualsCursor(guiGraphics, expression, expressionLeft, lineY)
            }
        }
    }

    /** Draws the graph-window configuration view inside the calculator LCD. */
    private fun renderWindowDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)
        val font = Minecraft.getInstance().font

        drawDisplayText(guiGraphics, "WINDOW", left, top)
        repeat(WindowSettingsMemory.size()) { settingIndex ->
            val lineY = top + (settingIndex + 1) * DISPLAY_LINE_HEIGHT
            val label = "${WindowSettingsMemory.label(settingIndex)}="
            drawDisplayText(guiGraphics, label, left, lineY)
            val valueLeft = left + (font.width(label) * DISPLAY_TEXT_SCALE).toInt() + 3
            val value = WindowSettingsMemory.value(settingIndex)
            val windowTemplate = state.fractionTemplate?.takeIf {
                it.targetView == CalculatorView.WINDOW &&
                    settingIndex == WindowSettingsMemory.selectedIndex
            }
            if (windowTemplate != null) {
                val cursor = WindowSettingsMemory.cursor(settingIndex)
                val start = windowTemplate.replacementStart ?: cursor
                val end = windowTemplate.originalToken?.let { start + it.length } ?: cursor
                val prefix = value.substring(0, start)
                drawMathExpression(guiGraphics, prefix, valueLeft, lineY)
                fractionAnchor =
                    FractionAnchor(valueLeft + measureMathExpression(prefix), lineY, value.substring(end))
            } else {
                drawMathExpression(guiGraphics, value, valueLeft, lineY)
            }
            if (settingIndex == WindowSettingsMemory.selectedIndex &&
                state.fractionTemplate?.targetView != CalculatorView.WINDOW
            ) {
                renderWindowCursor(guiGraphics, value, valueLeft, lineY)
            }
        }
    }

    /** Draws the table inputs and Auto/Ask choices used by TABLE. */
    private fun renderTableSetupDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)
        val font = Minecraft.getInstance().font

        drawDisplayText(guiGraphics, "TABLE SETUP", left, top)
        repeat(TableSettingsMemory.size()) { settingIndex ->
            val lineY = top + (settingIndex + 1) * DISPLAY_LINE_HEIGHT
            val label = TableSettingsMemory.label(settingIndex)
            if (settingIndex < 2) {
                val labelText = "$label="
                drawDisplayText(guiGraphics, labelText, left, lineY)
                val valueLeft =
                    left + (font.width(labelText) * DISPLAY_TEXT_SCALE).toInt() + 3
                val value = TableSettingsMemory.value(settingIndex)
                drawMathExpression(guiGraphics, value, valueLeft, lineY)
                if (settingIndex == TableSettingsMemory.selectedIndex) {
                    renderTableSetupCursor(guiGraphics, value, valueLeft, lineY)
                }
            } else {
                val rowText = "$label: Auto Ask"
                drawDisplayText(guiGraphics, rowText, left, lineY)
                val option = TableSettingsMemory.mode(settingIndex).displayName
                val optionStart = rowText.indexOf(option, startIndex = label.length + 2)
                renderModeSelection(
                    guiGraphics,
                    rowText,
                    option,
                    optionStart,
                    optionStart + option.length,
                    left,
                    lineY,
                    blink = settingIndex == TableSettingsMemory.selectedIndex
                )
            }
        }
    }

    /** Draws persistent graph FORMAT options; every row is honored by the graph renderer. */
    private fun renderFormatDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val top = editorDisplayTop(scaleY)

        drawDisplayText(guiGraphics, "FORMAT", left, top)
        repeat(FormatSettingsMemory.size()) { settingIndex ->
            val options = FormatSettingsMemory.options(settingIndex)
            val selectedIndex = FormatSettingsMemory.selectedOptionIndex(settingIndex)
            val rowText = options.joinToString(" ")
            val optionText = options[selectedIndex]
            val optionStart = options.take(selectedIndex).sumOf { it.length + 1 }
            val lineY = top + (settingIndex + 1) * DISPLAY_LINE_HEIGHT
            drawDisplayText(guiGraphics, rowText, left, lineY)
            var optionOffset = 0
            options.forEachIndexed { optionIndex, option ->
                if (!FormatSettingsMemory.optionAvailable(settingIndex, optionIndex)) {
                    drawDisplayText(
                        guiGraphics,
                        option,
                        left + measureDisplayText(rowText.substring(0, optionOffset)),
                        lineY,
                        DISPLAY_HIGHLIGHT_COLOR
                    )
                }
                optionOffset += option.length + 1
            }
            renderModeSelection(
                guiGraphics,
                rowText,
                optionText,
                optionStart,
                optionStart + optionText.length,
                left,
                lineY,
                blink = settingIndex == FormatSettingsMemory.selectedSettingIndex
            )
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

    private fun renderInvertedRow(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int,
        textColor: Int = DISPLAY_INVERTED_TEXT_COLOR
    ) {
        val textWidth =
            (Minecraft.getInstance().font.width(text) * DISPLAY_TEXT_SCALE).toInt().coerceAtLeast(2)
        guiGraphics.fill(
            left - MODE_SELECTION_PADDING,
            lineY - 1,
            left + textWidth + MODE_SELECTION_PADDING,
            lineY + DISPLAY_LINE_HEIGHT,
            MODE_SELECTION_COLOR
        )
        drawDisplayText(guiGraphics, text, left, lineY, textColor)
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

    /** Draws approved compact token menus without owning navigation or insertion behavior. */
    private fun renderCompactMenuDisplay(guiGraphics: GuiGraphics) {
        val menu = state.compactMenu ?: return
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val clipLeft = (x + DISPLAY_LEFT * scaleX).toInt()
        val clipTop = (y + DISPLAY_TOP * scaleY).toInt()
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val bottom = zoomDisplayBottom()
        val font = Minecraft.getInstance().font

        // Leave the outer display padding inside the scissor so selected tabs are centered in
        // their inverted box instead of clipping its top and left padding.
        guiGraphics.enableScissor(clipLeft, clipTop, right, bottom)
        var tabLeft = left
        menu.currentDefinition.tabs.forEachIndexed { index, tab ->
            drawZoomTab(guiGraphics, tab.label, tabLeft, top, menu.selectedTabIndex == index)
            tabLeft += (font.width("${tab.label}   ") * DISPLAY_TEXT_SCALE).toInt()
        }

        val items = menu.selectedTab.items
        val firstVisibleIndex = (menu.selectedItemIndex - COMPACT_MENU_VISIBLE_ROWS / 2)
            .coerceIn(0, (items.size - COMPACT_MENU_VISIBLE_ROWS).coerceAtLeast(0))
        repeat(COMPACT_MENU_VISIBLE_ROWS) { visibleRow ->
            val itemIndex = firstVisibleIndex + visibleRow
            if (itemIndex >= items.size) return@repeat
            val item = items[itemIndex]
            val suffix = if (item.available) "" else " [deferred]"
            val rowText = "${item.hotkey}: ${item.label}$suffix"
            val lineY = top + (visibleRow + 1) * DISPLAY_LINE_HEIGHT
            when {
                itemIndex == menu.selectedItemIndex ->
                    renderInvertedRow(
                        guiGraphics,
                        rowText,
                        left,
                        lineY,
                        if (item.available) DISPLAY_INVERTED_TEXT_COLOR else DISPLAY_HIGHLIGHT_COLOR
                    )
                item.available -> drawDisplayText(guiGraphics, rowText, left, lineY)
                else -> drawDisplayText(guiGraphics, rowText, left, lineY, DISPLAY_HIGHLIGHT_COLOR)
            }
        }

        guiGraphics.disableScissor()
    }

    /** Draws the F1–F4 option box and bottom tabs over the retained editable view. */
    private fun renderFunctionMenuOverlay(guiGraphics: GuiGraphics) {
        val menu = state.functionMenu ?: return
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt()
        val top = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val bottom = zoomDisplayBottom()
        val tabLineY = bottom - DISPLAY_LINE_HEIGHT
        val menuBottom = tabLineY - 1
        val displayedRows =
            if (menu.selectedTab == FunctionMenuTab.YVAR) 5 else menu.items.size
        val menuTop =
            (menuBottom - displayedRows * FUNCTION_MENU_LINE_HEIGHT - 2).coerceAtLeast(top)

        guiGraphics.fill(left, menuTop, right, menuBottom, DISPLAY_MENU_BACKGROUND_COLOR)
        guiGraphics.fill(left, menuTop, right, menuTop + 1, MODE_SELECTION_COLOR)
        guiGraphics.fill(left, menuBottom - 1, right, menuBottom, MODE_SELECTION_COLOR)
        guiGraphics.fill(left, menuTop, left + 1, menuBottom, MODE_SELECTION_COLOR)
        guiGraphics.fill(right - 1, menuTop, right, menuBottom, MODE_SELECTION_COLOR)

        menu.items.forEachIndexed { index, item ->
            val suffix = if (item.available) "" else " [deferred]"
            val hotkey = item.hotkey.takeIf(String::isNotEmpty)?.let { "$it: " }.orEmpty()
            val rowText = "$hotkey${item.label}$suffix"
            val twoColumnYVar = menu.selectedTab == FunctionMenuTab.YVAR
            val column = if (twoColumnYVar && index >= 5) 1 else 0
            val row = if (twoColumnYVar) index % 5 else index
            val columnLeft = if (column == 0) left else (left + right) / 2
            val columnRight = if (column == 0 && twoColumnYVar) (left + right) / 2 else right
            val lineY = menuTop + 1 + row * FUNCTION_MENU_LINE_HEIGHT
            if (index == menu.selectedItemIndex) {
                guiGraphics.fill(
                    columnLeft + 1,
                    lineY,
                    columnRight - 1,
                    (lineY + FUNCTION_MENU_LINE_HEIGHT).coerceAtMost(menuBottom - 1),
                    MODE_SELECTION_COLOR
                )
                drawDisplayText(
                    guiGraphics,
                    rowText,
                    columnLeft + 2,
                    lineY,
                    if (item.available) DISPLAY_INVERTED_TEXT_COLOR else DISPLAY_HIGHLIGHT_COLOR
                )
            } else {
                drawDisplayText(
                    guiGraphics,
                    rowText,
                    columnLeft + 2,
                    lineY,
                    if (item.available) DISPLAY_TEXT_COLOR else DISPLAY_HIGHLIGHT_COLOR
                )
            }
        }

        guiGraphics.fill(left, tabLineY - 1, right, bottom, DISPLAY_MENU_BACKGROUND_COLOR)
        val font = Minecraft.getInstance().font
        var tabLeft = left
        FunctionMenuTab.entries.forEach { tab ->
            drawZoomTab(
                guiGraphics,
                tab.label,
                tabLeft,
                tabLineY,
                menu.selectedTab == tab
            )
            tabLeft += (font.width("${tab.label}  ") * DISPLAY_TEXT_SCALE).toInt()
        }
    }

    /** Draws the active fraction compactly at the insertion cursor without replacing the view. */
    private fun renderFractionTemplateEditor(
        guiGraphics: GuiGraphics,
        template: FractionTemplateState
    ) {
        val anchor = fractionAnchor ?: return
        val font = Minecraft.getInstance().font
        var fractionLeft = anchor.x

        var numeratorIndex = 0
        var denominatorIndex = 1
        if (template.mixedNumber) {
            val whole = template.field(0)
            val displayedWhole = whole.ifEmpty { "□" }
            val wholeWidth =
                (font.width(displayedWhole) * DISPLAY_TEXT_SCALE).roundToInt().coerceAtLeast(4) + 2
            renderInlineTemplateField(
                guiGraphics,
                whole,
                fractionLeft,
                anchor.lineY,
                wholeWidth,
                template.selectedFieldIndex == 0,
                template.cursor(0),
                DISPLAY_TEXT_SCALE
            )
            fractionLeft += wholeWidth + 1
            numeratorIndex = 1
            denominatorIndex = 2
        }

        val numerator = template.field(numeratorIndex)
        val denominator = template.field(denominatorIndex)
        val fractionWidth =
            maxOf(
                font.width(numerator.ifEmpty { "□" }),
                font.width(denominator.ifEmpty { "□" })
            )
                .let { (it * FRACTION_TEXT_SCALE).roundToInt().coerceAtLeast(3) + 2 }
        renderInlineTemplateField(
            guiGraphics,
            numerator,
            fractionLeft,
            anchor.lineY - 3,
            fractionWidth,
            template.selectedFieldIndex == numeratorIndex,
            template.cursor(numeratorIndex),
            FRACTION_TEXT_SCALE
        )
        drawFractionBar(guiGraphics, fractionLeft, anchor.lineY, fractionWidth)
        renderInlineTemplateField(
            guiGraphics,
            denominator,
            fractionLeft,
            anchor.lineY + 2,
            fractionWidth,
            template.selectedFieldIndex == denominatorIndex,
            template.cursor(denominatorIndex),
            FRACTION_TEXT_SCALE
        )
        drawMathExpression(
            guiGraphics,
            anchor.suffix,
            fractionLeft + fractionWidth + 1,
            anchor.lineY
        )
    }

    private fun renderInlineTemplateField(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int,
        width: Int,
        selected: Boolean,
        cursorPosition: Int,
        scale: Float
    ) {
        val font = Minecraft.getInstance().font
        val displayedText = text.ifEmpty { "□" }
        val textWidth = (font.width(displayedText) * scale).roundToInt()
        val textLeft = left + ((width - textWidth) / 2).coerceAtLeast(0)
        drawScaledDisplayText(
            guiGraphics,
            displayedText,
            textLeft,
            lineY,
            scale,
            DISPLAY_TEXT_COLOR
        )
        if (!selected || (System.currentTimeMillis() / 500L) % 2L != 0L) return

        val cursor = cursorPosition.coerceIn(0, text.length)
        val unscaledCursorLeft = font.width(text.substring(0, cursor))
        val cursorText = when {
            text.isEmpty() -> displayedText
            cursor < text.length -> text[cursor].toString()
            else -> ""
        }
        val unscaledCursorWidth = font.width(cursorText.ifEmpty { "0" }).coerceAtLeast(1)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(textLeft.toFloat(), lineY.toFloat(), 0f)
        pose.scale(scale, scale, 1f)
        guiGraphics.fill(
            unscaledCursorLeft,
            0,
            unscaledCursorLeft + unscaledCursorWidth,
            font.lineHeight,
            MODE_SELECTION_COLOR
        )
        if (cursorText.isNotEmpty()) {
            guiGraphics.drawString(
                font,
                cursorText,
                unscaledCursorLeft,
                0,
                DISPLAY_INVERTED_TEXT_COLOR,
                false
            )
        }
        pose.popPose()
    }

    /** Draws linear text while replacing completed frac/mixed tokens with a compact stacked form. */
    private fun drawMathExpression(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int
    ): Int {
        val displayText = listReferenceDisplayText(text)
        if (displayText != text) return drawMathExpression(guiGraphics, displayText, left, lineY)
        val token = MathDisplayTokens.firstIn(text)
        if (token == null) {
            val displayText = formatFunctionVariables(text)
            drawDisplayText(guiGraphics, displayText, left, lineY)
            return measureDisplayText(displayText)
        }

        var drawLeft = left
        val prefix = text.substring(0, token.start)
        val displayPrefix = formatFunctionVariables(prefix)
        drawDisplayText(guiGraphics, displayPrefix, drawLeft, lineY)
        drawLeft += measureDisplayText(displayPrefix)

        drawLeft += when (token) {
            is MathDisplayToken.Fraction -> {
                token.whole?.let { whole ->
                    drawDisplayText(guiGraphics, whole, drawLeft, lineY)
                    drawLeft += measureDisplayText(whole) + 1
                }
                val fractionWidth =
                    completedFractionWidth(token.numerator, token.denominator)
                drawCompletedFraction(
                    guiGraphics,
                    token.numerator,
                    token.denominator,
                    drawLeft,
                    lineY,
                    fractionWidth
                )
                fractionWidth + 1
            }
            is MathDisplayToken.Root ->
                drawRoot(guiGraphics, token, drawLeft, lineY)
            is MathDisplayToken.Combinatoric ->
                drawCombinatoric(guiGraphics, token, drawLeft, lineY)
        }

        val suffix = text.substring(token.endExclusive)
        drawLeft += drawMathExpression(guiGraphics, suffix, drawLeft, lineY)
        return drawLeft - left
    }

    private fun measureMathExpression(text: String): Int {
        val displayText = listReferenceDisplayText(text)
        if (displayText != text) return measureMathExpression(displayText)
        val token =
            MathDisplayTokens.firstIn(text)
                ?: return measureDisplayText(formatFunctionVariables(text))
        return measureDisplayText(formatFunctionVariables(text.substring(0, token.start))) +
            measureMathToken(token) +
            measureMathExpression(text.substring(token.endExclusive))
    }

    /** Returns the highest ascent used by any nonlinear token in an expression. */
    private fun mathExpressionTopOffset(text: String): Int {
        val token = MathDisplayTokens.firstIn(text) ?: return 0
        val tokenTopOffset = when (token) {
            is MathDisplayToken.Fraction,
            is MathDisplayToken.Root -> -3
            is MathDisplayToken.Combinatoric -> -1
        }
        return minOf(tokenTopOffset, mathExpressionTopOffset(text.substring(token.endExclusive)))
    }

    /** Highlights the full visual height of a selected history expression, not just its baseline. */
    private fun renderHistoryHighlight(
        guiGraphics: GuiGraphics,
        expression: String,
        left: Int,
        right: Int,
        lineY: Int
    ) {
        guiGraphics.fill(
            left,
            lineY + mathExpressionTopOffset(expression),
            right,
            lineY + DISPLAY_LINE_HEIGHT,
            DISPLAY_HIGHLIGHT_COLOR
        )
    }

    /** Maps a raw editor prefix to the matching position inside nonlinear notation. */
    private fun measureMathCursorPrefix(text: String): Int {
        val displayText = listReferenceDisplayText(text)
        if (displayText != text) return measureMathCursorPrefix(displayText)
        val token =
            MathDisplayTokens.firstIn(text)
                ?: return measureDisplayText(formatFunctionVariables(text))
        val prefixWidth =
            measureDisplayText(formatFunctionVariables(text.substring(0, token.start)))
        val tokenWidth = when (token) {
            is MathDisplayToken.Combinatoric ->
                if (!token.complete && !token.rightOperandEntered) {
                    measureScaledText(token.leftOperand, COMBINATORIC_OPERAND_SCALE)
                } else if (!token.complete) {
                    combinatoricOperandWidth(token.leftOperand) +
                        COMBINATORIC_OPERATOR_GAP +
                        measureScaledText(token.operator.toString(), COMBINATORIC_OPERATOR_SCALE) +
                        measureScaledText(token.rightOperand, COMBINATORIC_OPERAND_SCALE)
                } else {
                    measureMathToken(token)
                }
            is MathDisplayToken.Root ->
                if (!token.complete) measureIncompleteRootCursor(token)
                else measureMathToken(token)
            is MathDisplayToken.Fraction -> measureMathToken(token)
        }
        return prefixWidth + tokenWidth +
            measureMathCursorPrefix(text.substring(token.endExclusive))
    }

    private fun formatFunctionVariables(text: String): String =
        Y_FUNCTION_TOKEN_PATTERN.replace(text) { match ->
            "Y${Y_FUNCTION_SUBSCRIPTS.getValue(match.groupValues[1])}"
        }

    private fun measureMathToken(token: MathDisplayToken): Int = when (token) {
        is MathDisplayToken.Fraction ->
            (token.whole?.let(::measureDisplayText) ?: 0) +
                (if (token.whole == null) 0 else 1) +
                completedFractionWidth(token.numerator, token.denominator) +
                1
        is MathDisplayToken.Root -> measureRoot(token)
        is MathDisplayToken.Combinatoric -> measureCombinatoric(token)
    }

    private fun drawRoot(
        guiGraphics: GuiGraphics,
        root: MathDisplayToken.Root,
        left: Int,
        lineY: Int
    ): Int {
        val index = root.index.orEmpty()
        val indexWidth = rootIndexWidth(root)
        if (index.isNotEmpty()) {
            drawScaledDisplayText(
                guiGraphics,
                index,
                left,
                lineY - 2,
                ROOT_INDEX_SCALE
            )
        }
        if (root.fieldOrder != RootFieldOrder.RADICAND_ONLY && index.isEmpty()) {
            drawDottedBox(
                guiGraphics,
                left - 1,
                lineY - 3,
                left + indexWidth,
                lineY + 1
            )
        }

        val radicalLeft = left + indexWidth
        drawScaledDisplayText(
            guiGraphics,
            "√",
            radicalLeft,
            lineY - 1,
            ROOT_SYMBOL_SCALE
        )
        val radicalWidth = measureScaledText("√", ROOT_SYMBOL_SCALE).coerceAtLeast(3)
        val radicandLeft = radicalLeft + radicalWidth + ROOT_CONTENT_GAP
        val radicandWidth =
            measureMathExpression(root.radicand).coerceAtLeast(ROOT_EMPTY_RADICAND_WIDTH)
        drawMathExpression(guiGraphics, root.radicand, radicandLeft, lineY)
        guiGraphics.fill(
            radicalLeft + radicalWidth - 1,
            lineY - 2,
            radicandLeft + radicandWidth,
            lineY - 1,
            DISPLAY_TEXT_COLOR
        )
        return indexWidth + radicalWidth + ROOT_CONTENT_GAP + radicandWidth
    }

    private fun measureRoot(root: MathDisplayToken.Root): Int {
        val radicalWidth = measureScaledText("√", ROOT_SYMBOL_SCALE).coerceAtLeast(3)
        return rootIndexWidth(root) + radicalWidth + ROOT_CONTENT_GAP +
            measureMathExpression(root.radicand).coerceAtLeast(ROOT_EMPTY_RADICAND_WIDTH)
    }

    private fun rootIndexWidth(root: MathDisplayToken.Root): Int =
        root.index.orEmpty().takeIf(String::isNotEmpty)
            ?.let { measureScaledText(it, ROOT_INDEX_SCALE) + 1 }
            ?: if (root.fieldOrder != RootFieldOrder.RADICAND_ONLY) {
                ROOT_EMPTY_INDEX_WIDTH
            } else {
                0
            }

    private fun measureIncompleteRootCursor(root: MathDisplayToken.Root): Int {
        val indexCursor = measureScaledText(root.index.orEmpty(), ROOT_INDEX_SCALE)
        val radicandCursor =
            rootIndexWidth(root) +
                measureScaledText("√", ROOT_SYMBOL_SCALE).coerceAtLeast(3) +
                ROOT_CONTENT_GAP +
                measureMathExpression(root.radicand)
        return when (root.fieldOrder) {
            RootFieldOrder.INDEX_THEN_RADICAND ->
                if (root.secondFieldEntered) radicandCursor else indexCursor
            RootFieldOrder.RADICAND_THEN_INDEX ->
                if (root.secondFieldEntered) indexCursor else radicandCursor
            RootFieldOrder.RADICAND_ONLY -> radicandCursor
        }
    }

    private fun drawCombinatoric(
        guiGraphics: GuiGraphics,
        token: MathDisplayToken.Combinatoric,
        left: Int,
        lineY: Int
    ): Int {
        val leftWidth = combinatoricOperandWidth(token.leftOperand)
        drawScaledDisplayText(
            guiGraphics,
            token.leftOperand,
            left,
            lineY + 2,
            COMBINATORIC_OPERAND_SCALE
        )
        if (token.leftOperand.isEmpty()) {
            drawDottedBox(
                guiGraphics,
                left - 1,
                lineY + 1,
                left + leftWidth,
                lineY + DISPLAY_LINE_HEIGHT - 1
            )
        }
        val operatorLeft = left + leftWidth + COMBINATORIC_OPERATOR_GAP
        val operator = token.operator.toString()
        drawScaledDisplayText(
            guiGraphics,
            operator,
            operatorLeft,
            lineY - 1,
            COMBINATORIC_OPERATOR_SCALE
        )
        val operatorWidth = measureScaledText(operator, COMBINATORIC_OPERATOR_SCALE)
        drawScaledDisplayText(
            guiGraphics,
            token.rightOperand,
            operatorLeft + operatorWidth,
            lineY + 2,
            COMBINATORIC_OPERAND_SCALE
        )
        if (token.rightOperand.isEmpty()) {
            val rightLeft = operatorLeft + operatorWidth
            drawDottedBox(
                guiGraphics,
                rightLeft - 1,
                lineY + 1,
                rightLeft + combinatoricOperandWidth(token.rightOperand),
                lineY + DISPLAY_LINE_HEIGHT - 1
            )
        }
        return measureCombinatoric(token)
    }

    private fun measureCombinatoric(token: MathDisplayToken.Combinatoric): Int =
        combinatoricOperandWidth(token.leftOperand) +
            COMBINATORIC_OPERATOR_GAP +
            measureScaledText(token.operator.toString(), COMBINATORIC_OPERATOR_SCALE) +
            combinatoricOperandWidth(token.rightOperand)

    private fun combinatoricOperandWidth(text: String): Int =
        measureScaledText(text, COMBINATORIC_OPERAND_SCALE)
            .coerceAtLeast(COMBINATORIC_EMPTY_OPERAND_WIDTH)

    private fun measureScaledText(text: String, scale: Float): Int =
        (Minecraft.getInstance().font.width(text) * scale).roundToInt()

    private fun drawDottedBox(
        guiGraphics: GuiGraphics,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        for (dotX in left until right step 2) {
            guiGraphics.fill(dotX, top, dotX + 1, top + 1, DISPLAY_HIGHLIGHT_COLOR)
            guiGraphics.fill(dotX, bottom - 1, dotX + 1, bottom, DISPLAY_HIGHLIGHT_COLOR)
        }
        for (dotY in top + 1 until bottom - 1 step 2) {
            guiGraphics.fill(left, dotY, left + 1, dotY + 1, DISPLAY_HIGHLIGHT_COLOR)
            guiGraphics.fill(right - 1, dotY, right, dotY + 1, DISPLAY_HIGHLIGHT_COLOR)
        }
    }

    private fun drawCompletedFraction(
        guiGraphics: GuiGraphics,
        numerator: String,
        denominator: String,
        left: Int,
        lineY: Int,
        width: Int
    ) {
        val font = Minecraft.getInstance().font
        val numeratorWidth = (font.width(numerator) * FRACTION_TEXT_SCALE).roundToInt()
        val denominatorWidth = (font.width(denominator) * FRACTION_TEXT_SCALE).roundToInt()
        drawScaledDisplayText(
            guiGraphics,
            numerator,
            left + ((width - numeratorWidth) / 2).coerceAtLeast(0),
            lineY - 3,
            FRACTION_TEXT_SCALE
        )
        drawFractionBar(guiGraphics, left, lineY, width)
        drawScaledDisplayText(
            guiGraphics,
            denominator,
            left + ((width - denominatorWidth) / 2).coerceAtLeast(0),
            lineY + 2,
            FRACTION_TEXT_SCALE
        )
    }

    private fun drawFractionBar(
        guiGraphics: GuiGraphics,
        left: Int,
        lineY: Int,
        fieldWidth: Int
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(left.toFloat(), lineY.toFloat(), 0f)
        pose.scale(1f, 0.5f, 1f)
        guiGraphics.fill(
            0,
            0,
            fieldWidth,
            1,
            DISPLAY_TEXT_COLOR
        )
        pose.popPose()
    }

    private fun completedFractionWidth(numerator: String, denominator: String): Int {
        val font = Minecraft.getInstance().font
        return maxOf(font.width(numerator), font.width(denominator))
            .let { (it * FRACTION_TEXT_SCALE).roundToInt().coerceAtLeast(3) + 2 }
    }

    private fun mathResultText(result: String): String =
        if (RESULT_FRACTION_PATTERN.matches(result)) {
            val (numerator, denominator) = result.split('/', limit = 2)
            "frac($numerator,$denominator)"
        } else {
            result
        }

    /** Raw `@NAME` list references render with a readable L prefix beside their list name. */
    private fun listReferenceDisplayText(text: String): String =
        text.replace(Regex("@([A-Z]{1,5})"), "L$1")

    private fun measureDisplayText(text: String): Int =
        (Minecraft.getInstance().font.width(text) * DISPLAY_TEXT_SCALE).toInt()

    /** Keeps tall fraction, root, and combinatoric notation out of the mode-indicator strip. */
    private fun editorDisplayTop(scaleY: Float): Int =
        (y + (DISPLAY_TOP + DISPLAY_PADDING + STRUCTURED_SYMBOL_TOP_INSET) * scaleY).toInt()

    fun graphDisplayAspect(width: Int, height: Int): Double =
        graphRenderer.displayAspect(width, height)

    /** Draws the blinking forward-edit cursor over the next token, or after the final token. */
    private fun renderCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = CalculatorDisplayMemory.cursorPosition()
        val cursorLeft = left + measureMathCursorPrefix(text.substring(0, cursorPosition))
        val cursorBounds =
            editorCursorBounds(text, cursorPosition, cursorLeft, font.width("0"))
        renderEditorCursor(guiGraphics, cursorBounds, lineY, MODE_SELECTION_COLOR)
    }

    /** Draws the forward-edit cursor for the selected Y= expression. */
    private fun renderYEqualsCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = YEqualsMemory.cursor(YEqualsMemory.selectedIndex)
        val cursorLeft = left + measureMathCursorPrefix(text.substring(0, cursorPosition))
        val cursorBounds =
            editorCursorBounds(text, cursorPosition, cursorLeft, font.width("0"))
        renderEditorCursor(guiGraphics, cursorBounds, lineY, DISPLAY_TEXT_COLOR)
    }

    private fun renderWindowCursor(guiGraphics: GuiGraphics, text: String, left: Int, lineY: Int) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = WindowSettingsMemory.cursor(WindowSettingsMemory.selectedIndex)
        val cursorLeft = left + measureMathCursorPrefix(text.substring(0, cursorPosition))
        val cursorBounds =
            editorCursorBounds(text, cursorPosition, cursorLeft, font.width("0"))
        renderEditorCursor(guiGraphics, cursorBounds, lineY, DISPLAY_TEXT_COLOR)
    }

    private fun renderTableSetupCursor(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int
    ) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val font = Minecraft.getInstance().font
        val cursorPosition = TableSettingsMemory.cursor(TableSettingsMemory.selectedIndex)
        val cursorLeft = left + measureMathCursorPrefix(text.substring(0, cursorPosition))
        val cursorBounds =
            editorCursorBounds(text, cursorPosition, cursorLeft, font.width("0"))
        renderEditorCursor(guiGraphics, cursorBounds, lineY, DISPLAY_TEXT_COLOR)
    }

    private fun renderTableEntryCursor(
        guiGraphics: GuiGraphics,
        text: String,
        left: Int,
        lineY: Int
    ) {
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return
        val table = state.table ?: return
        val cursorPosition = if (TableViewController.editingHeader(table)) {
            table.entryCursor
        } else {
            text.length
        }
        val font = Minecraft.getInstance().font
        val cursorLeft = left + measureMathCursorPrefix(text.substring(0, cursorPosition))
        val cursorBounds =
            editorCursorBounds(text, cursorPosition, cursorLeft, font.width("0"))
        renderEditorCursor(guiGraphics, cursorBounds, lineY, DISPLAY_TEXT_COLOR)
    }

    /** Insert mode keeps the character visible and marks its insertion point with an underscore. */
    private fun renderEditorCursor(
        guiGraphics: GuiGraphics,
        cursorBounds: EditorCursorBounds,
        lineY: Int,
        color: Int
    ) {
        val top =
            if (state.insertMode) lineY + cursorBounds.bottomOffset - 1
            else lineY + cursorBounds.topOffset
        val bottom = if (state.insertMode) top + 1 else lineY + cursorBounds.bottomOffset
        guiGraphics.fill(
            cursorBounds.left,
            top,
            cursorBounds.left + cursorBounds.width,
            bottom,
            color
        )
    }

    /**
     * A structured fraction is entered with Right rather than selected as one oversized glyph.
     * Its leading cursor therefore sits just before the stacked symbol.
     */
    private fun editorCursorBounds(
        text: String,
        cursorPosition: Int,
        measuredLeft: Int,
        fallbackGlyphWidth: Int
    ): EditorCursorBounds {
        if (ExpressionEditingTokens.structuredFractionStartingAt(text, cursorPosition) != null) {
            return EditorCursorBounds(
                measuredLeft - 2,
                2,
                -1,
                DISPLAY_LINE_HEIGHT - 1
            )
        }
        val cursorText = text.getOrNull(cursorPosition)
            ?.takeUnless { it == ',' || it == ')' }
            ?.toString()
            ?: "0"
        when (MathDisplayTokens.cursorFieldAt(text, cursorPosition)) {
            MathCursorField.COMBINATORIC_OPERAND -> {
                val width =
                    measureScaledText(cursorText, COMBINATORIC_OPERAND_SCALE).coerceAtLeast(1)
                return EditorCursorBounds(
                    measuredLeft,
                    width,
                    1,
                    DISPLAY_LINE_HEIGHT - 1
                )
            }
            MathCursorField.ROOT_INDEX -> {
                val width = measureScaledText(cursorText, ROOT_INDEX_SCALE).coerceAtLeast(1)
                return EditorCursorBounds(measuredLeft, width, -3, 1)
            }
            null -> Unit
        }
        val tokenWidth =
            if (cursorPosition == text.length) {
                fallbackGlyphWidth
            } else {
                Minecraft.getInstance().font.width(text[cursorPosition].toString())
            }
        return EditorCursorBounds(
            measuredLeft,
            maxOf(2, (tokenWidth * DISPLAY_TEXT_SCALE).toInt()),
            -1,
            DISPLAY_LINE_HEIGHT - 1
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
        drawScaledDisplayText(guiGraphics, text, x, y, DISPLAY_TEXT_SCALE, color)
    }

    private fun drawScaledDisplayText(
        guiGraphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        scale: Float,
        color: Int = DISPLAY_TEXT_COLOR
    ) {
        drawScaledDisplayText(guiGraphics, text, x, y.toFloat(), scale, color)
    }

    private fun drawScaledDisplayText(
        guiGraphics: GuiGraphics,
        text: String,
        x: Int,
        y: Float,
        scale: Float,
        color: Int = DISPLAY_TEXT_COLOR
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y, 0f)
        pose.scale(scale, scale, 1f)
        guiGraphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false)
        pose.popPose()
    }

}
