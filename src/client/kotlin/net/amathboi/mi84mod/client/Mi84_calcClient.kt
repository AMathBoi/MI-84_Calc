package net.amathboi.mi84mod.client

import net.amathboi.mi84mod.client.calculator.CalculatorScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component

object Mi84_calcClient : ClientModInitializer {
    override fun onInitializeClient() {
        // listens for new gui screen being opened
        ScreenEvents.AFTER_INIT.register { client, screen, i, j ->
            // only add button to inventory screen
            if (screen is InventoryScreen) {
                val buttonSize = 20
                val padding = 10

                // top-left position for the button
                val xPos = padding
                val yPos = screen.height - buttonSize - padding

                // button opens the calculator screen
                val calcButton =
                        Button.builder(
                            Component.literal("X"),
                            { _ ->
                                //add widget logic here

                                // testing
                                client.player?.sendSystemMessage(Component.literal("Clicked!"))
                            }
                        )
                        .build()

                // sets position and dimensions
                calcButton.x = xPos
                calcButton.y = yPos
                calcButton.width = buttonSize
                calcButton.height = buttonSize

                // adds the button to the screen
                Screens.getButtons(screen).add(calcButton)
            }
        }
    }
}
