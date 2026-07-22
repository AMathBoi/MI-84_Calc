package net.amathboi.mi84mod.client.calculator

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

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
        // Keep the LCD font proportional to the calculator's new half-size footprint.
        private const val DISPLAY_TEXT_SCALE = 0.5f
        private const val DISPLAY_LINE_HEIGHT = 6
        private const val DISPLAY_TEXT_COLOR = 0xFF1F1F1F.toInt()
        private const val DISPLAY_DIVIDER_COLOR = 0xFF555555.toInt()
        private const val DISPLAY_HIGHLIGHT_COLOR = 0xFF9A9A9A.toInt()

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

        renderDisplay(guiGraphics)
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
            DISPLAY_TEXT_COLOR
        )
    }

    /** Draws Minecraft's built-in font at half size without requiring a separate font texture. */
    private fun drawDisplayText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        pose.scale(DISPLAY_TEXT_SCALE, DISPLAY_TEXT_SCALE, 1f)
        guiGraphics.drawString(Minecraft.getInstance().font, text, 0, 0, DISPLAY_TEXT_COLOR, false)
        pose.popPose()
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit
}
