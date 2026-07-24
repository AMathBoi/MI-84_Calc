package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.controller.DispatchResult
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView

class CalculatorRegressionTest {
    @BeforeTest
    fun resetCalculatorState() {
        resetDisplayMemory()
        resetVariableMemory()
        resetModeMemory()
        resetYEqualsMemory()
        WindowSettingsMemory.restore(DEFAULT_WINDOW)
        setField(WindowSettingsMemory, "selectedIndex", 0)
    }

    @AfterTest
    fun removeSavedTestState() {
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_display_memory.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_mode_settings.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_window_settings.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_y_equals_memory.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_scalar_variables.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("atomic-test.txt"))
    }

    @Test
    fun rejectsPowerResultsThatWouldBeUnreasonablyLarge() {
        enter("9^1000000")
        CalculatorDisplayMemory.submit()

        assertEquals("Error: Result too large", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun preservesSmallDivisionResultsAndDisplaysThemScientifically() {
        enter("1/100000000000")
        CalculatorDisplayMemory.submit()

        val result = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals("1E-11", result.result)
        assertEquals(0, result.rawResult!!.compareTo(BigDecimal("0.00000000001")))
    }

    @Test
    fun preservesSmallComplexComponentsAndDisplaysThemScientifically() {
        selectRectangularComplexMode()
        enter("(-1)^0.5")
        CalculatorDisplayMemory.submit()
        enter("Ans/10000000000000")
        CalculatorDisplayMemory.submit()

        val result = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals("1E-13i", result.result)
        assertEquals(1.0e-13, result.rawImaginaryResult!!.toDouble(), 1.0e-28)
    }

    @Test
    fun exponentiationBindsMoreTightlyThanUnaryMinus() {
        enter("-2^2")
        CalculatorDisplayMemory.submit()
        enter("(-2)^2")
        CalculatorDisplayMemory.submit()

        assertEquals("-4", CalculatorDisplayMemory.allSubmitted()[0].result)
        assertEquals("4", CalculatorDisplayMemory.allSubmitted()[1].result)
    }

    @Test
    fun rejectsWindowBoundsThatCannotBeStoredSafely() {
        assertTrue(
            WindowSettingsMemory.setGraphWindow("-10", "10", "1", "-10", "10", "1")
        )
        val validWindow = WindowSettingsMemory.snapshot()

        assertFalse(
            WindowSettingsMemory.setGraphWindow(
                "-0.000000000000001",
                "0.000000000000001",
                "1",
                "-10",
                "10",
                "1"
            )
        )
        assertFalse(
            WindowSettingsMemory.setGraphWindow("-10000000000000", "10", "1", "-10", "10", "1")
        )
        assertEquals(validWindow, WindowSettingsMemory.snapshot())
    }

    @Test
    fun aSecondDecimalProducesSyntaxErrorInsteadOfBeingIgnored() {
        enter("12.3")
        CalculatorDisplayMemory.appendDecimalPoint()
        CalculatorDisplayMemory.submit()

        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun signToggleTargetsOperandAfterAnsSubtraction() {
        enter("2")
        CalculatorDisplayMemory.submit()
        CalculatorDisplayMemory.appendOperator('-')
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.toggleCurrentNumberSign()

        assertEquals("Ans--2", CalculatorDisplayMemory.current())
    }

    @Test
    fun homeEditorAllowsImplicitMultiplicationTokens() {
        CalculatorDisplayMemory.appendDigit('8')
        CalculatorDisplayMemory.appendXVariable()
        assertEquals("8X", CalculatorDisplayMemory.current())

        CalculatorDisplayMemory.clearCurrent()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendOpenParenthesis()
        assertEquals("2(", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.appendDigit('3')
        CalculatorDisplayMemory.appendCloseParenthesis()
        CalculatorDisplayMemory.submit()
        assertEquals("6", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.clearCurrent()
        CalculatorDisplayMemory.appendDigit('3')
        CalculatorDisplayMemory.appendFunction("sin")
        assertEquals("3sin(", CalculatorDisplayMemory.current())
    }

    @Test
    fun historyEvictsTheOldestEntryWhenOneThousandAreStored() {
        @Suppress("UNCHECKED_CAST")
        val entries = field(CalculatorDisplayMemory, "submittedEntries")
            .get(CalculatorDisplayMemory) as MutableList<CalculatorDisplayMemory.SubmittedEntry>
        repeat(1_000) { index ->
            entries += CalculatorDisplayMemory.SubmittedEntry("old-$index", index.toString())
        }

        enter("7")
        CalculatorDisplayMemory.submit()

        assertEquals(1_000, CalculatorDisplayMemory.allSubmitted().size)
        assertEquals("old-1", CalculatorDisplayMemory.allSubmitted().first().input)
        assertEquals("7", CalculatorDisplayMemory.allSubmitted().last().input)
    }

    @Test
    fun traceMovementStopsAtBothXBounds() {
        assertEquals(
            10.0,
            GraphNavigationMath.clampedTraceX(9.75, direction = 1, step = 1.0, xMin = -10.0, xMax = 10.0)
        )
        assertEquals(
            -10.0,
            GraphNavigationMath.clampedTraceX(-9.75, direction = -1, step = 1.0, xMin = -10.0, xMax = 10.0)
        )
    }

    @Test
    fun atomicPersistenceKeepsLastGoodFileWhenTheNextSaveFails() {
        val stateFile = TEST_CONFIG.resolve("atomic-test.txt")
        CalculatorPersistence.save(stateFile) { listOf("last-good") }
        CalculatorPersistence.save(stateFile) { error("simulated serialization failure") }

        var loaded = emptyList<String>()
        CalculatorPersistence.load(stateFile) { loaded = it }
        assertEquals(listOf("last-good"), loaded)
        Files.list(TEST_CONFIG).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".tmp") })
        }
    }

    @Test
    fun integerZoomBoundsRemainCorrectBeyondIntRange() {
        assertEquals(
            "3000000000",
            GraphNavigationMath.integerBound(3_000_000_000.25, RoundingMode.FLOOR)
        )
        assertEquals(
            "3000000001",
            GraphNavigationMath.integerBound(3_000_000_000.25, RoundingMode.CEILING)
        )
    }

    @Test
    fun exactDegreeTrigIdentitiesStoreExactResults() {
        selectDegreeMode()
        CalculatorDisplayMemory.appendFunction("sin")
        enter("180")
        CalculatorDisplayMemory.submit()
        CalculatorDisplayMemory.appendFunction("cos")
        enter("90")
        CalculatorDisplayMemory.submit()
        CalculatorDisplayMemory.appendFunction("tan")
        enter("180")
        CalculatorDisplayMemory.submit()

        CalculatorDisplayMemory.allSubmitted().forEach { result ->
            assertEquals("0", result.result)
            assertEquals(0, result.rawResult!!.compareTo(BigDecimal.ZERO))
        }

        CalculatorDisplayMemory.appendFunction("sin")
        enter("0.0000000000001")
        CalculatorDisplayMemory.submit()
        assertTrue(CalculatorDisplayMemory.allSubmitted().last().rawResult!! > BigDecimal.ZERO)
    }

    @Test
    fun phaseOneSecondMappingsInsertTypedTokensAndConsumeTheModifier() {
        val controller = CalculatorController()
        val mappings = listOf(
            CalculatorKey.NEGATIVE to "Ans",
            CalculatorKey.DECIMAL to "i",
            CalculatorKey.POWER to "π",
            CalculatorKey.DIVIDE to "e",
            CalculatorKey.SIN to "sin⁻¹(",
            CalculatorKey.COS to "cos⁻¹(",
            CalculatorKey.TAN to "tan⁻¹(",
            CalculatorKey.LOG to "10^(",
            CalculatorKey.LN to "e^(",
            CalculatorKey.SQUARE to "sqrt("
        )

        mappings.forEach { (key, expected) ->
            CalculatorDisplayMemory.clearCurrent()
            controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
            val result = controller.dispatch(CalculatorInputEvent(key))
            assertIs<DispatchResult.Handled>(result)
            assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
            assertEquals(expected, CalculatorDisplayMemory.current())
        }

        CalculatorDisplayMemory.clearCurrent()
        CalculatorDisplayMemory.appendDigit('1')
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.COMMA))
        assertEquals("1EE", CalculatorDisplayMemory.current())
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)
    }

    @Test
    fun phaseOneTokensRouteThroughYEqualsAndWindowEditors() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.POWER))
        assertEquals("π", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SIN))
        assertEquals("-10sin⁻¹(", WindowSettingsMemory.value(0))
    }

    @Test
    fun explicitAnsUsesTheLatestValidRawAnswerInNonemptyAndComplexExpressions() {
        CalculatorDisplayMemory.appendAns()
        CalculatorDisplayMemory.submit()
        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)

        enter("7")
        CalculatorDisplayMemory.submit()
        enter("1/0")
        CalculatorDisplayMemory.submit()

        CalculatorDisplayMemory.appendRecalledHistory("2+")
        CalculatorDisplayMemory.appendAns()
        assertEquals("2+Ans", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("9", CalculatorDisplayMemory.allSubmitted().last().result)

        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.submit()
        CalculatorDisplayMemory.appendAns()
        CalculatorDisplayMemory.submit()
        assertEquals("i", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun imaginaryUnitRequiresRectangularComplexModeAndSupportsImplicitMultiplication() {
        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.submit()
        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)

        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.submit()
        val result = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals("2i", result.result)
        assertEquals(2.0, result.rawImaginaryResult!!.toDouble())
    }

    @Test
    fun constantsAndPowerTemplatesEvaluateWithOmittedClosingParentheses() {
        CalculatorDisplayMemory.appendPi()
        CalculatorDisplayMemory.submit()
        assertEquals(Math.PI, CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble())

        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendPi()
        CalculatorDisplayMemory.submit()
        assertEquals(
            2.0 * Math.PI,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble()
        )

        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendOperator('+')
        CalculatorDisplayMemory.appendPi()
        CalculatorDisplayMemory.appendOperator('*')
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        assertEquals(
            2.0 + 2.0 * Math.PI,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble()
        )

        CalculatorDisplayMemory.appendEuler()
        CalculatorDisplayMemory.submit()
        assertEquals(Math.E, CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble())

        CalculatorDisplayMemory.appendTenPower()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        assertEquals("100", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendEulerPower()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals(
            Math.E,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble(),
            1.0e-15
        )
    }

    @Test
    fun inverseTrigRespectsAngleModeAndUsesPrincipalComplexValues() {
        CalculatorDisplayMemory.appendInverseSine()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals(
            Math.PI / 2.0,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble()
        )

        selectDegreeMode()
        CalculatorDisplayMemory.appendInverseSine()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals("90", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendInverseCosine()
        CalculatorDisplayMemory.toggleCurrentNumberSign()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals("180", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendInverseTangent()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals("45", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendInverseSine()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)

        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendInverseSine()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        val complexResult = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals(90.0, complexResult.rawResult!!.toDouble(), 1.0e-12)
        assertTrue(complexResult.rawImaginaryResult!!.toDouble() < 0.0)
    }

    @Test
    fun complexInverseCosineAndTangentUseFinitePrincipalBranches() {
        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendInverseCosine()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        val inverseCosine = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals(0.0, inverseCosine.rawResult!!.toDouble(), 1.0e-12)
        assertEquals(1.3169578969248166, inverseCosine.rawImaginaryResult!!.toDouble(), 1.0e-12)

        CalculatorDisplayMemory.appendInverseTangent()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.submit()
        val inverseTangent = CalculatorDisplayMemory.allSubmitted().last()
        assertEquals(Math.PI / 2.0, inverseTangent.rawResult!!.toDouble(), 1.0e-12)
        assertEquals(0.5493061443340549, inverseTangent.rawImaginaryResult!!.toDouble(), 1.0e-12)
    }

    @Test
    fun squareRootUsesRealAndPrincipalComplexDomains() {
        CalculatorDisplayMemory.appendSquareRoot()
        CalculatorDisplayMemory.appendDigit('9')
        CalculatorDisplayMemory.submit()
        assertEquals("3", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendSquareRoot()
        CalculatorDisplayMemory.toggleCurrentNumberSign()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)

        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendSquareRoot()
        CalculatorDisplayMemory.toggleCurrentNumberSign()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.submit()
        assertEquals("i", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun scientificExponentValidatesPlacementSignsAndMalformedInput() {
        CalculatorDisplayMemory.appendScientificExponent()
        assertEquals("", CalculatorDisplayMemory.current())

        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.appendDecimalPoint()
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendScientificExponent()
        CalculatorDisplayMemory.toggleCurrentNumberSign()
        CalculatorDisplayMemory.appendDigit('3')
        assertEquals("1.2EE-3", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("0.0012", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendScientificExponent()
        CalculatorDisplayMemory.appendDigit('3')
        CalculatorDisplayMemory.appendOperator('^')
        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.submit()
        assertEquals("4000000", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.appendScientificExponent()
        CalculatorDisplayMemory.appendDecimalPoint()
        CalculatorDisplayMemory.appendDigit('5')
        CalculatorDisplayMemory.submit()
        assertEquals("Error: Syntax", CalculatorDisplayMemory.allSubmitted().last().result)

        setField(ModeSettingsMemory, "selectedCategoryIndex", 0)
        ModeSettingsMemory.selectNextOption()
        CalculatorDisplayMemory.appendDigit('1')
        CalculatorDisplayMemory.appendScientificExponent()
        CalculatorDisplayMemory.appendDigit('3')
        CalculatorDisplayMemory.submit()
        assertEquals("1E3", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun entryRecallReplacesTheEditLineAndWalksBackwardThroughSubmittedInputs() {
        enter("1+1")
        CalculatorDisplayMemory.submit()
        enter("3*4")
        CalculatorDisplayMemory.submit()
        CalculatorDisplayMemory.appendDigit('9')

        val controller = CalculatorController()
        controller.state.historyNavigationPosition = 1
        dispatchSecond(controller, CalculatorKey.ENTER)
        assertEquals("3*4", CalculatorDisplayMemory.current())
        assertEquals(1, controller.state.entryRecallPosition)
        assertEquals(0, controller.state.historyNavigationPosition)

        dispatchSecond(controller, CalculatorKey.ENTER)
        assertEquals("1+1", CalculatorDisplayMemory.current())
        assertEquals(2, controller.state.entryRecallPosition)

        dispatchSecond(controller, CalculatorKey.ENTER)
        assertEquals("1+1", CalculatorDisplayMemory.current())
        assertEquals(2, controller.state.entryRecallPosition)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        assertEquals(0, controller.state.entryRecallPosition)
        dispatchSecond(controller, CalculatorKey.ENTER)
        assertEquals("3*4", CalculatorDisplayMemory.current())
    }

    @Test
    fun insertModeHasDeterministicHomeYEqualsAndWindowCursorBehavior() {
        val controller = CalculatorController()
        enter("123")
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals("193", CalculatorDisplayMemory.current())

        CalculatorDisplayMemory.clearCurrent()
        enter("123")
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        dispatchSecond(controller, CalculatorKey.DELETE)
        assertTrue(controller.state.insertMode)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals("1923", CalculatorDisplayMemory.current())

        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        YEqualsMemory.append("123")
        YEqualsMemory.moveCursorLeft()
        YEqualsMemory.moveCursorLeft()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals("1923", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        WindowSettingsMemory.clearSelected()
        WindowSettingsMemory.append("123")
        WindowSettingsMemory.moveCursorLeft()
        WindowSettingsMemory.moveCursorLeft()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals("1923", WindowSettingsMemory.value(0))

        dispatchSecond(controller, CalculatorKey.DELETE)
        assertFalse(controller.state.insertMode)
    }

    @Test
    fun insertModeDoesNotExceedTheHomeInputLimit() {
        repeat(31) { CalculatorDisplayMemory.appendDigit('1') }
        CalculatorDisplayMemory.moveCursorLeft()
        val cursorBeforeInsert = CalculatorDisplayMemory.cursorPosition()

        val controller = CalculatorController()
        dispatchSecond(controller, CalculatorKey.DELETE)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))

        assertEquals(31, CalculatorDisplayMemory.current().length)
        assertEquals(cursorBeforeInsert, CalculatorDisplayMemory.cursorPosition())
    }

    @Test
    fun alphaVariablesInsertOutsideZoomAndZoomKeepsItsPhysicalShortcuts() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals("A", CalculatorDisplayMemory.current())
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        assertEquals("θ", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        WindowSettingsMemory.clearSelected()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        assertEquals("Z", WindowSettingsMemory.value(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ZOOM))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.POWER))
        assertEquals(CalculatorView.ZOOM, controller.state.view)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals(CalculatorView.GRAPH, controller.state.view)

        val homeExpression = CalculatorDisplayMemory.current()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.POWER))
        assertEquals(CalculatorView.GRAPH, controller.state.view)
        assertEquals(homeExpression, CalculatorDisplayMemory.current())

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        val selectedModeCategory = ModeSettingsMemory.selectedCategoryIndex
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals(selectedModeCategory, ModeSettingsMemory.selectedCategoryIndex)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.TRACE))
        val traceBeforeAlpha = controller.state.trace
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.APPS))
        assertEquals(traceBeforeAlpha, controller.state.trace)
    }

    @Test
    fun scalarVariablesDefaultToZeroStoreRealAndComplexValuesAndSupportGraphs() {
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.A)
        CalculatorDisplayMemory.submit()
        assertEquals("0", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendDigit('5')
        CalculatorDisplayMemory.appendStoreOperator()
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.A)
        CalculatorDisplayMemory.submit()
        assertEquals(
            0,
            CalculatorVariableMemory.value(CalculatorVariable.A).real.compareTo(BigDecimal("5"))
        )

        CalculatorDisplayMemory.appendDigit('2')
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.A)
        CalculatorDisplayMemory.submit()
        assertEquals("10", CalculatorDisplayMemory.allSubmitted().last().result)
        assertEquals(10.0, CalculatorDisplayMemory.evaluateForGraph("AX", 2.0))

        selectRectangularComplexMode()
        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.appendStoreOperator()
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.B)
        CalculatorDisplayMemory.submit()
        assertEquals(
            0,
            CalculatorVariableMemory.value(CalculatorVariable.B).imaginary!!.compareTo(BigDecimal.ONE)
        )

        CalculatorDisplayMemory.appendVariable(CalculatorVariable.B)
        CalculatorDisplayMemory.squareCurrentOperand()
        CalculatorDisplayMemory.submit()
        assertEquals("-1", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendImaginaryUnit()
        CalculatorDisplayMemory.appendStoreOperator()
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.A)
        CalculatorDisplayMemory.submit()
        assertEquals(null, CalculatorDisplayMemory.evaluateForGraph("A", 0.0))
    }

    @Test
    fun xStorageKeepsLegacyPersistenceAndGraphXStillOverridesTheStoredValue() {
        CalculatorDisplayMemory.appendDigit('7')
        CalculatorDisplayMemory.appendStoreOperator()
        CalculatorDisplayMemory.appendXVariable()
        CalculatorDisplayMemory.submit()

        assertEquals(
            0,
            CalculatorVariableMemory.value(CalculatorVariable.X).real.compareTo(BigDecimal("7"))
        )
        assertEquals(7.0, CalculatorDisplayMemory.evaluateForGraph("X", 7.0))
        assertEquals(2.0, CalculatorDisplayMemory.evaluateForGraph("X", 2.0))
        assertTrue(
            Files.readAllLines(TEST_CONFIG.resolve("mi84_calc_display_memory.txt"))
                .contains("x\t7")
        )
    }

    @Test
    fun scalarVariablePersistenceRoundTripsAndImportsLegacyX() {
        CalculatorVariableMemory.set(CalculatorVariable.A, BigDecimal("12.5"))
        CalculatorVariableMemory.set(
            CalculatorVariable.B,
            BigDecimal.ONE,
            BigDecimal("-2.25")
        )
        resetVariableMemory()
        invokePrivateNoArg(CalculatorVariableMemory, "load")

        assertEquals(
            CalculatorScalarValue(BigDecimal("12.5")),
            CalculatorVariableMemory.value(CalculatorVariable.A)
        )
        assertEquals(
            CalculatorScalarValue(BigDecimal.ONE, BigDecimal("-2.25")),
            CalculatorVariableMemory.value(CalculatorVariable.B)
        )

        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_scalar_variables.txt"))
        resetVariableMemory()
        CalculatorVariableMemory.initializeLegacyX(BigDecimal("9"))
        assertEquals(
            CalculatorScalarValue(BigDecimal("9")),
            CalculatorVariableMemory.value(CalculatorVariable.X)
        )
        assertTrue(Files.exists(TEST_CONFIG.resolve("mi84_calc_scalar_variables.txt")))
    }

    private fun enter(expression: String) {
        expression.forEach { character ->
            when {
                character.isDigit() -> CalculatorDisplayMemory.appendDigit(character)
                character == '-' && (
                    CalculatorDisplayMemory.current().isEmpty() ||
                        CalculatorDisplayMemory.current().last() in "(+*/^-"
                    ) -> CalculatorDisplayMemory.toggleCurrentNumberSign()
                character in "+-*/^" -> CalculatorDisplayMemory.appendOperator(character)
                character == '.' -> CalculatorDisplayMemory.appendDecimalPoint()
                character == '(' -> CalculatorDisplayMemory.appendOpenParenthesis()
                character == ')' -> CalculatorDisplayMemory.appendCloseParenthesis()
                character == 'X' -> CalculatorDisplayMemory.appendXVariable()
                else -> Unit
            }
        }
    }

    private fun selectRectangularComplexMode() {
        setField(ModeSettingsMemory, "selectedCategoryIndex", 4)
        ModeSettingsMemory.selectNextOption()
    }

    private fun selectDegreeMode() {
        setField(ModeSettingsMemory, "selectedCategoryIndex", 2)
        ModeSettingsMemory.selectPreviousOption()
    }

    private fun dispatchSecond(controller: CalculatorController, key: CalculatorKey) {
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(key))
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetDisplayMemory() {
        field(CalculatorDisplayMemory, "submittedEntries")
            .get(CalculatorDisplayMemory)
            .let { it as MutableList<CalculatorDisplayMemory.SubmittedEntry> }
            .clear()
        setField(CalculatorDisplayMemory, "currentEntry", "")
        setField(CalculatorDisplayMemory, "cursor", 0)
        setField(CalculatorDisplayMemory, "xValue", BigDecimal.ZERO)
        setField(CalculatorDisplayMemory, "firstVisibleSubmittedIndex", 0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetYEqualsMemory() {
        val equations = field(YEqualsMemory, "equations").get(YEqualsMemory) as MutableList<String>
        val cursors = field(YEqualsMemory, "cursors").get(YEqualsMemory) as MutableList<Int>
        equations.indices.forEach { index ->
            equations[index] = ""
            cursors[index] = 0
        }
        setField(YEqualsMemory, "selectedIndex", 0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetVariableMemory() {
        val values = field(CalculatorVariableMemory, "values")
            .get(CalculatorVariableMemory) as MutableMap<CalculatorVariable, CalculatorScalarValue>
        CalculatorVariable.entries.forEach { variable ->
            values[variable] = CalculatorScalarValue(BigDecimal.ZERO)
        }
        val loadedVariables = field(CalculatorVariableMemory, "loadedVariables")
            .get(CalculatorVariableMemory) as MutableSet<CalculatorVariable>
        loadedVariables.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetModeMemory() {
        val selectedOptions = field(ModeSettingsMemory, "selectedOptions")
            .get(ModeSettingsMemory) as MutableList<Int>
        DEFAULT_MODE_OPTIONS.forEachIndexed { index, option -> selectedOptions[index] = option }
        setField(ModeSettingsMemory, "selectedCategoryIndex", 0)
    }

    private fun setField(target: Any, name: String, value: Any) {
        field(target, name).set(target, value)
    }

    private fun field(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun invokePrivateNoArg(target: Any, name: String) {
        target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    }

    companion object {
        private val TEST_CONFIG: Path = TestFabricEnvironment.configDir
        private val DEFAULT_MODE_OPTIONS = listOf(0, 0, 1, 0, 0, 0, 0, 0, 0)
        private val DEFAULT_WINDOW =
            listOf("-10", "10", "1", "-10", "10", "1", "1", "5/66", "5/33")

    }
}
