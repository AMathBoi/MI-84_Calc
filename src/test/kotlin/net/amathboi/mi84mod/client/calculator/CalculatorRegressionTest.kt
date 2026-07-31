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
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuId
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuTab

class CalculatorRegressionTest {
    @BeforeTest
    fun resetCalculatorState() {
        resetDisplayMemory()
        resetVariableMemory()
        resetModeMemory()
        resetYEqualsMemory()
        WindowSettingsMemory.restore(DEFAULT_WINDOW)
        setField(WindowSettingsMemory, "selectedIndex", 0)
        setField(ZoomMemory, "storedWindow", null)
        setField(ZoomMemory, "previousWindow", null)
    }

    @AfterTest
    fun removeSavedTestState() {
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_display_memory.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_mode_settings.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_window_settings.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_y_equals_memory.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_scalar_variables.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_zoom_memory.txt"))
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
    fun graphSamplingBreaksAtUndefinedOrPoleLikeMidpoints() {
        assertTrue(
            GraphNavigationMath.shouldConnectSamples(
                0.0,
                0.0,
                1.0,
                100.0,
                -10.0,
                10.0
            ) { x -> x * 100.0 }
        )
        assertFalse(
            GraphNavigationMath.shouldConnectSamples(
                1.4893617021276597,
                12.25263201580879,
                1.7021276595744688,
                -7.570501652965523,
                -10.0,
                10.0,
                Math::tan
            )
        )
        assertFalse(
            GraphNavigationMath.shouldConnectSamples(
                -1.0,
                -1.0,
                1.0,
                1.0,
                -10.0,
                10.0
            ) { null }
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
    fun commonDegreeTrigAndInverseTrigIdentitiesAreDeterministic() {
        selectDegreeMode()
        listOf(
            "sin(30)" to "0.5",
            "sin(45)" to "0.7071067811865476",
            "sin(90)" to "1",
            "cos(30)" to "0.8660254037844386",
            "cos(45)" to "0.7071067811865476",
            "cos(90)" to "0",
            "tan(30)" to "0.5773502691896258",
            "tan(45)" to "1",
            "tan(60)" to "1.7320508075688772",
            "tan(90)" to "Error: Domain",
            "tan(270)" to "Error: Domain",
            "sin(-90)" to "-1",
            "cos(-90)" to "0",
            "tan(-90)" to "Error: Domain",
            "sin⁻¹(0.5)" to "30",
            "sin⁻¹(1)" to "90",
            "cos⁻¹(-0.5)" to "120",
            "cos⁻¹(0)" to "90",
            "tan⁻¹(1)" to "45",
            "sin⁻¹(sin(45))" to "45",
            "cos⁻¹(cos(45))" to "45",
            "tan⁻¹(tan(45))" to "45"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }

        selectRectangularComplexMode()
        submitRaw("tan(90)")
        assertEquals("Error: Domain", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun commonRadianTrigIdentitiesNormalizePiExpressions() {
        listOf(
            "sin(π)" to "0",
            "cos(π/2)" to "0",
            "tan(π/4)" to "1"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
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
    fun negativeKeyTogglesTheActiveOperandInYEqualsAndWindow() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.NEGATIVE))
        assertEquals("-5", YEqualsMemory.equation(0))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.NEGATIVE))
        assertEquals("5", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        WindowSettingsMemory.clearSelected()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.NEGATIVE))
        assertEquals("-5", WindowSettingsMemory.value(0))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.NEGATIVE))
        assertEquals("5", WindowSettingsMemory.value(0))

