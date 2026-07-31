package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.amathboi.mi84mod.client.calculator.controller.ListEditorController
import net.amathboi.mi84mod.client.calculator.controller.CalculatorController
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.ListEditorState

class CalculatorListTest {
    // List memory initializes Fabric's config path; make this test independent of execution order.
    private val testConfig = TestFabricEnvironment.configDir

    @BeforeTest
    fun resetLists() {
        Files.deleteIfExists(testConfig.resolve("mi84_calc_lists.txt"))
        resetListMemoryInMemory()
    }

    @AfterTest
    fun removeSavedLists() {
        Files.deleteIfExists(testConfig.resolve("mi84_calc_lists.txt"))
    }

    @Test
    fun parsesNestedListLiteralElementsWithoutSplittingTheirArguments() {
        val parsed = CalculatorListLiteral.parse("{1,f(2,3),4}") { element ->
            CalculatorScalarValue(BigDecimal(element.filter(Char::isDigit).ifEmpty { "0" }))
        }

        assertEquals(listOf("1", "23", "4"), parsed.values.map { it.real.toPlainString() })
    }

    @Test
    fun supportsCoreListOperationsAndRejectsComplexOrdering() {
        val list = CalculatorListValue(listOf("3", "1", "2").map { CalculatorScalarValue(BigDecimal(it)) })
        assertEquals("6", CalculatorListOperations.sum(list).toPlainString())
        assertEquals("2", CalculatorListOperations.mean(list).stripTrailingZeros().toPlainString())
        assertEquals(listOf("1", "2", "3"), CalculatorListOperations.sortAscending(list).values.map { it.real.toPlainString() })
        assertEquals(listOf("-2", "1"), CalculatorListOperations.deltaList(
            CalculatorListValue(listOf("3", "1", "2").map { CalculatorScalarValue(BigDecimal(it)) })
        ).values.map { it.real.toPlainString() })
        assertFailsWith<IllegalArgumentException> {
            CalculatorListOperations.sortAscending(CalculatorListValue(listOf(CalculatorScalarValue(BigDecimal.ONE, BigDecimal.ONE))))
        }
    }

    @Test
    fun listEditorCommitsTheBottomEntryAndMaintainsTheNextEmptyRow() {
        CalculatorListMemory.clear(CalculatorListName.L1)
        val state = ListEditorState()
        ListEditorController.handle(CalculatorCommand.Digit('5'), state)
        ListEditorController.handle(CalculatorCommand.Digit('5'), state)
        ListEditorController.handle(CalculatorCommand.Enter, state)

        assertEquals("55", CalculatorListMemory.value(CalculatorListName.L1).values.single().real.toPlainString())
        assertEquals(1, state.selectedRowIndex)
        assertEquals("", state.entry)
        ListEditorController.handle(CalculatorCommand.Down, state)
        assertEquals(1, state.selectedRowIndex)
        repeat(6) { ListEditorController.handle(CalculatorCommand.Right, state) }
        assertEquals("L6", ListEditorController.selectedName(state))
        assertEquals(3, state.firstVisibleListIndex)
        CalculatorListMemory.clear(CalculatorListName.L1)
    }

