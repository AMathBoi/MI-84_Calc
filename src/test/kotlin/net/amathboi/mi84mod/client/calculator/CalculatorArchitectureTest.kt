package net.amathboi.mi84mod.client.calculator

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.controller.DispatchResult
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.input.CalculatorKeyBindings
import net.amathboi.mi84mod.client.calculator.input.CalculatorKeyLayout
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuId
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuTab

class CalculatorArchitectureTest {
    @BeforeTest
    fun configureFabric() {
        TestFabricEnvironment.configure()
    }

    @Test
    fun physicalLayoutContainsEveryKeyExactlyOnce() {
        assertEquals(CalculatorKey.entries.size, CalculatorKeyLayout.keys.size)
        assertEquals(CalculatorKey.entries.toSet(), CalculatorKeyLayout.keys.map { it.key }.toSet())
    }

    @Test
    fun physicalHitboxesStayInsideTextureAndDoNotOverlap() {
        CalculatorKeyLayout.keys.forEach { hitbox ->
            assertTrue(hitbox.sourceX >= 0)
            assertTrue(hitbox.sourceY >= 0)
            assertTrue(hitbox.sourceX + CalculatorKeyLayout.KEY_WIDTH <= CalculatorKeyLayout.SOURCE_WIDTH)
            assertTrue(hitbox.sourceY + CalculatorKeyLayout.KEY_HEIGHT <= CalculatorKeyLayout.SOURCE_HEIGHT)
        }

        CalculatorKeyLayout.keys.forEachIndexed { index, first ->
            CalculatorKeyLayout.keys.drop(index + 1).forEach { second ->
                val overlaps =
                    first.sourceX < second.sourceX + CalculatorKeyLayout.KEY_WIDTH &&
                        first.sourceX + CalculatorKeyLayout.KEY_WIDTH > second.sourceX &&
                        first.sourceY < second.sourceY + CalculatorKeyLayout.KEY_HEIGHT &&
                        first.sourceY + CalculatorKeyLayout.KEY_HEIGHT > second.sourceY
                assertFalse(overlaps, "${first.key} overlaps ${second.key}")
            }
        }
    }

    @Test
    fun hitTestingReturnsTheTypedPhysicalKey() {
        CalculatorKeyLayout.keys.forEach { hitbox ->
            val key = CalculatorKeyLayout.hitTest(
                hitbox.sourceX + CalculatorKeyLayout.KEY_WIDTH / 2.0,
                hitbox.sourceY + CalculatorKeyLayout.KEY_HEIGHT / 2.0,
                widgetX = 0,
                widgetY = 0,
                widgetWidth = CalculatorKeyLayout.SOURCE_WIDTH,
                widgetHeight = CalculatorKeyLayout.SOURCE_HEIGHT
            )
            assertEquals(hitbox.key, key)
        }
    }

    @Test
    fun everyPrimaryKeyHasAnExplicitCommand() {
        CalculatorKey.entries.forEach { key ->
            val command = CalculatorKeyBindings.resolve(key, ModifierLayer.NORMAL)
            assertFalse(command is CalculatorCommand.Placeholder, key.toString())
        }
    }