        WindowSettingsMemory.clearSelected()
        WindowSettingsMemory.append("1EE")
        controller.dispatch(CalculatorInputEvent(CalculatorKey.NEGATIVE))
        assertEquals("1EE-", WindowSettingsMemory.value(0))
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
    fun scientificAndFunctionRangesRejectOverflowAndUnderflow() {
        submitRaw("1EE-309")
        assertEquals("Error: Result out of range", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("1EE309")
        assertEquals("Error: Result too large", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("sqrt(1EE308*10)")
        assertEquals("Error: Result too large", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("sqrt(1EE-308)")
        assertEquals("1E-154", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("sqrt((1EE-308)^3)")
        assertEquals("Error: Result out of range", CalculatorDisplayMemory.allSubmitted().last().result)

        selectRectangularComplexMode()
        submitRaw("sqrt((1EE-308)^3)")
        assertEquals("Error: Result out of range", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("1EE-309i")
        assertEquals("Error: Result out of range", CalculatorDisplayMemory.allSubmitted().last().result)
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

    @Test
    fun phaseThreeRelationsReturnNumericBooleansAfterArithmeticEvaluation() {
        listOf(
            "2+3=5" to "1",
            "2≠3" to "1",
            "3>2" to "1",
            "3≥3" to "1",
            "2<3" to "1",
            "2≤2" to "1",
            "2+3*4>13" to "1",
            "4<2" to "0"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun phaseThreeBooleanOperationsUseNumericTruthAndDocumentedPrecedence() {
        listOf(
            "1or0and0" to "1",
            "1xor1" to "0",
            "1or1xor1" to "0",
            "not(0)" to "1",
            "not(-2)" to "0",
            "2<3and4≥4" to "1",
            "2>3or4<1" to "0"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun phaseThreeMultiArgumentParsingRejectsStrayCommasAndBadArity() {
        listOf(
            "min(3,1)" to "1",
            "max(1,min(3,2))" to "2",
            "min(3,2" to "2",
            "1,2" to "Error: Syntax",
            "gcd(1)" to "Error: Syntax",
            "min(1,)" to "Error: Syntax",
            "min(3,1,2)" to "Error: Syntax"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun phaseThreeMathAndNumberHelpersUseDefinedScalarSemantics() {
        listOf(
            "abs(-3)" to "3",
            "round(1.235,2)" to "1.24",
            "round(-1.5)" to "-1.5",
            "round(1.234567890123)" to "1.23456789",
            "iPart(-1.7)" to "-1",
            "fPart(-1.7)" to "-0.7",
            "int(-1.7)" to "-2",
            "gcd(48,18)" to "6",
            "lcm(48,18)" to "144",
            "remainder(7,3)" to "1",
            "gcd(1.5,2)" to "Error: Syntax",
            "gcd(-1,2)" to "Error: Syntax",
            "lcm(1000000000001,2)" to "Error: Syntax",
            "remainder(-7,3)" to "Error: Syntax",
            "remainder(7.5,3)" to "Error: Syntax",
            "remainder(1,0)" to "Error: Division by zero"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun phaseThreeComplexRelationsBooleansAndAbsoluteValueAreExplicit() {
        selectRectangularComplexMode()

        listOf(
            "i=i" to "1",
            "i≠1" to "1",
            "not(i)" to "0",
            "abs(3+4i)" to "5",
            "i>0" to "Error: Syntax"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun phaseThreeExpressionsWorkForGraphsAndCommaRoutesThroughExistingEditors() {
        assertEquals(1.0, CalculatorDisplayMemory.evaluateForGraph("X>0andX≤2", 1.0))
        assertEquals(0.0, CalculatorDisplayMemory.evaluateForGraph("X>0andX≤2", 3.0))
        assertEquals(2.0, CalculatorDisplayMemory.evaluateForGraph("min(X,2)", 3.0))

        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.COMMA))
        assertEquals(",", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.COMMA))
        assertEquals("-10,", WindowSettingsMemory.value(0))
    }

    @Test
    fun approvedAlphaLockPersistsUsesTemporarySecondAndCancelsWithAlpha() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals("A", CalculatorDisplayMemory.current())
        assertTrue(controller.state.alphaLocked)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SIN))
        assertEquals("Asin⁻¹(", CalculatorDisplayMemory.current())
        assertTrue(controller.state.alphaLocked)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        assertTrue(controller.state.alphaLocked)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("Asin⁻¹(=", CalculatorDisplayMemory.current())
        assertTrue(controller.state.alphaLocked)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        assertEquals(CalculatorView.WINDOW, controller.state.view)
        assertTrue(controller.state.alphaLocked)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        assertFalse(controller.state.alphaLocked)
    }

    @Test
    fun testMenuNavigatesTabsRowsAndPastesLogicTokensIntoTheOriginEditor() {
        val controller = CalculatorController()
        CalculatorDisplayMemory.appendDigit('1')
        dispatchSecond(controller, CalculatorKey.MATH)

        val menu = controller.state.compactMenu!!
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        assertEquals("TEST", menu.selectedTab.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("LOGIC", menu.selectedTab.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN))
        assertEquals("or", menu.selectedItem.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        assertEquals(2, menu.selectedItemIndex)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))

        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("1or", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.appendDigit('0')
        CalculatorDisplayMemory.submit()
        assertEquals("1", CalculatorDisplayMemory.allSubmitted().last().result)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        dispatchSecond(controller, CalculatorKey.MATH)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        assertEquals(CalculatorView.Y_EQUALS, controller.state.view)
        assertEquals(">", YEqualsMemory.equation(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        WindowSettingsMemory.clearSelected()
        dispatchSecond(controller, CalculatorKey.MATH)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_4))
        assertEquals(CalculatorView.WINDOW, controller.state.view)
        assertEquals("≥", WindowSettingsMemory.value(0))
    }

    @Test
    fun unavailableConditionsStayOpenAndClearReturnsWithoutChangingTheEditor() {
        val controller = CalculatorController()
        CalculatorDisplayMemory.appendDigit('7')
        dispatchSecond(controller, CalculatorKey.MATH)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))

        val menu = controller.state.compactMenu!!
        assertEquals("CONDITIONS", menu.selectedTab.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        assertFalse(menu.selectedItem.available)
        assertEquals("7", CalculatorDisplayMemory.current())

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.CLEAR))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("7", CalculatorDisplayMemory.current())
    }

    @Test
    fun mathMenuExposesApprovedTabsTokensAndDeferredRows() {
        val controller = CalculatorController()
        CalculatorDisplayMemory.appendDigit('2')
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))

        val menu = controller.state.compactMenu!!
        assertEquals(listOf("MATH", "NUM", "CMPLX", "PROB", "FRAC"), menu.definition.tabs.map { it.label })
        assertEquals(13, menu.selectedTab.items.size)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        assertFalse(menu.selectedItem.available)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("2^3", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("8", CalculatorDisplayMemory.allSubmitted().last().result)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("logBASE(", CalculatorDisplayMemory.current())
    }

    @Test
    fun mathMenuCubeRootAndProbabilityRowsEvaluate() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_4))
        enter("27")
        CalculatorDisplayMemory.submit()
        assertEquals("3", CalculatorDisplayMemory.allSubmitted().last().result)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        repeat(3) { controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT)) }
        assertEquals("PROB", controller.state.compactMenu!!.selectedTab.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.COMMA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        CalculatorDisplayMemory.submit()
        assertEquals("20", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun mathDisplayTokensRecognizeCompleteAndInProgressNotation() {
        val squareRoot =
            assertIs<MathDisplayToken.Root>(MathDisplayTokens.firstIn("2+sqrt(1+frac(1,2))"))
        assertEquals(2, squareRoot.start)
        assertEquals("1+frac(1,2)", squareRoot.radicand)
        assertEquals(null, squareRoot.index)
        assertTrue(squareRoot.complete)

        val nthRoot =
            assertIs<MathDisplayToken.Root>(MathDisplayTokens.firstIn("nthRoot(27,3"))
        assertEquals("27", nthRoot.radicand)
        assertEquals("3", nthRoot.index)
        assertEquals(RootFieldOrder.RADICAND_THEN_INDEX, nthRoot.fieldOrder)
        assertTrue(nthRoot.secondFieldEntered)
        assertFalse(nthRoot.complete)

        val indexedRoot =
            assertIs<MathDisplayToken.Root>(MathDisplayTokens.firstIn("root(3,27"))
        assertEquals("27", indexedRoot.radicand)
        assertEquals("3", indexedRoot.index)
        assertEquals(RootFieldOrder.INDEX_THEN_RADICAND, indexedRoot.fieldOrder)
        assertTrue(indexedRoot.secondFieldEntered)

        val permutation =
            assertIs<MathDisplayToken.Combinatoric>(MathDisplayTokens.firstIn("nPr(10,2"))
        assertEquals("10", permutation.leftOperand)
        assertEquals('P', permutation.operator)
        assertEquals("2", permutation.rightOperand)
        assertTrue(permutation.rightOperandEntered)
        assertFalse(permutation.complete)

        val combination =
            assertIs<MathDisplayToken.Combinatoric>(MathDisplayTokens.firstIn("4+nCr(8,3)"))
        assertEquals(2, combination.start)
        assertEquals('C', combination.operator)
        assertTrue(combination.complete)
    }

    @Test
    fun mathMenuNumAndFracRowsOpenTheSharedFractionEditor() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("NUM", controller.state.compactMenu!!.selectedTab.label)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RECIPROCAL))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertFalse(controller.state.fractionTemplate!!.mixedNumber)

        controller.state.fractionTemplate = null
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        repeat(4) { controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT)) }
        assertEquals("FRAC", controller.state.compactMenu!!.selectedTab.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        assertTrue(controller.state.fractionTemplate!!.mixedNumber)
    }

