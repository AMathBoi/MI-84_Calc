package net.amathboi.mi84mod.client

import net.amathboi.mi84mod.client.calculator.CalculatorWidget
import net.amathboi.mi84mod.client.calculator.CalculatorPosition
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

object Mi84_calcClient : ClientModInitializer {
    private lateinit var resetCalculatorPositionKey: KeyMapping
    private var activeCalculator: CalculatorWidget? = null

    override fun onInitializeClient() {
        resetCalculatorPositionKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.mi84_calc.reset_calculator_position",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "key.categories.mi84_calc"
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register {
            while (resetCalculatorPositionKey.consumeClick()) {
                resetCalculatorPosition()
            }
        }

        // listens for new gui screen being opened
        ScreenEvents.AFTER_INIT.register { client, screen, i, j ->
            // only add button to inventory screen
            if (screen is InventoryScreen) {
                ScreenKeyboardEvents.allowKeyPress(screen).register { _, key, scancode, _ ->
                    if (resetCalculatorPositionKey.matches(key, scancode)) {
                        resetCalculatorPosition()
                        false
                    } else {
                        true
                    }
                }

                val buttonSize = 20
                val padding = 10
                val calculator = CalculatorWidget(screen.width, screen.height)
                activeCalculator = calculator

                // top-left position for the button
                val xPos = padding
                val yPos = screen.height - buttonSize - padding

                // Button toggles the calculator overlay within the inventory screen.
                val calcButton =
                        Button.builder(
                            Component.literal("X"),
                            { _ -> calculator.visible = !calculator.visible }
                        )
                        .build()

                // sets position and dimensions
                calcButton.x = xPos
                calcButton.y = yPos
                calcButton.width = buttonSize
                calcButton.height = buttonSize

                // adds the button to the screen
                Screens.getButtons(screen).add(calcButton)

                // Add after the inventory button so the calculator renders above the inventory.
                Screens.getButtons(screen).add(calculator)
            }
        }
    }

    private fun resetCalculatorPosition() {
        CalculatorPosition.reset()
        activeCalculator?.resetPosition()
    }
}