    @Test
    fun secondAndAlphaLayersHaveExplicitShiftedCommands() {
        val nonModifierKeys = CalculatorKey.entries - setOf(CalculatorKey.SECOND, CalculatorKey.ALPHA)
        val implementedSecondCommands = mapOf(
            CalculatorKey.NEGATIVE to CalculatorCommand.InsertAns,
            CalculatorKey.DECIMAL to CalculatorCommand.InsertImaginaryUnit,
            CalculatorKey.POWER to CalculatorCommand.InsertPi,
            CalculatorKey.DIVIDE to CalculatorCommand.InsertEuler,
            CalculatorKey.SIN to CalculatorCommand.InsertInverseSine,
            CalculatorKey.COS to CalculatorCommand.InsertInverseCosine,
            CalculatorKey.TAN to CalculatorCommand.InsertInverseTangent,
            CalculatorKey.LOG to CalculatorCommand.InsertTenPower,
            CalculatorKey.LN to CalculatorCommand.InsertEulerPower,
            CalculatorKey.SQUARE to CalculatorCommand.InsertSquareRoot,
            CalculatorKey.COMMA to CalculatorCommand.InsertScientificExponent,
            CalculatorKey.ENTER to CalculatorCommand.RecallEntry,
            CalculatorKey.DELETE to CalculatorCommand.ToggleInsertMode,
            CalculatorKey.MATH to CalculatorCommand.OpenCompactMenu(CompactMenuId.TEST),
            CalculatorKey.APPS to CalculatorCommand.OpenCompactMenu(CompactMenuId.ANGLE)
        )
        val implementedAlphaCommands = listOf(
            CalculatorKey.MATH,
            CalculatorKey.APPS,
            CalculatorKey.PROGRAM,
            CalculatorKey.RECIPROCAL,
            CalculatorKey.SIN,
            CalculatorKey.COS,
            CalculatorKey.TAN,
            CalculatorKey.POWER,
            CalculatorKey.SQUARE,
            CalculatorKey.COMMA,
            CalculatorKey.OPEN_PARENTHESIS,
            CalculatorKey.CLOSE_PARENTHESIS,
            CalculatorKey.DIVIDE,
            CalculatorKey.LOG,
            CalculatorKey.DIGIT_7,
            CalculatorKey.DIGIT_8,
            CalculatorKey.DIGIT_9,
            CalculatorKey.MULTIPLY,
            CalculatorKey.LN,
            CalculatorKey.DIGIT_4,
            CalculatorKey.DIGIT_5,
            CalculatorKey.DIGIT_6,
            CalculatorKey.SUBTRACT,
            CalculatorKey.STORE,
            CalculatorKey.DIGIT_1,
            CalculatorKey.DIGIT_2,
            CalculatorKey.DIGIT_3
        ).zip(CalculatorVariable.entries).toMap()
        val implementedAlphaMenus = mapOf(
            CalculatorKey.Y_EQUALS to CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FRAC),
            CalculatorKey.WINDOW to CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FUNC)
        )
        nonModifierKeys.forEach { key ->
            when {
                key == CalculatorKey.MODE ->
                    assertIs<CalculatorCommand.QuitToHome>(
                        CalculatorKeyBindings.resolve(key, ModifierLayer.SECOND)
                    )
                key in implementedSecondCommands ->
                    assertEquals(
                        implementedSecondCommands.getValue(key),
                        CalculatorKeyBindings.resolve(key, ModifierLayer.SECOND)
                    )
                else ->
                    assertIs<CalculatorCommand.Placeholder>(
                        CalculatorKeyBindings.resolve(key, ModifierLayer.SECOND)
                    )
            }
            if (key in implementedAlphaMenus) {
                assertEquals(
                    implementedAlphaMenus.getValue(key),
                    CalculatorKeyBindings.resolve(key, ModifierLayer.ALPHA)
                )
            } else if (key in implementedAlphaCommands) {
                assertEquals(
                    CalculatorCommand.InsertVariable(implementedAlphaCommands.getValue(key)),
                    CalculatorKeyBindings.resolve(key, ModifierLayer.ALPHA)
                )
            } else {
                assertIs<CalculatorCommand.Placeholder>(
                    CalculatorKeyBindings.resolve(key, ModifierLayer.ALPHA)
                )
            }
        }
    }

    @Test
    fun secondModeQuitsToHomeAndConsumesTheModifier() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        controller.state.historyNavigationPosition = 4
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        assertEquals(ModifierLayer.SECOND, controller.state.modifier)

        val result = controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        assertIs<DispatchResult.Handled>(result)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals(0, controller.state.historyNavigationPosition)

    }

    @Test
    fun shiftedPlaceholdersAreOneShotAndDoNotFallThroughToPrimaryAction() {
        val controller = CalculatorController()
        val beforeAlphaPlaceholder = CalculatorDisplayMemory.current()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        assertEquals(ModifierLayer.ALPHA, controller.state.modifier)
        val alphaResult = controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_0))
        assertIs<DispatchResult.Placeholder>(alphaResult)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
        assertEquals(beforeAlphaPlaceholder, CalculatorDisplayMemory.current())
    }

    @Test
    fun pressingTheActiveModifierAgainCancelsIt() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
    }

    @Test
    fun alphaLockPersistsAcrossCommandsAndTemporarySecondThenAlphaCancelsIt() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))

        assertTrue(controller.state.alphaLocked)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        assertIs<DispatchResult.Placeholder>(
            controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_0))
        )
        assertTrue(controller.state.alphaLocked)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        assertIs<DispatchResult.Placeholder>(
            controller.dispatch(CalculatorInputEvent(CalculatorKey.PROGRAM))
        )
        assertTrue(controller.state.alphaLocked)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        assertFalse(controller.state.alphaLocked)
    }

    @Test
    fun directViewKeysUseExplicitTransitionsAndResetTransientState() {
        val controller = CalculatorController()
        controller.state.historyNavigationPosition = 4
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))

        assertEquals(CalculatorView.MODE, controller.state.view)
        assertEquals(0, controller.state.historyNavigationPosition)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
    }

    @Test
    fun unsupportedPrimaryKeysAreReportedWithoutChangingView() {
        val controller = CalculatorController()
        val result = controller.dispatch(CalculatorInputEvent(CalculatorKey.STAT))

        assertIs<DispatchResult.Unsupported>(result)
        assertEquals(CalculatorView.HOME, controller.state.view)
    }

    @Test
    fun approvedPhaseFourMenusUseExplicitTypedMappings() {
        assertEquals(
            CalculatorCommand.OpenCompactMenu(CompactMenuId.MATH),
            CalculatorKeyBindings.resolve(CalculatorKey.MATH, ModifierLayer.NORMAL)
        )
        assertEquals(
            CalculatorCommand.OpenCompactMenu(CompactMenuId.TEST),
            CalculatorKeyBindings.resolve(CalculatorKey.MATH, ModifierLayer.SECOND)
        )
        assertEquals(
            CalculatorCommand.OpenCompactMenu(CompactMenuId.ANGLE),
            CalculatorKeyBindings.resolve(CalculatorKey.APPS, ModifierLayer.SECOND)
        )
        assertEquals(
            CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FRAC),
            CalculatorKeyBindings.resolve(CalculatorKey.Y_EQUALS, ModifierLayer.ALPHA)
        )
        assertEquals(
            CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FUNC),
            CalculatorKeyBindings.resolve(CalculatorKey.WINDOW, ModifierLayer.ALPHA)
        )
        assertIs<CalculatorCommand.Placeholder>(
            CalculatorKeyBindings.resolve(CalculatorKey.VARS, ModifierLayer.SECOND)
        )
    }

    @Test
    fun zoomAlphaShortcutRequiresPhysicalAlphaLayer() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ZOOM))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))

        assertIs<DispatchResult.Handled>(
            controller.dispatch(CalculatorInputEvent(CalculatorKey.SIN))
        )
        assertEquals(CalculatorView.GRAPH, controller.state.view)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
    }
}