    @Test
    fun rightAdvancesIndexedRootAndCombinatoricFieldsThenExitsTheirNotation() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("root(3,", CalculatorDisplayMemory.current())
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_7))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("root(3,27)", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("3", CalculatorDisplayMemory.allSubmitted().last().result)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        repeat(3) { controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT)) }
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("nPr(5,", CalculatorDisplayMemory.current())
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ADD))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals("nPr(5,2)+1", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("21", CalculatorDisplayMemory.allSubmitted().last().result)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MATH))
        repeat(3) { controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT)) }
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("nCr(5,2)", CalculatorDisplayMemory.current())
    }

    @Test
    fun completedCombinatoricsTraverseBothOperandsWithoutHiddenCursorStops() {
        listOf("nPr(12,3)", "nCr(12,3)").forEach { expression ->
            CalculatorDisplayMemory.clearCurrent()
            CalculatorDisplayMemory.appendMenuToken(expression)
            val controller = CalculatorController()

            controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
            assertEquals(expression.lastIndex, CalculatorDisplayMemory.cursorPosition())
            controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
            assertEquals(expression.lastIndex - 1, CalculatorDisplayMemory.cursorPosition())
            controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
            assertEquals(expression.indexOf(','), CalculatorDisplayMemory.cursorPosition())
            assertEquals(
                MathCursorField.COMBINATORIC_OPERAND,
                MathDisplayTokens.cursorFieldAt(
                    expression,
                    CalculatorDisplayMemory.cursorPosition()
                )
            )

            controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
            assertEquals(expression.indexOf(',') + 1, CalculatorDisplayMemory.cursorPosition())
            controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
            assertEquals(expression.lastIndex, CalculatorDisplayMemory.cursorPosition())
            controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
            assertEquals(expression.length, CalculatorDisplayMemory.cursorPosition())
            assertEquals(
                null,
                MathDisplayTokens.cursorFieldAt(
                    expression,
                    CalculatorDisplayMemory.cursorPosition()
                )
            )
        }
    }

    @Test
    fun compactMenuDirectViewKeysCancelItAndNonEditorOriginsReturnHome() {
        val controller = CalculatorController()
        dispatchSecond(controller, CalculatorKey.MATH)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.WINDOW))
        assertEquals(CalculatorView.WINDOW, controller.state.view)
        assertEquals(null, controller.state.compactMenu)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        dispatchSecond(controller, CalculatorKey.MATH)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("=", CalculatorDisplayMemory.current())
    }

    @Test
    fun booleanWordOperatorsMoveOverwriteAndDeleteAsWholeEditorTokens() {
        listOf("and", "or", "xor").forEach { operator ->
            CalculatorDisplayMemory.clearCurrent()
            CalculatorDisplayMemory.appendRecalledHistory("1${operator}0")

            CalculatorDisplayMemory.moveCursorLeft()
            assertEquals(operator.length + 1, CalculatorDisplayMemory.cursorPosition())
            CalculatorDisplayMemory.moveCursorLeft()
            assertEquals(1, CalculatorDisplayMemory.cursorPosition())
            CalculatorDisplayMemory.moveCursorRight()
            assertEquals(operator.length + 1, CalculatorDisplayMemory.cursorPosition())
            CalculatorDisplayMemory.moveCursorLeft()
            CalculatorDisplayMemory.appendDigit('9')
            assertEquals("190", CalculatorDisplayMemory.current())

            CalculatorDisplayMemory.clearCurrent()
            CalculatorDisplayMemory.appendRecalledHistory("1${operator}0")
            CalculatorDisplayMemory.moveCursorLeft()
            CalculatorDisplayMemory.moveCursorLeft()
            CalculatorDisplayMemory.deleteAtCursor()
            assertEquals("10", CalculatorDisplayMemory.current())
        }
    }

    @Test
    fun angleMarkersOverrideTheActiveAngleMode() {
        submitRaw("sin(30°)")
        assertEquals(
            0.5,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble(),
            1.0e-15
        )

        selectDegreeMode()
        submitRaw("sin((π/2)ʳ)")
        assertEquals(
            1.0,
            CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble(),
            1.0e-15
        )
    }

    @Test
    fun angleCoordinateConversionsRespectDegreeAndRadianModes() {
        listOf(
            "R►Pr(3,4)" to 5.0,
            "R►Pθ(0,1)" to Math.PI / 2.0,
            "P►Rx(2,π)" to -2.0,
            "P►Ry(2,π/2)" to 2.0
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(
                expected,
                CalculatorDisplayMemory.allSubmitted().last().rawResult!!.toDouble(),
                1.0e-12
            )
        }

        selectDegreeMode()
        listOf(
            "R►Pθ(0,1)" to "90",
            "P►Rx(2,60)" to "1",
            "P►Ry(2,30)" to "1"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    @Test
    fun angleMenuShowsAllRowsPastesApprovedTokensAndKeepsDmsRowsUnavailable() {
        val controller = CalculatorController()
        CalculatorDisplayMemory.appendDigit('3')
        CalculatorDisplayMemory.appendDigit('0')
        dispatchSecond(controller, CalculatorKey.APPS)

        val menu = controller.state.compactMenu!!
        assertEquals("ANGLE", menu.selectedTab.label)
        assertEquals(8, menu.selectedTab.items.size)
        assertFalse(menu.selectedTab.items[1].available)
        assertFalse(menu.selectedTab.items[3].available)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        assertEquals(CalculatorView.COMPACT_MENU, controller.state.view)
        assertEquals("30", CalculatorDisplayMemory.current())
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals("30°", CalculatorDisplayMemory.current())

        CalculatorDisplayMemory.clearCurrent()
        dispatchSecond(controller, CalculatorKey.APPS)
        repeat(7) { controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN)) }
        assertEquals("P►Ry(", controller.state.compactMenu!!.selectedItem.label)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))
        assertEquals("P►Ry(", CalculatorDisplayMemory.current())
    }

    @Test
    fun functionMenusOverlayEditableViewsAndRedirectNonEditorsToHome() {
        val controller = CalculatorController()
        dispatchAlpha(controller, CalculatorKey.Y_EQUALS)

        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals(FunctionMenuTab.FRAC, controller.state.functionMenu!!.selectedTab)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals(FunctionMenuTab.FUNC, controller.state.functionMenu!!.selectedTab)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals(FunctionMenuTab.MTRX, controller.state.functionMenu!!.selectedTab)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals(FunctionMenuTab.YVAR, controller.state.functionMenu!!.selectedTab)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.CLEAR))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        dispatchAlpha(controller, CalculatorKey.WINDOW)
        assertEquals(CalculatorView.Y_EQUALS, controller.state.view)
        assertEquals(CalculatorView.Y_EQUALS, controller.state.functionMenu!!.targetView)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals("abs(", YEqualsMemory.equation(0))
        assertEquals(null, controller.state.functionMenu)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        dispatchAlpha(controller, CalculatorKey.Y_EQUALS)
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals(CalculatorView.HOME, controller.state.functionMenu!!.targetView)
    }

    @Test
    fun varsUsesNestedWindowAndZoomTabsWithDeferredSiblingDomains() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.VARS))
        val menu = controller.state.compactMenu!!
        assertEquals(CompactMenuId.VARS, menu.currentDefinition.id)
        assertEquals(listOf("VARS", "Y-VARS"), menu.currentDefinition.tabs.map { it.label })

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))
        assertEquals(CompactMenuId.VARS_WINDOW, menu.currentDefinition.id)
        assertEquals(listOf("X/Y", "T/θ", "U/V/W"), menu.currentDefinition.tabs.map { it.label })
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "A", "B"),
            menu.selectedTab.items.map { it.hotkey }
        )
        assertEquals(
            listOf(
                "Xmin",
                "Xmax",
                "Xscl",
                "Ymin",
                "Ymax",
                "Yscl",
                "Xres",
                "ΔX",
                "ΔY",
                "XFact",
                "YFact",
                "TraceStep"
            ),
            menu.selectedTab.items.map { it.label }
        )
        assertFalse(menu.selectedTab.items[8].available)
        assertFalse(menu.selectedTab.items[9].available)
        assertFalse(menu.selectedTab.items[10].available)
        assertTrue(menu.selectedTab.items[11].available)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.CLEAR))
        assertEquals(CompactMenuId.VARS, menu.currentDefinition.id)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        assertEquals(CompactMenuId.VARS_ZOOM, menu.currentDefinition.id)
        assertEquals(listOf("ZX/ZY", "ZT/Zθ", "ZU"), menu.currentDefinition.tabs.map { it.label })
        assertEquals(
            listOf("ZXmin", "ZXmax", "ZXscl", "ZYmin", "ZYmax", "ZYscl", "ZXres"),
            menu.selectedTab.items.map { it.label }
        )
        assertTrue(menu.selectedTab.items.none { "ΔX" in it.label || "TraceStep" in it.label })

        controller.dispatch(CalculatorInputEvent(CalculatorKey.CLEAR))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertEquals(CompactMenuId.Y_VARS_FUNCTION, menu.currentDefinition.id)
        assertEquals((1..9).map { "Y$it" }, menu.selectedTab.items.map { it.label })
        assertTrue(menu.selectedTab.items.none { it.hotkey == "0" })
    }

    @Test
    fun varsSelectionsInsertBackedValuesAndRejectDeferredRows() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.VARS))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals(CompactMenuId.VARS_WINDOW, controller.state.compactMenu!!.currentDefinition.id)
        assertEquals("", CalculatorDisplayMemory.current())

        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.APPS))
        assertEquals("TraceStep", CalculatorDisplayMemory.current())
        assertEquals(
            5.0 / 33.0,
            CalculatorDisplayMemory.evaluateForGraph("TraceStep", 0.0)
        )
        CalculatorDisplayMemory.submit()
        assertEquals(
            "0.1515151515151515151515151515151515",
            CalculatorDisplayMemory.allSubmitted().last().result
        )

        WindowSettingsMemory.restore(listOf("-4", "8", "2", "-3", "9", "3", "1", "0.25", "0.5"))
        ZoomMemory.storeCurrent()
        WindowSettingsMemory.restore(DEFAULT_WINDOW)
        submitRaw("ZXmin+ZYmax")
        assertEquals("5", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun f4YVarUsesTwoColumnNavigationWithoutY0() {
        val controller = CalculatorController()
        dispatchAlpha(controller, CalculatorKey.TRACE)
        val menu = controller.state.functionMenu!!
        assertEquals(FunctionMenuTab.YVAR, menu.selectedTab)
        assertEquals(9, menu.items.size)
        assertEquals((1..9).map { it.toString() }, menu.items.map { it.hotkey })
        assertTrue(menu.items.all { it.available })

        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals(5, menu.selectedItemIndex)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN))
        assertEquals(6, menu.selectedItemIndex)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        assertEquals(1, menu.selectedItemIndex)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_0))
        assertEquals(menu, controller.state.functionMenu)
        assertEquals("", CalculatorDisplayMemory.current())
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        assertEquals("Y₉", CalculatorDisplayMemory.current())
        assertEquals(null, controller.state.functionMenu)
    }

    @Test
    fun yFunctionVariablesEvaluateAtTheCurrentGraphCoordinateAndDetectCycles() {
        YEqualsMemory.append("X+1")
        assertEquals(4.0, CalculatorDisplayMemory.evaluateForGraph("Y1", 3.0))
        assertEquals(4.0, CalculatorDisplayMemory.evaluateForGraph("Y₁", 3.0))

        YEqualsMemory.select(1)
        YEqualsMemory.append("2Y1")
        assertEquals(8.0, CalculatorDisplayMemory.evaluateForGraph("Y2", 3.0))

        YEqualsMemory.clearSelected()
        YEqualsMemory.append("Y2")
        assertEquals(null, CalculatorDisplayMemory.evaluateForGraph("Y2", 3.0))
    }

    @Test
    fun scalarYFollowedByADigitCannotBecomeAYFunctionToken() {
        CalculatorVariableMemory.set(CalculatorVariable.Y, BigDecimal("5"))
        CalculatorDisplayMemory.appendVariable(CalculatorVariable.Y)
        CalculatorDisplayMemory.appendDigit('1')
        assertEquals("Y*1", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("5", CalculatorDisplayMemory.allSubmitted().last().result)

        YEqualsMemory.append("X+1")
        submitRaw(ExpressionEditingTokens.yFunctionToken(1))
        assertEquals("1", CalculatorDisplayMemory.allSubmitted().last().result)
        assertEquals(4.0, CalculatorDisplayMemory.evaluateForGraph("Y₁", 3.0))

        YEqualsMemory.select(1)
        YEqualsMemory.append("Y")
        YEqualsMemory.appendDigit('2')
        assertEquals("Y*2", YEqualsMemory.equation(1))
    }

    @Test
    fun quitClosesTheFunctionMenuWithoutLeavingItsEditableView() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        dispatchAlpha(controller, CalculatorKey.WINDOW)
        assertEquals(CalculatorView.Y_EQUALS, controller.state.view)
        assertTrue(controller.state.functionMenu != null)

        dispatchSecond(controller, CalculatorKey.MODE)

        assertEquals(CalculatorView.Y_EQUALS, controller.state.view)
        assertEquals(null, controller.state.functionMenu)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        dispatchSecond(controller, CalculatorKey.MODE)
        assertEquals(CalculatorView.HOME, controller.state.view)
    }

    @Test
    fun fractionTemplatesProduceReducedFractionsUnlessDecimalInputIsPresent() {
        val controller = CalculatorController()
        dispatchAlpha(controller, CalculatorKey.Y_EQUALS)
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        assertTrue(controller.state.fractionTemplate != null)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_1))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("frac(1,2)", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("1/2", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("frac(1,2)+frac(1,3)")
        assertEquals("5/6", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("frac(1,2)+0.25")
        assertEquals("0.75", CalculatorDisplayMemory.allSubmitted().last().result)

        submitRaw("mixed(2,1,4)")
        assertEquals("9/4", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun alphaVariableOpensTheDirectFractionTemplateInTheCorrectEditor() {
        val controller = CalculatorController()
        controller.dispatch(CalculatorInputEvent(CalculatorKey.Y_EQUALS))
        dispatchAlpha(controller, CalculatorKey.VARIABLE)

        val template = controller.state.fractionTemplate!!
        assertFalse(template.mixedNumber)
        assertEquals(CalculatorView.Y_EQUALS, controller.state.view)
        assertEquals(CalculatorView.Y_EQUALS, template.targetView)
        assertEquals(ModifierLayer.NORMAL, controller.state.modifier)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_3))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_4))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals("frac(3,4)", YEqualsMemory.equation(0))
        assertEquals(null, controller.state.fractionTemplate)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.MODE))
        dispatchAlpha(controller, CalculatorKey.VARIABLE)
        assertEquals(CalculatorView.HOME, controller.state.view)
        assertEquals(CalculatorView.HOME, controller.state.fractionTemplate!!.targetView)
    }

    @Test
    fun completedStructuredFractionsRemainAtomicAcrossEditableViews() {
        val fraction = "frac(1,2)"
        CalculatorDisplayMemory.appendMenuToken(fraction)
        CalculatorDisplayMemory.moveCursorLeft()
        assertEquals(0, CalculatorDisplayMemory.cursorPosition())
        CalculatorDisplayMemory.moveCursorRight()
        assertEquals(fraction.length, CalculatorDisplayMemory.cursorPosition())

        YEqualsMemory.append(fraction)
        YEqualsMemory.moveCursorLeft()
        assertEquals(0, YEqualsMemory.cursor(0))
        YEqualsMemory.moveCursorRight()
        assertEquals(fraction.length, YEqualsMemory.cursor(0))

        WindowSettingsMemory.clearSelected()
        WindowSettingsMemory.append(fraction)
        WindowSettingsMemory.moveCursorLeft()
        assertEquals(0, WindowSettingsMemory.cursor(0))
        WindowSettingsMemory.deleteAtCursor()
        assertEquals("", WindowSettingsMemory.value(0))
    }

    @Test
    fun leftReopensACompletedFractionAtDenominatorThenNumerator() {
        val original = "frac(12,34)"
        CalculatorDisplayMemory.appendMenuToken(original)
        val controller = CalculatorController()

        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        val template = controller.state.fractionTemplate!!
        assertEquals(1, template.selectedFieldIndex)
        assertEquals("34", template.field(1))
        assertEquals(0, template.cursor(1))
        assertEquals(original, CalculatorDisplayMemory.current())

        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        assertEquals(0, template.selectedFieldIndex)
        assertEquals("12", template.field(0))
        assertEquals(1, template.cursor(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DOWN))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.CLEAR))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))
        assertEquals("frac(12,5)", CalculatorDisplayMemory.current())
        assertEquals(null, controller.state.fractionTemplate)
    }

    @Test
    fun leftFromTheFirstNumeratorElementExitsBeforeTheCompletedFraction() {
        val original = "frac(12,34)"
        CalculatorDisplayMemory.appendMenuToken(original)
        val controller = CalculatorController()

        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.LEFT))

        assertEquals(null, controller.state.fractionTemplate)
        assertEquals(original, CalculatorDisplayMemory.current())
        assertEquals(0, CalculatorDisplayMemory.cursorPosition())
    }

    @Test
    fun rightBeforeACompletedFractionReopensNumeratorFirstAndExitsAfterDenominator() {
        val original = "frac(12,34)"
        CalculatorDisplayMemory.appendMenuToken(original)
        CalculatorDisplayMemory.moveCursorLeft()
        val controller = CalculatorController()

        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        val template = controller.state.fractionTemplate!!
        assertEquals(0, template.selectedFieldIndex)
        assertEquals(0, template.cursor(0))

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_9))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        assertEquals(1, template.selectedFieldIndex)
        assertEquals(0, template.cursor(1))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.RIGHT))

        assertEquals(null, controller.state.fractionTemplate)
        assertEquals("frac(92,54)", CalculatorDisplayMemory.current())
        assertEquals("frac(92,54)".length, CalculatorDisplayMemory.cursorPosition())
    }

    @Test
    fun recallingAFractionResultPreservesItsStructuredFractionBehavior() {
        submitRaw("frac(1,2)")
        val controller = CalculatorController()

        controller.dispatch(CalculatorInputEvent(CalculatorKey.UP))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ENTER))

        assertEquals("frac(1,2)", CalculatorDisplayMemory.current())
        CalculatorDisplayMemory.submit()
        assertEquals("1/2", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun functionMenuScalarOperationsEvaluateAndUnavailableRowsStayOpen() {
        val controller = CalculatorController()
        dispatchAlpha(controller, CalculatorKey.WINDOW)
        val menu = controller.state.functionMenu!!
        assertEquals(FunctionMenuTab.FUNC, menu.selectedTab)
        assertEquals(10, menu.items.size)
        assertFalse(menu.items[1].available)
        assertFalse(menu.items[2].available)
        assertFalse(menu.items[3].available)

        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_2))
        assertEquals(menu, controller.state.functionMenu)
        assertEquals("", CalculatorDisplayMemory.current())
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DIGIT_5))
        assertEquals("logBASE(", CalculatorDisplayMemory.current())

        CalculatorDisplayMemory.clearCurrent()
        listOf(
            "abs(-5)" to "5",
            "logBASE(8,2)" to "3",
            "nthRoot(27,3)" to "3",
            "root(3,27)" to "3",
            "nPr(5,2)" to "20",
            "nCr(5,2)" to "10",
            "5!" to "120"
        ).forEach { (expression, expected) ->
            submitRaw(expression)
            assertEquals(expected, CalculatorDisplayMemory.allSubmitted().last().result)
        }
    }

    private fun submitRaw(expression: String) {
        CalculatorDisplayMemory.appendRecalledHistory(expression)
        CalculatorDisplayMemory.submit()
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

    private fun dispatchAlpha(controller: CalculatorController, key: CalculatorKey) {
        controller.dispatch(CalculatorInputEvent(CalculatorKey.ALPHA))
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

    private fun setField(target: Any, name: String, value: Any?) {
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
