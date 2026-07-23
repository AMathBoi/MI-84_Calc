package net.amathboi.mi84mod.client.calculator

import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKeyLayout
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** Thin Minecraft adapter for calculator rendering, dragging, hit-testing, and input forwarding. */
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
    private val controller = CalculatorController()
    private val renderer = CalculatorRenderer(controller)
    private var dragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    init {
        visible = false
    }

    fun resetPosition() {
        x = CalculatorPosition.xOrDefault(
            screenWidth,
            CALCULATOR_WIDTH,
            INVENTORY_WIDTH,
            GAP_FROM_INVENTORY
        )
        y = CalculatorPosition.yOrDefault(screenHeight, CALCULATOR_HEIGHT)
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        Minecraft.getInstance().textureManager.getTexture(CALCULATOR_TEXTURE).setFilter(false, false)
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat(), 0f)
        guiGraphics.pose().scale(
            width.toFloat() / CalculatorKeyLayout.SOURCE_WIDTH,
            height.toFloat() / CalculatorKeyLayout.SOURCE_HEIGHT,
            1f
        )
        guiGraphics.blit(
            CALCULATOR_TEXTURE,
            0,
            0,
            0f,
            0f,
            CalculatorKeyLayout.SOURCE_WIDTH,
            CalculatorKeyLayout.SOURCE_HEIGHT,
            CalculatorKeyLayout.SOURCE_WIDTH,
            CalculatorKeyLayout.SOURCE_HEIGHT
        )
        guiGraphics.pose().popPose()
        renderer.render(guiGraphics, x, y, width, height)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false
        if (button == 0 && isInDragHandle(mouseX, mouseY)) {
            dragging = true
            dragOffsetX = mouseX.toInt() - x
            dragOffsetY = mouseY.toInt() - y
            return true
        }

        val pressedKey = CalculatorKeyLayout.hitTest(mouseX, mouseY, x, y, width, height)
        if (button == 0 && pressedKey != null) {
            controller.dispatch(
                CalculatorInputEvent(pressedKey),
                renderer.graphDisplayAspect(width, height)
            )
        }
        // Consume clicks anywhere on the calculator artwork.
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

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) = Unit

    companion object {
        private val CALCULATOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mi84_calc", "textures/calculator/calc.png")
        private const val CALCULATOR_WIDTH = 110
        private const val CALCULATOR_HEIGHT = 256
        private const val DRAG_HANDLE_HEIGHT = 44
        private const val INVENTORY_WIDTH = 176
        private const val GAP_FROM_INVENTORY = 12
    }
}