    @Test
    fun listHeaderLoadsAndValidatesTheEditableLiteral() {
        CalculatorListMemory.set(
            CalculatorListName.L1,
            CalculatorListValue(listOf("2", "2", "2").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        val state = ListEditorState()
        ListEditorController.handle(CalculatorCommand.Up, state)
        assertTrue(ListEditorController.editingHeader(state))
        assertEquals("{2,2,2}", state.entry)
        assertFalse(state.headerEntryLocked)

        ListEditorController.handle(CalculatorCommand.Clear, state)
        assertTrue(state.headerEntryLocked)
        assertEquals("", state.entry)
        state.entry = "{2,2,2}"

        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertFalse(state.headerEntryLocked)
        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertTrue(state.headerEntryLocked)
        state.entry = "{8,9"
        state.entryCursor = state.entry.length
        ListEditorController.handle(CalculatorCommand.CloseListLiteral, state)
        assertEquals("{8,9}", state.entry)
        state.entry = "{4,5}"
        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertEquals("{4,5}", state.entry)
        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertTrue(state.headerEntryLocked)
        state.entry = "{4,,5}"
        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertEquals("{4,5}", state.entry)

        state.entry = ""
        ListEditorController.handle(CalculatorCommand.Enter, state)
        assertEquals(0, CalculatorListMemory.value(CalculatorListName.L1).dimension)
    }

    @Test
    fun headerInsertionCreatesAFiveCharacterBoundedNamedListToTheLeft() {
        val state = ListEditorState().also { it.selectedRowIndex = -1 }
        val controller = CalculatorController().also {
            it.state.view = CalculatorView.LIST_EDITOR
            it.state.listEditor = state
        }
        controller.dispatch(CalculatorInputEvent(CalculatorKey.SECOND))
        controller.dispatch(CalculatorInputEvent(CalculatorKey.DELETE))
        assertEquals("_", ListEditorController.names(state).first())
        "ALPHAZ".forEach { character ->
            val variable = CalculatorVariable.fromSymbol(character)!!
            ListEditorController.handle(CalculatorCommand.InsertVariable(variable), state)
        }
        ListEditorController.handle(CalculatorCommand.Enter, state)

        assertEquals("ALPHA", ListEditorController.selectedName(state))
        assertEquals(0, CalculatorListMemory.value("ALPHA")!!.dimension)
        assertEquals("ALPHA", CalculatorListMemory.names().first())
    }

    @Test
    fun namedListReferencesDoNotCollideWithAlphaScalarVariables() {
        assertTrue(CalculatorListMemory.createNamed("A"))
        CalculatorListMemory.set(
            "A",
            CalculatorListValue(listOf(CalculatorScalarValue(BigDecimal("8"))))
        )
        CalculatorVariableMemory.set(CalculatorVariable.A, BigDecimal("3"))
        fun submit(expression: String): String {
            CalculatorDisplayMemory.appendMenuToken(expression)
            CalculatorDisplayMemory.submit()
            return CalculatorDisplayMemory.allSubmitted().last().result
        }

        assertEquals("3", submit("A"))
        assertEquals("{8}", submit("@A"))
    }

    @Test
    fun homeEvaluationDisplaysABuiltInListWithSpaceSeparatedElements() {
        CalculatorListMemory.set(
            CalculatorListName.L1,
            CalculatorListValue(listOf("8", "6").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        CalculatorDisplayMemory.appendMenuToken("L1")
        CalculatorDisplayMemory.submit()

        assertEquals("{8 6}", CalculatorDisplayMemory.allSubmitted().last().result)
        CalculatorListMemory.clear(CalculatorListName.L1)
    }

    @Test
    fun homeEvaluationDisplaysAListLiteralWithScalarExpressions() {
        CalculatorDisplayMemory.appendMenuToken("{2+2,3*2}")
        CalculatorDisplayMemory.submit()

        assertEquals("{4 6}", CalculatorDisplayMemory.allSubmitted().last().result)
    }

    @Test
    fun homeEvaluationSupportsReviewedListOpsAndMath() {
        CalculatorListMemory.set(
            CalculatorListName.L1,
            CalculatorListValue(listOf("3", "1", "2").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        fun submit(expression: String): String {
            CalculatorDisplayMemory.appendMenuToken(expression)
            CalculatorDisplayMemory.submit()
            return CalculatorDisplayMemory.allSubmitted().last().result
        }

        assertEquals("{1 2 3}", submit("SortA(L1)"))
        assertEquals("{1 3 6}", submit("cumSum(L1)"))
        assertEquals("6", submit("sum(L1)"))
        assertEquals("2", submit("mean(L1)"))
        assertEquals("{2 3 4}", submit("seq(2,4)"))
        assertEquals("{1 4 9}", submit("seq(X^2,X,1,3)"))
        assertEquals("{9 9 9}", submit("Fill(9,L1)"))
        assertEquals(listOf("9", "9", "9"), CalculatorListMemory.value(CalculatorListName.L1).values.map { it.real.toPlainString() })
        CalculatorListMemory.clear(CalculatorListName.L1)
    }

    @Test
    fun listFunctionsAutocloseAndEditAsWholeTokens() {
        CalculatorListMemory.set(
            CalculatorListName.L1,
            CalculatorListValue(listOf("8", "6").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        CalculatorDisplayMemory.appendMenuToken("min(L1")
        CalculatorDisplayMemory.submit()
        assertEquals("6", CalculatorDisplayMemory.allSubmitted().last().result)

        CalculatorDisplayMemory.appendMenuToken("SortA(")
        assertEquals(6, CalculatorDisplayMemory.cursorPosition())
        CalculatorDisplayMemory.moveCursorLeft()
        assertEquals(0, CalculatorDisplayMemory.cursorPosition())
        CalculatorDisplayMemory.deleteAtCursor()
        assertEquals("", CalculatorDisplayMemory.current())
        CalculatorListMemory.clear(CalculatorListName.L1)
    }

    @Test
    fun namedListValuesAndTableOrderReloadFromPersistence() {
        assertTrue(CalculatorListMemory.createNamed("SAVE", before = "L2"))
        assertTrue(
            CalculatorListMemory.set(
                "SAVE",
                CalculatorListValue(listOf(CalculatorScalarValue(BigDecimal("1.23456789"))))
            )
        )

        resetListMemoryInMemory()
        CalculatorListMemory.javaClass.getDeclaredMethod("load").apply {
            isAccessible = true
        }.invoke(CalculatorListMemory)

        assertEquals(listOf("L1", "SAVE", "L2"), CalculatorListMemory.names().take(3))
        assertEquals("1.23456789", CalculatorListMemory.value("SAVE")!!.values.single().real.toPlainString())
    }

    @Test
    fun listCellsEvaluateFunctionsAndRetainRawPrecision() {
        val state = ListEditorState()
        ListEditorController.handle(CalculatorCommand.Function("sin"), state)
        ListEditorController.handle(CalculatorCommand.Digit('0'), state)
        ListEditorController.handle(CalculatorCommand.Enter, state)
        ListEditorController.handle(CalculatorCommand.Digit('1'), state)
        ListEditorController.handle(CalculatorCommand.Operator('/'), state)
        ListEditorController.handle(CalculatorCommand.Digit('3'), state)
        ListEditorController.handle(CalculatorCommand.Enter, state)

        val stored = CalculatorListMemory.value(CalculatorListName.L1).values
        assertEquals(0, stored[0].real.compareTo(BigDecimal.ZERO))
        assertTrue(stored[1].real.toPlainString().length > 10)
    }

    @Test
    fun dependentListsFollowThePrimarySortPermutation() {
        CalculatorListMemory.set(
            CalculatorListName.L1,
            CalculatorListValue(listOf("3", "1", "2").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        CalculatorListMemory.set(
            CalculatorListName.L2,
            CalculatorListValue(listOf("30", "10", "20").map { CalculatorScalarValue(BigDecimal(it)) })
        )
        CalculatorDisplayMemory.appendMenuToken("SortA(L1,L2)")
        CalculatorDisplayMemory.submit()

        assertEquals(listOf("1", "2", "3"), CalculatorListMemory.value(CalculatorListName.L1).values.map { it.real.toPlainString() })
        assertEquals(listOf("10", "20", "30"), CalculatorListMemory.value(CalculatorListName.L2).values.map { it.real.toPlainString() })
    }

    @Suppress("UNCHECKED_CAST")
    private fun resetListMemoryInMemory() {
        val values = CalculatorListMemory.javaClass.getDeclaredField("values").apply {
            isAccessible = true
        }.get(CalculatorListMemory) as MutableMap<CalculatorListName, CalculatorListValue>
        CalculatorListName.entries.forEach { values[it] = CalculatorListValue(emptyList()) }
        (CalculatorListMemory.javaClass.getDeclaredField("namedValues").apply {
            isAccessible = true
        }.get(CalculatorListMemory) as MutableMap<String, CalculatorListValue>).clear()
        val order = CalculatorListMemory.javaClass.getDeclaredField("listOrder").apply {
            isAccessible = true
        }.get(CalculatorListMemory) as MutableList<String>
        order.clear()
        order += CalculatorListName.entries.map(CalculatorListName::token)
    }
}
