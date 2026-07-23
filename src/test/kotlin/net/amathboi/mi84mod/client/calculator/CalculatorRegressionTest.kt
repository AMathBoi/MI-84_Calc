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
import kotlin.test.assertTrue

class CalculatorRegressionTest {
    @BeforeTest
    fun resetCalculatorState() {
        resetDisplayMemory()
        resetModeMemory()
        WindowSettingsMemory.restore(DEFAULT_WINDOW)
    }

    @AfterTest
    fun removeSavedTestState() {
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_display_memory.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_mode_settings.txt"))
        Files.deleteIfExists(TEST_CONFIG.resolve("mi84_calc_window_settings.txt"))
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
        repeat(4) { ModeSettingsMemory.selectNextCategory() }
        ModeSettingsMemory.selectNextOption()
    }

    private fun selectDegreeMode() {
        repeat(2) { ModeSettingsMemory.selectNextCategory() }
        ModeSettingsMemory.selectPreviousOption()
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

    companion object {
        private val TEST_CONFIG: Path = TestFabricEnvironment.configDir
        private val DEFAULT_MODE_OPTIONS = listOf(0, 0, 1, 0, 0, 0, 0, 0, 0)
        private val DEFAULT_WINDOW =
            listOf("-10", "10", "1", "-10", "10", "1", "1", "5/66", "5/33")

    }
}
