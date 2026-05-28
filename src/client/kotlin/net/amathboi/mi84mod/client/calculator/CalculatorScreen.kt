package net.amathboi.mi84mod.client.calculator

import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class CalculatorScreen : Screen(Component.literal("MI-84 Calculator")) {

    // calculator dimensions
    val displayWidth = 160
    val displayHeight = 30

    // center the calculator on screen
    //TODO have user be able to move this
    val centerX = width / 2
    val centerY = height / 2

    // declare the expression
    var expression = ""

    override fun init() {
        super.init()

        // add calculator number buttons
        //TODO add full list
        val buttonSize = 30
        val gap = 4
        val cols = 4
        val totalWidth = cols * buttonSize + (cols - 1) * gap
        val startX = centerX + totalWidth / 2
        var startY = centerY + displayHeight / 2 + 10

        // layout array
        val labels = arrayOf("7", "8", "9", "/",
                             "4", "5", "6", "*",
                             "1", "2", "3", "-",
                             "C", "0", "=", "+")

        // for each element
        for (label in labels) {
            // gets x and y for each button
            // TODO easier with 2D array?
            val row = labels.indexOf(label) / cols
            val col = labels.indexOf(label) % cols
            val x = startX + col * (buttonSize + gap)
            val y = startY + row * (buttonSize + gap)

            // make each button
            val button =
                Button.builder(
                    Component.literal(label),
                    { _ ->
                        onButtonClick(label)
                    })
                    .build()

            // set each parameter
            button.x = x
            button.y = y
            button.width = buttonSize
            button.height = buttonSize

            // add the button to the screen
            Screens.getButtons(this).add(button)
        }
    }

    private fun onButtonClick(label: String) {
        // button -> action
        when (label) {
            // c clears
            "C" -> expression = ""
            // = evals
            "=" -> { /* TODO: evaluate */ }
            // add it to the buffer
            else -> expression += label
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // draw background
        guiGraphics.fill(0, 0, width, height, 0xFF181818.toInt())

        // draw display area (centered around centerX)
        guiGraphics.fill(centerX - displayWidth / 2,
             centerY,
             centerX + displayWidth / 2,
             centerY + displayHeight,
             0xFF333333.toInt())

        // draw text on display (centered in the display area)
        guiGraphics.drawCenteredString(font, expression,
            centerX,
            centerY + displayHeight / 2,
            0xFFFFFFFF.toInt())

        super.render(guiGraphics, mouseX, mouseY, delta)
    }
    // don't pause the game
    override fun isPauseScreen() = false
}
