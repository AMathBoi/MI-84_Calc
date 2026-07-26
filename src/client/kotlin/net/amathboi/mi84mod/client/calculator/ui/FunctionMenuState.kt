package net.amathboi.mi84mod.client.calculator.ui

import net.amathboi.mi84mod.client.calculator.ExpressionEditingTokens

enum class FunctionMenuTab(val label: String) {
    FRAC("FRAC"),
    FUNC("FUNC"),
    MTRX("MTRX"),
    YVAR("YVAR")
}

sealed interface FunctionMenuAction {
    data class InsertToken(val token: String) : FunctionMenuAction
    data class BeginFractionTemplate(val mixedNumber: Boolean) : FunctionMenuAction
    data object Unavailable : FunctionMenuAction
}

data class FunctionMenuItem(
    val hotkey: String,
    val label: String,
    val action: FunctionMenuAction
) {
    val available: Boolean get() = action != FunctionMenuAction.Unavailable
}

/** Approved F1–F4 bottom-tab overlay content. F3/F4 stay visible but unavailable for now. */
object FunctionMenuDefinitions {
    private val items = mapOf(
        FunctionMenuTab.FRAC to listOf(
            FunctionMenuItem("1", "n/d", FunctionMenuAction.BeginFractionTemplate(false)),
            FunctionMenuItem("2", "Un/d", FunctionMenuAction.BeginFractionTemplate(true)),
            FunctionMenuItem("3", "►F↔D", FunctionMenuAction.Unavailable),
            FunctionMenuItem("4", "►n/d↔Un/d", FunctionMenuAction.Unavailable)
        ),
        FunctionMenuTab.FUNC to listOf(
            FunctionMenuItem("1", "abs(", FunctionMenuAction.InsertToken("abs(")),
            FunctionMenuItem("2", "nDeriv(", FunctionMenuAction.Unavailable),
            FunctionMenuItem("3", "fnInt(", FunctionMenuAction.Unavailable),
            FunctionMenuItem("4", "Σ(", FunctionMenuAction.Unavailable),
            FunctionMenuItem("5", "logBASE(", FunctionMenuAction.InsertToken("logBASE(")),
            FunctionMenuItem("6", "sqrt(", FunctionMenuAction.InsertToken("sqrt(")),
            FunctionMenuItem("7", "nthRoot(", FunctionMenuAction.InsertToken("nthRoot(")),
            FunctionMenuItem("8", "nPr(", FunctionMenuAction.InsertToken("nPr(")),
            FunctionMenuItem("9", "nCr(", FunctionMenuAction.InsertToken("nCr(")),
            FunctionMenuItem("0", "!", FunctionMenuAction.InsertToken("!"))
        ),
        FunctionMenuTab.MTRX to listOf(
            FunctionMenuItem("", "MTRX not implemented", FunctionMenuAction.Unavailable)
        ),
        FunctionMenuTab.YVAR to listOf(
            FunctionMenuItem("", "YVAR not implemented", FunctionMenuAction.Unavailable)
        )
    )

    fun items(tab: FunctionMenuTab): List<FunctionMenuItem> = items.getValue(tab)
}

/** Transient tab and row selection for an overlay that retains its editable origin view. */
class FunctionMenuState(
    initialTab: FunctionMenuTab,
    val targetView: CalculatorView
) {
    var selectedTabIndex = initialTab.ordinal
        private set
    private val selectedItemIndices = MutableList(FunctionMenuTab.entries.size) { 0 }

    val selectedTab: FunctionMenuTab get() = FunctionMenuTab.entries[selectedTabIndex]
    val items: List<FunctionMenuItem> get() = FunctionMenuDefinitions.items(selectedTab)

    var selectedItemIndex: Int
        get() = selectedItemIndices[selectedTabIndex]
        set(value) {
            selectedItemIndices[selectedTabIndex] = value.coerceIn(items.indices)
        }

    val selectedItem: FunctionMenuItem get() = items[selectedItemIndex]

    fun selectTab(tab: FunctionMenuTab) {
        selectedTabIndex = tab.ordinal
    }

    fun selectPreviousTab() {
        selectedTabIndex = (selectedTabIndex - 1).coerceAtLeast(0)
    }

    fun selectNextTab() {
        selectedTabIndex =
            (selectedTabIndex + 1).coerceAtMost(FunctionMenuTab.entries.lastIndex)
    }

    fun selectPreviousItem() {
        selectedItemIndex--
    }

    fun selectNextItem() {
        selectedItemIndex++
    }

    fun selectHotkey(hotkey: String): Boolean {
        val index = items.indexOfFirst { it.hotkey == hotkey }
        if (index < 0) return false
        selectedItemIndex = index
        return true
    }
}

