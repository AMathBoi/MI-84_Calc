package net.amathboi.mi84mod.client.calculator

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW

/** A draggable calculator overlay, used only while the inventory screen is open. */
class CalculatorWidget(private val screenWidth: Int, private val screenHeight: Int) :
    AbstractWidget(
        CalculatorPosition.xOrDefault(
            screenWidth,
            CALCULATOR_WIDTH,
            INVENTORY_WIDTH,
            GAP_FROM_INVENTORY
        ),
        CalculatorPosition.yOrDefault(screenHeight, CALCULATOR_HEIGHT),
        CALCULATOR_WIDTH,
        CALCULATOR_HEIGHT,
        Component.empty()
    ) {
    companion object {
        private val CALCULATOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mi84_calc", "textures/calculator/calc.png")

        // Default on-screen calculator window: half the previous 220×512 footprint.
        // The 440×1024 source texture and texture-relative hitboxes scale with these dimensions.
        private const val CALCULATOR_WIDTH = 110
        private const val CALCULATOR_HEIGHT = 256
        private const val TEXTURE_WIDTH = 440
        private const val TEXTURE_HEIGHT = 1024
        private const val DRAG_HANDLE_HEIGHT = 44

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
        private const val TRACE_FOOTER_OFFSET = 6
        private const val DISPLAY_TEXT_COLOR = 0xFF1F1F1F.toInt()
        private const val DISPLAY_DIVIDER_COLOR = 0xFF555555.toInt()
        private const val DISPLAY_HIGHLIGHT_COLOR = 0xFF9A9A9A.toInt()
        private const val DISPLAY_INVERTED_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val MODE_SELECTION_COLOR = 0xFF000000.toInt()
        private const val GRAPH_AXIS_COLOR = 0xFF555555.toInt()
        private const val Y_EQUALS_VISIBLE_ROWS = 9
        private const val MODE_VISIBLE_ROWS = 10
        private const val ZOOM_VISIBLE_ROWS = 9
        // The Mode page can use the final strip of white LCD beneath the shared display bounds.
        private const val MODE_BOTTOM_EXTENSION = 10
        private const val MODE_SELECTION_PADDING = 1

        // Source-texture dimensions of one calculator key. Button positions below use source pixels.
        private const val BUTTON_WIDTH = 72
        private const val BUTTON_HEIGHT = 55

        // Placeholder inventory dimensions, used to place the calculator to its left.
        private const val INVENTORY_WIDTH = 176
        private const val GAP_FROM_INVENTORY = 12

    }

    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0
    // Zero means normal editing; positive values select result/input rows from newest to oldest.
    private var historyNavigationPosition = 0
    private var lcdView = LcdView.HOME
    private var traceState: TraceState? = null
    private var zoomGraphState: ZoomGraphState? = null
    private var zoomTab = ZoomTab.ZOOM
    private var zoomSelectedIndex = 0
    private var memorySelectedIndex = 0
    private var factorSelectedIndex = ZoomMemory.selectedDenominatorIndex()
    private var zoomAlphaArmed = false

    private val zoomOptions = listOf(
        ZoomMenuOption("1", "ZBox"),
        ZoomMenuOption("2", "Zoom In"),
        ZoomMenuOption("3", "Zoom Out"),
        ZoomMenuOption("4", "ZDecimal"),
        ZoomMenuOption("5", "ZSquare"),
        ZoomMenuOption("6", "ZStandard"),
        ZoomMenuOption("7", "ZTrig"),
        ZoomMenuOption("8", "ZInteger"),
        ZoomMenuOption("9", "ZoomStat"),
        ZoomMenuOption("0", "ZoomFit"),
        ZoomMenuOption("A", "ZQuadrant1"),
        ZoomMenuOption("B", "ZFrac1/2"),
        ZoomMenuOption("C", "ZFrac1/3"),
        ZoomMenuOption("D", "ZFrac1/4"),
        ZoomMenuOption("E", "ZFrac1/5"),
        ZoomMenuOption("F", "ZFrac1/8"),
        ZoomMenuOption("G", "ZFrac1/10")
    )
    private val memoryOptions = listOf(
        ZoomMenuOption("1", "ZPrevious"),
        ZoomMenuOption("2", "ZoomSto"),
        ZoomMenuOption("3", "ZoomRcl"),
        ZoomMenuOption("4", "SetFactors...")
    )

    /**
     * Hitboxes are kept in texture coordinates so they stay aligned whenever the calculator moves
     * or its texture scale changes. Button dispatch below defines the implemented behavior.
     */
    private data class CalculatorButton(
        val label: String,
        val sourceX: Int,
        val sourceY: Int
    )

    private val calculatorButtons = listOf(
        // Row 1: Y= (variable editor), Window (graph window), Zoom, Trace, Graph.
        CalculatorButton("y=", 19, 359),
        CalculatorButton("window", 102, 359),
        CalculatorButton("zoom", 184, 359),
        CalculatorButton("trace", 266, 359),
        CalculatorButton("graph", 349, 359),

        // Row 2: 2nd (secondary functions), Mode (settings), Del (delete character), down/up arrows.
        CalculatorButton("2nd", 19, 437),
        CalculatorButton("mode", 102, 437),
        CalculatorButton("del", 184, 437),
        CalculatorButton("down arrow", 266, 437),
        CalculatorButton("up arrow", 349, 437),

        // Row 3: Alpha (letter entry), X,T,theta,n (variable), Stat (statistics menu), left/right arrows.
        CalculatorButton("alpha", 19, 502),
        CalculatorButton("x,t,theta,n", 102, 502),
        CalculatorButton("stat", 184, 502),
        CalculatorButton("left arrow", 266, 502),
        CalculatorButton("right arrow", 349, 502),

        // Row 4: Math, Apps, Prgm, Vars menus, and Clear (clear entry).
        CalculatorButton("math", 19, 567),
        CalculatorButton("apps", 102, 567),
        CalculatorButton("prgm", 184, 567),
        CalculatorButton("vars", 266, 567),
        CalculatorButton("clear", 349, 567),

        // Row 5: Reciprocal, sine, cosine, tangent, exponentiation.
        CalculatorButton("x^-1", 19, 632),
        CalculatorButton("sin", 102, 632),
        CalculatorButton("cos", 184, 632),
        CalculatorButton("tan", 266, 632),
        CalculatorButton("^", 349, 632),

        // Row 6: Square, comma, open parenthesis, close parenthesis, division.
        CalculatorButton("x^2", 19, 698),
        CalculatorButton(",", 102, 698),
        CalculatorButton("(", 184, 698),
        CalculatorButton(")", 266, 698),
        CalculatorButton("/", 349, 698),

        // Row 7: Logarithm, digits 7–9, multiplication.
        CalculatorButton("log", 19, 763),
        CalculatorButton("7", 102, 763),
        CalculatorButton("8", 184, 763),
        CalculatorButton("9", 266, 763),
        CalculatorButton("*", 349, 763),

        // Row 8: Natural logarithm, digits 4–6, subtraction.
        CalculatorButton("ln", 19, 828),
        CalculatorButton("4", 102, 828),
        CalculatorButton("5", 184, 828),
        CalculatorButton("6", 266, 828),
        CalculatorButton("-", 349, 828),

        // Row 9: Store-to variable, digits 1–3, addition.
        CalculatorButton("sto->", 19, 893),
        CalculatorButton("1", 102, 893),
        CalculatorButton("2", 184, 893),
        CalculatorButton("3", 266, 893),
        CalculatorButton("+", 349, 893),

        // Row 10: Power on, digit 0, decimal point, negative sign, Enter (evaluate/confirm).
        CalculatorButton("on", 19, 958),
        CalculatorButton("0", 102, 958),
        CalculatorButton(".", 184, 958),
        CalculatorButton("(-)", 266, 958),
        CalculatorButton("enter", 349, 958)
    )

    init {
        visible = false
    }

    /** Restores the overlay to its inventory-relative default position. */
    fun resetPosition() {
        x = CalculatorPosition.xOrDefault(screenWidth, CALCULATOR_WIDTH, INVENTORY_WIDTH, GAP_FROM_INVENTORY)
        y = CalculatorPosition.yOrDefault(screenHeight, CALCULATOR_HEIGHT)
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Force nearest-neighbor filtering so the scaled pixel art remains sharp.
        Minecraft.getInstance().textureManager.getTexture(CALCULATOR_TEXTURE).setFilter(false, false)

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        guiGraphics.pose().scale(
            CALCULATOR_WIDTH.toFloat() / TEXTURE_WIDTH,
            CALCULATOR_HEIGHT.toFloat() / TEXTURE_HEIGHT,
            1f
        )
        guiGraphics.blit(
            CALCULATOR_TEXTURE,
            0,
            0,
            0f,
            0f,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        )
        guiGraphics.pose().popPose()

        renderModeIndicator(guiGraphics)
        when (lcdView) {
            LcdView.HOME -> renderDisplay(guiGraphics)
            LcdView.Y_EQUALS -> renderYEqualsDisplay(guiGraphics)
            LcdView.WINDOW -> renderWindowDisplay(guiGraphics)
            LcdView.MODE -> renderModeDisplay(guiGraphics)
            LcdView.ZOOM -> renderZoomDisplay(guiGraphics)
            LcdView.ZOOM_FACTORS -> renderZoomFactorsDisplay(guiGraphics)
            LcdView.GRAPH -> renderGraphDisplay(guiGraphics)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false

        if (button == 0 && isInDragHandle(mouseX, mouseY)) {
            dragging = true
            dragOffsetX = mouseX.toInt() - x
            dragOffsetY = mouseY.toInt() - y
            return true
        }

        val pressedCalculatorButton = calculatorButtons.firstOrNull { it.contains(mouseX, mouseY) }
        if (button == 0 && pressedCalculatorButton != null) {
            if (switchLcdView(pressedCalculatorButton.label)) return true

            if (lcdView == LcdView.Y_EQUALS) {
                handleYEqualsButton(pressedCalculatorButton.label)
                return true
            }
            if (lcdView == LcdView.WINDOW) {
                handleWindowButton(pressedCalculatorButton.label)
                return true
            }
            if (lcdView == LcdView.MODE) {
                handleModeButton(pressedCalculatorButton.label)
                return true
            }
            if (lcdView == LcdView.ZOOM) {
                handleZoomButton(pressedCalculatorButton.label)
                return true
            }
            if (lcdView == LcdView.ZOOM_FACTORS) {
                handleZoomFactorsButton(pressedCalculatorButton.label)
                return true
            }
            if (lcdView == LcdView.GRAPH) {
                handleGraphButton(pressedCalculatorButton.label)
                return true
            }
            if (historyNavigationPosition > 0) {
                when (pressedCalculatorButton.label) {
                    "up arrow" -> moveHistoryUp()
                    "down arrow" -> moveHistoryDown()
                    "enter" -> acceptHistorySelection()
                }
                return true
            }

            when (pressedCalculatorButton.label) {
                in "0".."9" -> CalculatorDisplayMemory.appendDigit(pressedCalculatorButton.label.single())
                "/", "*", "-", "+" ->
                    CalculatorDisplayMemory.appendOperator(pressedCalculatorButton.label.single())
                "." -> CalculatorDisplayMemory.appendDecimalPoint()
                "(-)" -> CalculatorDisplayMemory.toggleCurrentNumberSign()
                "x^2" -> CalculatorDisplayMemory.squareCurrentOperand()
                "x^-1" -> CalculatorDisplayMemory.reciprocalCurrentOperand()
                "^" -> CalculatorDisplayMemory.appendOperator('^')
                "(" -> CalculatorDisplayMemory.appendOpenParenthesis()
                ")" -> CalculatorDisplayMemory.appendCloseParenthesis()
                "sin", "cos", "tan", "log", "ln" ->
                    CalculatorDisplayMemory.appendFunction(pressedCalculatorButton.label)
                "," -> CalculatorDisplayMemory.appendComma()
                "x,t,theta,n" -> CalculatorDisplayMemory.appendXVariable()
                "sto->" -> CalculatorDisplayMemory.appendStoreOperator()
                "left arrow" -> CalculatorDisplayMemory.moveCursorLeft()
                "right arrow" -> CalculatorDisplayMemory.moveCursorRight()
                "up arrow" -> moveHistoryUp()
                "down arrow" -> moveHistoryDown()
                "del" -> CalculatorDisplayMemory.deleteAtCursor()
                "clear" -> CalculatorDisplayMemory.clearCurrent()
                "enter" -> CalculatorDisplayMemory.submit()
                // Other calculator buttons intentionally do not have behavior yet.
            }
            return true
        }

        // Consume clicks on non-button calculator artwork without implementing behavior.
        return true
    }

    /** Mirrors the calculator arrows, Enter, and menu hotkeys while a Zoom view has focus. */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!visible) return false

        val navigationLabel = when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> "left arrow"
            GLFW.GLFW_KEY_RIGHT -> "right arrow"
            GLFW.GLFW_KEY_UP -> "up arrow"
            GLFW.GLFW_KEY_DOWN -> "down arrow"
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "enter"
            else -> null
        }
        val digitLabel = when (keyCode) {
            in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> (keyCode - GLFW.GLFW_KEY_0).toString()
            in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_9 ->
                (keyCode - GLFW.GLFW_KEY_KP_0).toString()
            else -> null
        }

        when (lcdView) {
            LcdView.ZOOM -> {
                navigationLabel?.let {
                    handleZoomButton(it)
                    return true
                }
                digitLabel?.let {
                    handleZoomButton(it)
                    return true
                }
                if (keyCode in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_G && zoomTab == ZoomTab.ZOOM) {
                    activateZoomHotkey(('A'.code + keyCode - GLFW.GLFW_KEY_A).toChar().toString())
                    return true
                }
            }
            LcdView.ZOOM_FACTORS -> {
                navigationLabel?.let {
                    handleZoomFactorsButton(it)
                    return true
                }
                digitLabel?.let {
                    handleZoomFactorsButton(it)
                    return true
                }
            }
            LcdView.GRAPH -> if (zoomGraphState != null && navigationLabel != null) {
                handleGraphButton(navigationLabel)
                return true
            }
            else -> Unit
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double
    ): Boolean {
        if (!dragging || button != 0) return false

        x = mouseX.toInt() - dragOffsetX
        y = mouseY.toInt() - dragOffsetY
        CalculatorPosition.save(x, y)
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!dragging || button != 0) return false

        dragging = false
        return true
    }

    private fun isInDragHandle(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + DRAG_HANDLE_HEIGHT

    private fun CalculatorButton.contains(mouseX: Double, mouseY: Double): Boolean {
        val scaleX = width.toDouble() / TEXTURE_WIDTH
        val scaleY = height.toDouble() / TEXTURE_HEIGHT
        val left = x + sourceX * scaleX
        val top = y + sourceY * scaleY

        return mouseX >= left && mouseX < left + BUTTON_WIDTH * scaleX &&
            mouseY >= top && mouseY < top + BUTTON_HEIGHT * scaleY
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
        val indicatorText = ModeSettingsMemory.indicatorValues().joinToString(" ")
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
        val selectedLine = CalculatorDisplayMemory.historyLineFromNewest(historyNavigationPosition)
        val submitted = if (historyNavigationPosition > 0 && selectedLine != null) {
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
            if (historyNavigationPosition == 0) CalculatorDisplayMemory.discardOldestSubmitted()
        }

        val firstEntryIndex = if (historyNavigationPosition > 0) {
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
        if (historyNavigationPosition == 0) renderCursor(guiGraphics, currentEntry, left, lineY)
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

    private fun handleYEqualsButton(label: String) {
        when (label) {
            in "0".."9", ".", "/", "*", "-", "+", "^", "(", ")" -> YEqualsMemory.append(label)
            "x,t,theta,n" -> YEqualsMemory.append("X")
            "sin", "cos", "tan", "log", "ln" -> YEqualsMemory.append("$label(")
            "x^2" -> YEqualsMemory.append("^2")
            "x^-1" -> YEqualsMemory.append("^-1")
            "left arrow" -> YEqualsMemory.moveCursorLeft()
            "right arrow" -> YEqualsMemory.moveCursorRight()
            "up arrow" -> YEqualsMemory.selectPrevious()
            "down arrow", "enter" -> YEqualsMemory.selectNext()
            "del" -> YEqualsMemory.deleteAtCursor()
            "clear" -> YEqualsMemory.clearSelected()
            // The calculator's remaining menus remain intentionally inert.
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

    private fun handleWindowButton(label: String) {
        when (label) {
            in "0".."9", ".", "/", "*", "-", "+", "^", "(", ")" -> WindowSettingsMemory.append(label)
            "left arrow" -> WindowSettingsMemory.moveCursorLeft()
            "right arrow" -> WindowSettingsMemory.moveCursorRight()
            "up arrow" -> WindowSettingsMemory.selectPrevious()
            "down arrow", "enter" -> WindowSettingsMemory.selectNext()
            "del" -> WindowSettingsMemory.deleteAtCursor()
            "clear" -> WindowSettingsMemory.clearSelected()
            // The calculator's remaining menus remain intentionally inert.
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

    private fun handleModeButton(label: String) {
        when (label) {
            "left arrow" -> ModeSettingsMemory.selectPreviousOption()
            "right arrow" -> ModeSettingsMemory.selectNextOption()
            "up arrow" -> ModeSettingsMemory.selectPreviousCategory()
            "down arrow", "enter" -> ModeSettingsMemory.selectNextCategory()
            // Mode values are selected only with the arrow keys.
        }
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

        drawZoomTab(guiGraphics, "ZOOM", left, top, zoomTab == ZoomTab.ZOOM)
        drawZoomTab(guiGraphics, "MEMORY", memoryLeft, top, zoomTab == ZoomTab.MEMORY)

        val options = currentZoomOptions()
        val selectedIndex = currentZoomSelectedIndex()
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

    private fun currentZoomOptions(): List<ZoomMenuOption> =
        if (zoomTab == ZoomTab.ZOOM) zoomOptions else memoryOptions

    private fun currentZoomSelectedIndex(): Int =
        if (zoomTab == ZoomTab.ZOOM) zoomSelectedIndex else memorySelectedIndex

    private fun setCurrentZoomSelectedIndex(index: Int) {
        if (zoomTab == ZoomTab.ZOOM) {
            zoomSelectedIndex = index.coerceIn(zoomOptions.indices)
        } else {
            memorySelectedIndex = index.coerceIn(memoryOptions.indices)
        }
    }

    private fun handleZoomButton(label: String) {
        if (label == "alpha") {
            zoomAlphaArmed = !zoomAlphaArmed
            return
        }

        val alphaHotkey = if (zoomAlphaArmed) {
            mapOf(
                "math" to "A",
                "apps" to "B",
                "prgm" to "C",
                "x^-1" to "D",
                "sin" to "E",
                "cos" to "F",
                "tan" to "G"
            )[label]
        } else {
            null
        }
        if (alphaHotkey != null) {
            zoomAlphaArmed = false
            activateZoomHotkey(alphaHotkey)
            return
        }

        when (label) {
            "left arrow" -> {
                zoomTab = ZoomTab.ZOOM
                zoomAlphaArmed = false
            }
            "right arrow" -> {
                zoomTab = ZoomTab.MEMORY
                zoomAlphaArmed = false
            }
            "up arrow" -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() - 1)
            "down arrow" -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() + 1)
            "enter" -> activateCurrentZoomOption()
            in "0".."9" -> activateZoomHotkey(label)
        }
    }

    private fun activateZoomHotkey(hotkey: String) {
        val optionIndex = currentZoomOptions().indexOfFirst { it.hotkey == hotkey }
        if (optionIndex < 0) return
        setCurrentZoomSelectedIndex(optionIndex)
        activateCurrentZoomOption()
    }

    private fun activateCurrentZoomOption() {
        if (zoomTab == ZoomTab.ZOOM) {
            activateZoomOption(zoomSelectedIndex)
        } else {
            activateMemoryOption(memorySelectedIndex)
        }
    }

    private fun activateZoomOption(index: Int) {
        when (index) {
            0 -> beginZoomGraphOperation(ZoomGraphOperation.BOX)
            1 -> beginZoomGraphOperation(ZoomGraphOperation.IN)
            2 -> beginZoomGraphOperation(ZoomGraphOperation.OUT)
            3 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-4.7", "4.7", "1", "-3.1", "3.1", "1")
            }
            4 -> applySquareWindow()
            5 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-10", "10", "1", "-10", "10", "1")
            }
            6 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow(
                    "-6.28318530718",
                    "6.28318530718",
                    "1.57079632679",
                    "-4",
                    "4",
                    "1"
                )
            }
            7 -> applyIntegerWindow()
            8, 9 -> applyZoomFit()
            10 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("0", "10", "1", "0", "10", "1")
            }
            in 11..16 -> {
                val denominator = listOf(2, 3, 4, 5, 8, 10)[index - 11]
                applyFractionalWindow(denominator)
            }
        }
    }

    private fun activateMemoryOption(index: Int) {
        when (index) {
            0 -> {
                if (ZoomMemory.restorePrevious()) {
                    lcdView = LcdView.GRAPH
                    zoomGraphState = null
                }
            }
            1 -> {
                ZoomMemory.storeCurrent()
                lcdView = LcdView.GRAPH
            }
            2 -> {
                if (ZoomMemory.recallStored()) {
                    lcdView = LcdView.GRAPH
                    zoomGraphState = null
                }
            }
            3 -> {
                factorSelectedIndex = ZoomMemory.selectedDenominatorIndex()
                lcdView = LcdView.ZOOM_FACTORS
            }
        }
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
            if (index == factorSelectedIndex) {
                renderInvertedRow(guiGraphics, rowText, left, lineY)
            } else {
                drawDisplayText(guiGraphics, rowText, left, lineY)
            }
        }
    }

    private fun handleZoomFactorsButton(label: String) {
        when (label) {
            "up arrow" ->
                factorSelectedIndex = (factorSelectedIndex - 1).coerceAtLeast(0)
            "down arrow" ->
                factorSelectedIndex =
                    (factorSelectedIndex + 1).coerceAtMost(ZoomMemory.denominators().lastIndex)
            "enter" -> {
                ZoomMemory.selectDenominator(factorSelectedIndex)
                lcdView = LcdView.ZOOM
                zoomTab = ZoomTab.MEMORY
            }
            in "1".."6" -> {
                factorSelectedIndex = label.toInt() - 1
                ZoomMemory.selectDenominator(factorSelectedIndex)
                lcdView = LcdView.ZOOM
                zoomTab = ZoomTab.MEMORY
            }
            "zoom" -> lcdView = LcdView.ZOOM
        }
    }

    private fun applyImmediateZoom(change: () -> Unit) {
        ZoomMemory.rememberPrevious(WindowSettingsMemory.snapshot())
        change()
        traceState = null
        zoomGraphState = null
        lcdView = LcdView.GRAPH
    }

    private fun applySquareWindow() {
        val graphWindow = readGraphWindow() ?: return
        val graphAspect = graphDisplayAspect()
        val xCenter = (graphWindow.xMin + graphWindow.xMax) / 2.0
        val yCenter = (graphWindow.yMin + graphWindow.yMax) / 2.0
        val xSpan = graphWindow.xMax - graphWindow.xMin
        val ySpan = graphWindow.yMax - graphWindow.yMin
        val squareXSpan = max(xSpan, ySpan * graphAspect)
        val squareYSpan = max(ySpan, xSpan / graphAspect)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(xCenter - squareXSpan / 2.0),
                formatWindowValue(xCenter + squareXSpan / 2.0),
                WindowSettingsMemory.value(2),
                formatWindowValue(yCenter - squareYSpan / 2.0),
                formatWindowValue(yCenter + squareYSpan / 2.0),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyIntegerWindow() {
        val graphWindow = readGraphWindow() ?: return
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                floor(graphWindow.xMin).toInt().toString(),
                ceil(graphWindow.xMax).toInt().toString(),
                "1",
                floor(graphWindow.yMin).toInt().toString(),
                ceil(graphWindow.yMax).toInt().toString(),
                "1",
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyFractionalWindow(denominator: Int) {
        val graphWindow = readGraphWindow() ?: return
        val spacing = formatWindowValue(1.0 / denominator)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(graphWindow.xMin),
                formatWindowValue(graphWindow.xMax),
                spacing,
                formatWindowValue(graphWindow.yMin),
                formatWindowValue(graphWindow.yMax),
                spacing,
                WindowSettingsMemory.value(6)
            )
        }
    }

    /** Fits all currently graphed Y= functions vertically while retaining the current X range. */
    private fun applyZoomFit() {
        val graphWindow = readGraphWindow() ?: return
        val expressions = YEqualsMemory.subscripts.indices
            .map(YEqualsMemory::equation)
            .filter(String::isNotEmpty)
        if (expressions.isEmpty()) return

        val values = mutableListOf<Double>()
        repeat(101) { sampleIndex ->
            val graphX =
                graphWindow.xMin + sampleIndex / 100.0 * (graphWindow.xMax - graphWindow.xMin)
            expressions.forEach { expression ->
                CalculatorDisplayMemory.evaluateForGraph(expression, graphX)
                    ?.takeIf(Double::isFinite)
                    ?.let(values::add)
            }
        }
        if (values.isEmpty()) return
        var yMin = values.minOrNull() ?: return
        var yMax = values.maxOrNull() ?: return
        val padding = if (yMin == yMax) max(1.0, abs(yMin) * 0.1) else (yMax - yMin) * 0.05
        yMin -= padding
        yMax += padding
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                WindowSettingsMemory.value(0),
                WindowSettingsMemory.value(1),
                WindowSettingsMemory.value(2),
                formatWindowValue(yMin),
                formatWindowValue(yMax),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun beginZoomGraphOperation(operation: ZoomGraphOperation) {
        val graphWindow = readGraphWindow() ?: return
        traceState = null
        zoomGraphState = ZoomGraphState(
            operation,
            (graphWindow.xMin + graphWindow.xMax) / 2.0,
            (graphWindow.yMin + graphWindow.yMax) / 2.0
        )
        lcdView = LcdView.GRAPH
    }

    private fun graphDisplayAspect(): Double {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val graphWidth = (DISPLAY_RIGHT - DISPLAY_LEFT - DISPLAY_PADDING * 2) * scaleX
        val graphHeight = (DISPLAY_BOTTOM - DISPLAY_TOP - DISPLAY_PADDING * 2) * scaleY
        return (graphWidth / graphHeight).toDouble()
    }

    private fun formatWindowValue(value: Double): String =
        java.math.BigDecimal.valueOf(value)
            .round(java.math.MathContext(12))
            .stripTrailingZeros()
            .toPlainString()

    /** Y=, Window, Zoom, Mode, Graph, and Trace are direct LCD view selectors from every view. */
    private fun switchLcdView(label: String): Boolean {
        lcdView = when (label) {
            "y=" -> LcdView.Y_EQUALS
            "window" -> LcdView.WINDOW
            "zoom" -> LcdView.ZOOM
            "mode" -> LcdView.MODE
            "graph" -> LcdView.GRAPH
            "trace" -> {
                beginTrace()
                LcdView.GRAPH
            }
            else -> return false
        }
        if (label != "trace") traceState = null
        zoomGraphState = null
        zoomAlphaArmed = false
        historyNavigationPosition = 0
        return true
    }

    /** Starts tracing Y1 at the centre of the current graph window. */
    private fun beginTrace() {
        val graphWindow = readGraphWindow()
        traceState = graphWindow?.let { TraceState(0, (it.xMin + it.xMax) / 2.0) }
    }

    private fun handleGraphButton(label: String) {
        zoomGraphState?.let { zoom ->
            when (label) {
                "left arrow" -> moveZoomGraphCursor(zoom, -1, 0)
                "right arrow" -> moveZoomGraphCursor(zoom, 1, 0)
                "up arrow" -> moveZoomGraphCursor(zoom, 0, 1)
                "down arrow" -> moveZoomGraphCursor(zoom, 0, -1)
                "enter" -> acceptZoomGraphSelection(zoom)
            }
            return
        }

        val trace = traceState ?: return
        when (label) {
            "left arrow" -> moveTrace(trace, -1)
            "right arrow" -> moveTrace(trace, 1)
            "up arrow" -> moveTraceFunction(trace, -1)
            "down arrow" -> moveTraceFunction(trace, 1)
        }
    }

    /** Selects the next populated Y= entry in the requested direction without wrapping. */
    private fun moveTraceFunction(trace: TraceState, direction: Int) {
        var candidate = trace.functionIndex + direction
        while (candidate in YEqualsMemory.subscripts.indices) {
            if (YEqualsMemory.equation(candidate).isNotEmpty()) {
                trace.functionIndex = candidate
                return
            }
            candidate += direction
        }
    }

    /** Advances the traced X coordinate by the current Window TraceStep value. */
    private fun moveTrace(trace: TraceState, direction: Int) {
        val step = CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(8), 0.0)
        if (step != null && step.isFinite() && step > 0.0) trace.x += direction * step
    }

    private fun moveZoomGraphCursor(zoom: ZoomGraphState, xDirection: Int, yDirection: Int) {
        val graphWindow = readGraphWindow() ?: return
        val xStep = (graphWindow.xMax - graphWindow.xMin) / 40.0
        val yStep = (graphWindow.yMax - graphWindow.yMin) / 26.0
        zoom.x = (zoom.x + xDirection * xStep).coerceIn(graphWindow.xMin, graphWindow.xMax)
        zoom.y = (zoom.y + yDirection * yStep).coerceIn(graphWindow.yMin, graphWindow.yMax)
    }

    private fun acceptZoomGraphSelection(zoom: ZoomGraphState) {
        val graphWindow = readGraphWindow() ?: return
        if (zoom.operation == ZoomGraphOperation.BOX && zoom.anchorX == null) {
            zoom.anchorX = zoom.x
            zoom.anchorY = zoom.y
            return
        }

        val newBounds = when (zoom.operation) {
            ZoomGraphOperation.BOX -> {
                val anchorX = zoom.anchorX ?: return
                val anchorY = zoom.anchorY ?: return
                val xMin = min(anchorX, zoom.x)
                val xMax = max(anchorX, zoom.x)
                val yMin = min(anchorY, zoom.y)
                val yMax = max(anchorY, zoom.y)
                if (xMin == xMax || yMin == yMax) return
                listOf(xMin, xMax, yMin, yMax)
            }
            ZoomGraphOperation.IN, ZoomGraphOperation.OUT -> {
                val factor = ZoomMemory.selectedDenominator().toDouble()
                val directionFactor =
                    if (zoom.operation == ZoomGraphOperation.IN) 1.0 / factor else factor
                val halfWidth = (graphWindow.xMax - graphWindow.xMin) * directionFactor / 2.0
                val halfHeight = (graphWindow.yMax - graphWindow.yMin) * directionFactor / 2.0
                listOf(
                    zoom.x - halfWidth,
                    zoom.x + halfWidth,
                    zoom.y - halfHeight,
                    zoom.y + halfHeight
                )
            }
        }

        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(newBounds[0]),
                formatWindowValue(newBounds[1]),
                WindowSettingsMemory.value(2),
                formatWindowValue(newBounds[2]),
                formatWindowValue(newBounds[3]),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    /** Plots all non-empty Y= expressions using the current persistent Window settings. */
    private fun renderGraphDisplay(guiGraphics: GuiGraphics) {
        val scaleX = width.toFloat() / TEXTURE_WIDTH
        val scaleY = height.toFloat() / TEXTURE_HEIGHT
        val left = (x + (DISPLAY_LEFT + DISPLAY_PADDING) * scaleX).toInt()
        val right = (x + (DISPLAY_RIGHT - DISPLAY_PADDING) * scaleX).toInt() - 1
        val displayTop = (y + (DISPLAY_TOP + DISPLAY_PADDING) * scaleY).toInt()
        val displayBottom = (y + (DISPLAY_BOTTOM - DISPLAY_PADDING) * scaleY).toInt() - 1
        val graphWindow = readGraphWindow()

        if (graphWindow == null) {
            drawDisplayText(guiGraphics, "INVALID WINDOW", left, displayTop)
            return
        }

        val tracing = traceState != null
        // Trace labels occupy one LCD line above and below the graph, keeping both unobstructed.
        val top = displayTop + if (tracing) DISPLAY_LINE_HEIGHT else 0
        val bottom = displayBottom - if (tracing) DISPLAY_LINE_HEIGHT else 0
        if (tracing) renderTraceLabels(guiGraphics, left, right, displayTop, displayBottom, graphWindow)
        renderGraphAxes(guiGraphics, left, right, top, bottom, graphWindow)
        repeat(YEqualsMemory.subscripts.size) { equationIndex ->
            val expression = YEqualsMemory.equation(equationIndex)
            if (expression.isNotEmpty()) {
                renderGraphFunction(
                    guiGraphics,
                    expression,
                    YEqualsMemory.colors[equationIndex],
                    left,
                    right,
                    top,
                    bottom,
                    graphWindow
                )
            }
        }
        renderGraphTrace(guiGraphics, left, right, top, bottom, graphWindow)
        renderZoomGraphSelection(guiGraphics, left, right, top, bottom, graphWindow)
    }

    private fun renderZoomGraphSelection(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val zoom = zoomGraphState ?: return
        val cursorX = graphXToPixel(zoom.x, left, right, graphWindow)
        val cursorY = graphYToPixel(zoom.y, top, bottom, graphWindow)

        if (zoom.operation == ZoomGraphOperation.BOX && zoom.anchorX != null && zoom.anchorY != null) {
            val anchorX = graphXToPixel(zoom.anchorX!!, left, right, graphWindow)
            val anchorY = graphYToPixel(zoom.anchorY!!, top, bottom, graphWindow)
            val boxLeft = min(anchorX, cursorX)
            val boxRight = max(anchorX, cursorX)
            val boxTop = min(anchorY, cursorY)
            val boxBottom = max(anchorY, cursorY)
            guiGraphics.fill(boxLeft, boxTop, boxRight + 1, boxTop + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxLeft, boxBottom, boxRight + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxLeft, boxTop, boxLeft + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(boxRight, boxTop, boxRight + 1, boxBottom + 1, DISPLAY_TEXT_COLOR)
        }

        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            guiGraphics.fill(cursorX - 3, cursorY, cursorX + 4, cursorY + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(cursorX, cursorY - 3, cursorX + 1, cursorY + 4, DISPLAY_TEXT_COLOR)
        }
    }

    /** Renders the selected equation and its current coordinate outside the trace graph area. */
    private fun renderTraceLabels(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        displayTop: Int,
        displayBottom: Int,
        graphWindow: GraphWindow
    ) {
        val trace = traceState ?: return
        val color = YEqualsMemory.colors[trace.functionIndex]
        val functionLabel = "Y${YEqualsMemory.subscripts[trace.functionIndex]}=${YEqualsMemory.equation(trace.functionIndex)}"
        drawDisplayText(guiGraphics, functionLabel, left, displayTop, color)

        val xLabel = "X=${formatTraceValue(trace.x)}"
        val footerY = displayBottom - DISPLAY_LINE_HEIGHT + TRACE_FOOTER_OFFSET
        drawDisplayText(guiGraphics, xLabel, left, footerY, color)
        val yLabel = CalculatorDisplayMemory.evaluateForGraph(
            YEqualsMemory.equation(trace.functionIndex),
            trace.x
        )?.let { "Y=${formatTraceValue(it)}" } ?: "Y=ERR"
        val yAxisX = if (0.0 in graphWindow.xMin..graphWindow.xMax) {
            graphXToPixel(0.0, left, right, graphWindow)
        } else {
            (left + right) / 2
        }
        drawDisplayText(guiGraphics, yLabel, yAxisX, footerY, color)
    }

    private fun formatTraceValue(value: Double): String = ModeSettingsMemory.formatNumber(value)

    /** Draws the blinking trace marker at the evaluated coordinate of the selected Y= function. */
    private fun renderGraphTrace(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val trace = traceState ?: return
        if ((System.currentTimeMillis() / 500L) % 2L != 0L) return

        val expression = YEqualsMemory.equation(trace.functionIndex)
        val graphY = CalculatorDisplayMemory.evaluateForGraph(expression, trace.x) ?: return
        if (!graphY.isFinite() || trace.x !in graphWindow.xMin..graphWindow.xMax ||
            graphY !in graphWindow.yMin..graphWindow.yMax
        ) return

        val markerX = graphXToPixel(trace.x, left, right, graphWindow)
        val markerY = graphYToPixel(graphY, top, bottom, graphWindow)
        // Draw a pixel X centred directly on the plotted coordinate. A font glyph has its own
        // baseline and side bearings, which makes it look displaced from the graph point.
        for (offset in -2..2) {
            guiGraphics.fill(markerX + offset, markerY + offset, markerX + offset + 1, markerY + offset + 1, DISPLAY_TEXT_COLOR)
            guiGraphics.fill(markerX + offset, markerY - offset, markerX + offset + 1, markerY - offset + 1, DISPLAY_TEXT_COLOR)
        }
    }

    private fun readGraphWindow(): GraphWindow? {
        val numbers = (0..6).map { settingIndex ->
            CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(settingIndex), 0.0)
                ?: return null
        }
        val xMin = numbers[0]
        val xMax = numbers[1]
        val xScale = numbers[2]
        val yMin = numbers[3]
        val yMax = numbers[4]
        val yScale = numbers[5]
        if (xMin >= xMax || yMin >= yMax || xScale <= 0.0 || yScale <= 0.0 || numbers[6] <= 0.0) {
            return null
        }

        return GraphWindow(
            xMin,
            xMax,
            xScale,
            yMin,
            yMax,
            yScale,
            numbers[6].roundToInt().coerceAtLeast(1)
        )
    }

    private fun renderGraphAxes(
        guiGraphics: GuiGraphics,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val xAxisY = if (0.0 in graphWindow.yMin..graphWindow.yMax) {
            graphYToPixel(0.0, top, bottom, graphWindow)
        } else {
            null
        }
        val yAxisX = if (0.0 in graphWindow.xMin..graphWindow.xMax) {
            graphXToPixel(0.0, left, right, graphWindow)
        } else {
            null
        }

        xAxisY?.let { guiGraphics.fill(left, it, right + 1, it + 1, GRAPH_AXIS_COLOR) }
        yAxisX?.let { guiGraphics.fill(it, top, it + 1, bottom + 1, GRAPH_AXIS_COLOR) }

        if (xAxisY != null) {
            forEachTick(graphWindow.xMin, graphWindow.xMax, graphWindow.xScale) { tick ->
                val tickX = graphXToPixel(tick, left, right, graphWindow)
                guiGraphics.fill(tickX, xAxisY - 1, tickX + 1, xAxisY + 2, GRAPH_AXIS_COLOR)
            }
        }
        if (yAxisX != null) {
            forEachTick(graphWindow.yMin, graphWindow.yMax, graphWindow.yScale) { tick ->
                val tickY = graphYToPixel(tick, top, bottom, graphWindow)
                guiGraphics.fill(yAxisX - 1, tickY, yAxisX + 2, tickY + 1, GRAPH_AXIS_COLOR)
            }
        }
    }

    private fun renderGraphFunction(
        guiGraphics: GuiGraphics,
        expression: String,
        color: Int,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val plotWidth = (right - left).coerceAtLeast(1)
        val sampleColumns = (0..plotWidth step graphWindow.xResolution.coerceAtMost(plotWidth)).toMutableList()
        if (sampleColumns.lastOrNull() != plotWidth) sampleColumns += plotWidth

        var previous: GraphSample? = null
        sampleColumns.forEach { column ->
            val graphX = graphWindow.xMin + column.toDouble() / plotWidth * (graphWindow.xMax - graphWindow.xMin)
            val graphY = CalculatorDisplayMemory.evaluateForGraph(expression, graphX)
            val current = graphY?.let { GraphSample(left + column, it) }

            if (current != null) {
                previous?.let { prior ->
                    // Large jumps generally indicate an asymptote; do not join across it.
                    if (abs(current.y - prior.y) <= (graphWindow.yMax - graphWindow.yMin) * 2.0) {
                        drawGraphSegment(guiGraphics, prior, current, color, top, bottom, graphWindow)
                    }
                }
                if (graphY in graphWindow.yMin..graphWindow.yMax) {
                    val pixelY = graphYToPixel(graphY, top, bottom, graphWindow)
                    guiGraphics.fill(current.pixelX, pixelY, current.pixelX + 1, pixelY + 1, color)
                }
            }
            previous = current
        }
    }

    /** Interpolates between sampled columns while clipping every plotted pixel to the LCD. */
    private fun drawGraphSegment(
        guiGraphics: GuiGraphics,
        start: GraphSample,
        end: GraphSample,
        color: Int,
        top: Int,
        bottom: Int,
        graphWindow: GraphWindow
    ) {
        val pixelDistance = (end.pixelX - start.pixelX).coerceAtLeast(1)
        for (pixelX in start.pixelX..end.pixelX) {
            val progress = (pixelX - start.pixelX).toDouble() / pixelDistance
            val graphY = start.y + (end.y - start.y) * progress
            if (graphY in graphWindow.yMin..graphWindow.yMax) {
                val pixelY = graphYToPixel(graphY, top, bottom, graphWindow)
                guiGraphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color)
            }
        }
    }

    private fun graphXToPixel(value: Double, left: Int, right: Int, graphWindow: GraphWindow): Int =
        (left + (value - graphWindow.xMin) / (graphWindow.xMax - graphWindow.xMin) * (right - left))
            .roundToInt()

    private fun graphYToPixel(value: Double, top: Int, bottom: Int, graphWindow: GraphWindow): Int =
        (bottom - (value - graphWindow.yMin) / (graphWindow.yMax - graphWindow.yMin) * (bottom - top))
            .roundToInt()

    private fun forEachTick(minimum: Double, maximum: Double, spacing: Double, action: (Double) -> Unit) {
        val firstTickValue = ceil(minimum / spacing)
        val lastTickValue = floor(maximum / spacing)
        val tickCount = lastTickValue - firstTickValue
        if (!firstTickValue.isFinite() || !lastTickValue.isFinite() || tickCount !in 0.0..1_000.0) return
        val firstTick = firstTickValue.toLong()
        val lastTick = lastTickValue.toLong()
        for (multiple in firstTick..lastTick) action(multiple * spacing)
    }

    private fun moveHistoryUp() {
        if (CalculatorDisplayMemory.historyLineFromNewest(historyNavigationPosition + 1) != null) {
            historyNavigationPosition++
        }
    }

    private fun moveHistoryDown() {
        if (historyNavigationPosition > 0) historyNavigationPosition--
    }

    private fun acceptHistorySelection() {
        CalculatorDisplayMemory.historyLineFromNewest(historyNavigationPosition)?.let { selected ->
            if (selected.text.startsWith("Error:")) return
            CalculatorDisplayMemory.appendRecalledHistory(selected.text)
            historyNavigationPosition = 0
        }
    }

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

    private enum class LcdView {
        HOME,
        Y_EQUALS,
        WINDOW,
        MODE,
        ZOOM,
        ZOOM_FACTORS,
        GRAPH
    }

    private enum class ZoomTab {
        ZOOM,
        MEMORY
    }

    private enum class ZoomGraphOperation {
        BOX,
        IN,
        OUT
    }

    private data class ZoomMenuOption(val hotkey: String, val label: String)

    private data class ZoomGraphState(
        val operation: ZoomGraphOperation,
        var x: Double,
        var y: Double,
        var anchorX: Double? = null,
        var anchorY: Double? = null
    )

    private data class GraphWindow(
        val xMin: Double,
        val xMax: Double,
        val xScale: Double,
        val yMin: Double,
        val yMax: Double,
        val yScale: Double,
        val xResolution: Int
    )

    private data class GraphSample(val pixelX: Int, val y: Double)

    private data class TraceState(var functionIndex: Int, var x: Double)

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

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit
}