/**
 * Structured fraction entry stays transient until it is complete, then emits a linear evaluator
 * token into the originating editor.
 */
class FractionTemplateState(
    val mixedNumber: Boolean,
    val targetView: CalculatorView,
    initialValues: List<String>? = null,
    val replacementStart: Int? = null,
    val originalToken: String? = null
) {
    private val values =
        initialValues?.toMutableList() ?: MutableList(if (mixedNumber) 3 else 2) { "" }
    private val cursors = MutableList(values.size) { 0 }
    var selectedFieldIndex = if (originalToken == null) 0 else values.lastIndex
        private set

    init {
        require(values.size == if (mixedNumber) 3 else 2)
    }

    fun fieldCount(): Int = values.size

    fun field(index: Int): String = values[index]

    fun cursor(index: Int): Int = cursors[index]

    fun append(text: String, insertMode: Boolean = false) {
        if (text.isEmpty()) return
        val value = values[selectedFieldIndex]
        val cursor = cursors[selectedFieldIndex]
        val replacedLength = if (!insertMode && cursor < value.length) 1 else 0
        if (value.length - replacedLength + text.length > MAX_FIELD_LENGTH) return

        values[selectedFieldIndex] =
            value.substring(0, cursor) + text + value.substring(cursor + replacedLength)
        cursors[selectedFieldIndex] += text.length
    }

    fun toggleSign() {
        val value = values[selectedFieldIndex]
        values[selectedFieldIndex] = if (value.startsWith("-")) {
            cursors[selectedFieldIndex] = (cursors[selectedFieldIndex] - 1).coerceAtLeast(0)
            value.removePrefix("-")
        } else if (value.length < MAX_FIELD_LENGTH) {
            cursors[selectedFieldIndex]++
            "-$value"
        } else {
            value
        }
    }

    fun deleteAtCursor() {
        val value = values[selectedFieldIndex]
        val cursor = cursors[selectedFieldIndex]
        if (cursor < value.length) {
            values[selectedFieldIndex] = value.removeRange(cursor, cursor + 1)
        }
    }

    fun clearSelectedField(): Boolean {
        if (values[selectedFieldIndex].isEmpty()) return false
        values[selectedFieldIndex] = ""
        cursors[selectedFieldIndex] = 0
        return true
    }

    fun moveLeft() {
        val cursor = cursors[selectedFieldIndex]
        if (cursor > 0) {
            cursors[selectedFieldIndex]--
        } else if (selectedFieldIndex > 0) {
            selectedFieldIndex--
            cursors[selectedFieldIndex] =
                (values[selectedFieldIndex].length - 1).coerceAtLeast(0)
        }
    }

    /** Returns true when movement passes the final field and the template should be committed. */
    fun moveRight(): Boolean {
        val cursor = cursors[selectedFieldIndex]
        if (cursor < values[selectedFieldIndex].length) {
            cursors[selectedFieldIndex]++
        } else if (selectedFieldIndex < values.lastIndex) {
            selectedFieldIndex++
            cursors[selectedFieldIndex] = 0
        } else {
            return true
        }
        return false
    }

    fun moveUp() {
        if (selectedFieldIndex > 0) {
            selectedFieldIndex--
            cursors[selectedFieldIndex] =
                cursors[selectedFieldIndex].coerceAtMost(
                    (values[selectedFieldIndex].length - 1).coerceAtLeast(0)
                )
        }
    }

    /** Returns true when Down passes the final field and the template should be committed. */
    fun moveDown(): Boolean {
        if (selectedFieldIndex == values.lastIndex) return true
        selectedFieldIndex++
        cursors[selectedFieldIndex] =
            cursors[selectedFieldIndex].coerceAtMost(values[selectedFieldIndex].length)
        return false
    }

    fun completedExpression(): String? {
        if (values.any(String::isBlank)) return null
        return if (mixedNumber) {
            "mixed(${values[0]},${values[1]},${values[2]})"
        } else {
            "frac(${values[0]},${values[1]})"
        }
    }

    companion object {
        private const val MAX_FIELD_LENGTH = 12

        fun reopen(
            token: String,
            start: Int,
            targetView: CalculatorView
        ): FractionTemplateState? {
            val fraction = ExpressionEditingTokens.parseStructuredFraction(token) ?: return null
            return FractionTemplateState(
                fraction.mixedNumber,
                targetView,
                fraction.fields,
                start,
                token
            )
        }
    }
}
