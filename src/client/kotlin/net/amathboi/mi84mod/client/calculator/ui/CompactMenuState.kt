package net.amathboi.mi84mod.client.calculator.ui

import net.amathboi.mi84mod.client.calculator.ExpressionEditingTokens
import net.amathboi.mi84mod.client.calculator.CalculatorListName
import net.amathboi.mi84mod.client.calculator.CalculatorListMemory

enum class CompactMenuId {
    MATH,
    TEST,
    ANGLE,
    VARS,
    VARS_WINDOW,
    VARS_ZOOM,
    Y_VARS_FUNCTION,
    STAT,
    LIST
}

sealed interface CompactMenuAction {
    data class InsertToken(val token: String) : CompactMenuAction
    data class BeginFractionTemplate(val mixedNumber: Boolean) : CompactMenuAction
    data class OpenSubmenu(val definition: CompactMenuDefinition) : CompactMenuAction
    data object OpenListEditor : CompactMenuAction
    data object Unavailable : CompactMenuAction
}

data class CompactMenuItem(
    val hotkey: String,
    val label: String,
    val action: CompactMenuAction = CompactMenuAction.Unavailable
) {
    val available: Boolean get() = action != CompactMenuAction.Unavailable
}

data class CompactMenuTab(
    val label: String,
    val items: List<CompactMenuItem>
)

data class CompactMenuDefinition(
    val id: CompactMenuId,
    val tabs: List<CompactMenuTab>
)

/** Immutable compact-menu content kept outside rendering and controller routing. */
object CompactMenuDefinitions {
    private fun token(hotkey: String, label: String, insertedToken: String = label) =
        CompactMenuItem(hotkey, label, CompactMenuAction.InsertToken(insertedToken))

    private fun fraction(hotkey: String, label: String, mixedNumber: Boolean) =
        CompactMenuItem(
            hotkey,
            label,
            CompactMenuAction.BeginFractionTemplate(mixedNumber)
        )

    private fun unavailable(hotkey: String, label: String) =
        CompactMenuItem(hotkey, label)

    private fun listEditor(hotkey: String, label: String) =
        CompactMenuItem(hotkey, label, CompactMenuAction.OpenListEditor)

    private fun submenu(
        hotkey: String,
        label: String,
        definition: CompactMenuDefinition
    ) = CompactMenuItem(hotkey, label, CompactMenuAction.OpenSubmenu(definition))

    val math = CompactMenuDefinition(
        CompactMenuId.MATH,
        listOf(
            CompactMenuTab(
                "MATH",
                listOf(
                    unavailable("1", "►Frac"),
                    unavailable("2", "►Dec"),
                    token("3", "^3"),
                    token("4", "³√(", "cubeRoot("),
                    token("5", "x√(", "root("),
                    unavailable("6", "fMin("),
                    unavailable("7", "fMax("),
                    unavailable("8", "nDeriv("),
                    unavailable("9", "fnInt("),
                    unavailable("0", "Σ("),
                    token("A", "logBASE("),
                    unavailable("B", "piecewise("),
                    unavailable("C", "Numeric Solver...")
                )
            ),
            CompactMenuTab(
                "NUM",
                listOf(
                    token("1", "abs("),
                    token("2", "round("),
                    token("3", "iPart("),
                    token("4", "fPart("),
                    token("5", "int("),
                    token("6", "min("),
                    token("7", "max("),
                    token("8", "lcm("),
                    token("9", "gcd("),
                    token("0", "remainder("),
                    unavailable("A", "►Un/d"),
                    unavailable("B", "►Dec"),
                    fraction("C", "Un/d", mixedNumber = true),
                    fraction("D", "n/d", mixedNumber = false)
                )
            ),
            CompactMenuTab(
                "CMPLX",
                listOf(
                    unavailable("1", "conj("),
                    unavailable("2", "real("),
                    unavailable("3", "imag("),
                    unavailable("4", "angle("),
                    token("5", "abs("),
                    unavailable("6", "►Rect"),
                    unavailable("7", "►Polar")
                )
            ),
            CompactMenuTab(
                "PROB",
                listOf(
                    unavailable("1", "rand"),
                    token("2", "nPr(", "nPr("),
                    token("3", "nCr(", "nCr("),
                    token("4", "!"),
                    unavailable("5", "randInt("),
                    unavailable("6", "randNorm("),
                    unavailable("7", "randBin("),
                    unavailable("8", "randIntNoRep(")
                )
            ),
            CompactMenuTab(
                "FRAC",
                listOf(
                    fraction("1", "n/d", mixedNumber = false),
                    fraction("2", "Un/d", mixedNumber = true),
                    unavailable("3", "►Dec"),
                    unavailable("4", "►Un/d")
                )
            )
        )
    )

    val test = CompactMenuDefinition(
        CompactMenuId.TEST,
        listOf(
            CompactMenuTab(
                "TEST",
                listOf("=", "≠", ">", "≥", "<", "≤").mapIndexed { index, symbol ->
                    token((index + 1).toString(), symbol)
                }
            ),
            CompactMenuTab(
                "LOGIC",
                listOf("and", "or", "xor", "not(").mapIndexed { index, operator ->
                    token((index + 1).toString(), operator)
                }
            ),
            CompactMenuTab(
                "CONDITIONS",
                listOf(
                    "lower < X < upper",
                    "lower < X ≤ upper",
                    "lower ≤ X < upper",
                    "lower ≤ X ≤ upper",
                    "outside / open",
                    "outside / mixed"
                ).mapIndexed { index, label ->
                    CompactMenuItem((index + 1).toString(), label)
                }
            )
        )
    )

    val angle = CompactMenuDefinition(
        CompactMenuId.ANGLE,
        listOf(
            CompactMenuTab(
                "ANGLE",
                listOf(
                    token("1", "°"),
                    unavailable("2", "'"),
                    token("3", "ʳ"),
                    unavailable("4", "►DMS"),
                    token("5", "R►Pr("),
                    token("6", "R►Pθ("),
                    token("7", "P►Rx("),
                    token("8", "P►Ry(")
                )
            )
        )
    )

    val windowVariables = CompactMenuDefinition(
        CompactMenuId.VARS_WINDOW,
        listOf(
            CompactMenuTab(
                "X/Y",
                listOf(
                    token("1", "Xmin"),
                    token("2", "Xmax"),
                    token("3", "Xscl"),
                    token("4", "Ymin"),
                    token("5", "Ymax"),
                    token("6", "Yscl"),
                    token("7", "Xres"),
                    token("8", "ΔX"),
                    unavailable("9", "ΔY"),
                    unavailable("0", "XFact"),
                    unavailable("A", "YFact"),
                    token("B", "TraceStep")
                )
            ),
            CompactMenuTab(
                "T/θ",
                listOf(unavailable("1", "T/θ variables"))
            ),
            CompactMenuTab(
                "U/V/W",
                listOf(unavailable("1", "U/V/W variables"))
            )
        )
    )

    val zoomVariables = CompactMenuDefinition(
        CompactMenuId.VARS_ZOOM,
        listOf(
            CompactMenuTab(
                "ZX/ZY",
                listOf(
                    token("1", "ZXmin"),
                    token("2", "ZXmax"),
                    token("3", "ZXscl"),
                    token("4", "ZYmin"),
                    token("5", "ZYmax"),
                    token("6", "ZYscl"),
                    token("7", "ZXres")
                )
            ),
            CompactMenuTab(
                "ZT/Zθ",
                listOf(unavailable("1", "ZT/Zθ variables"))
            ),
            CompactMenuTab(
                "ZU",
                listOf(unavailable("1", "ZU variables"))
            )
        )
    )

    val functionVariables = CompactMenuDefinition(
        CompactMenuId.Y_VARS_FUNCTION,
        listOf(
            CompactMenuTab(
                "FUNCTION",
                (1..9).map { index ->
                    token(
                        index.toString(),
                        "Y$index",
                        ExpressionEditingTokens.yFunctionToken(index)
                    )
                }
            )
        )
    )

    val vars = CompactMenuDefinition(
        CompactMenuId.VARS,
        listOf(
            CompactMenuTab(
                "VARS",
                listOf(
                    submenu("1", "Window...", windowVariables),
                    submenu("2", "Zoom...", zoomVariables),
                    unavailable("3", "GDB..."),
                    unavailable("4", "Picture..."),
                    unavailable("5", "Image..."),
                    unavailable("6", "String..."),
                    unavailable("7", "Table..."),
                    unavailable("8", "Statistics...")
                )
            ),
            CompactMenuTab(
                "Y-VARS",
                listOf(
                    submenu("1", "Function...", functionVariables),
                    unavailable("2", "Parametric..."),
                    unavailable("3", "Polar..."),
                    unavailable("4", "On/Off..."),
                    unavailable("5", "Sequence..."),
                    unavailable("6", "Background...")
                )
            )
        )
    )

    val stat = CompactMenuDefinition(
        CompactMenuId.STAT,
        listOf(
            CompactMenuTab(
                "EDIT",
                listOf(
                    listEditor("1", "Edit..."),
                    unavailable("2", "SortA("),
                    unavailable("3", "SortD("),
                    unavailable("4", "ClrList"),
                    unavailable("5", "SetUpEditor")
                )
            ),
            CompactMenuTab("CALC", listOf(unavailable("1", "Statistics..."))),
            CompactMenuTab("TESTS", listOf(unavailable("1", "Tests...")))
        )
    )

    fun list() = CompactMenuDefinition(
        CompactMenuId.LIST,
        listOf(
            CompactMenuTab(
                "NAMES",
                (CalculatorListName.entries.map(CalculatorListName::token) + CalculatorListMemory.namedNames())
                    .mapIndexed { index, name ->
                    token(
                        if (index < 6) (index + 1).toString() else ('A' + index - 6).toString(),
                        name,
                        CalculatorListMemory.referenceToken(name)
                    )
                }
            ),
            CompactMenuTab(
                "OPS",
                listOf(
                    token("1", "SortA("),
                    token("2", "SortD("),
                    token("3", "dim("),
                    token("4", "Fill("),
                    token("5", "seq("),
                    token("6", "cumSum("),
                    token("7", "ΔList("),
                    token("8", "augment(")
                )
            ),
            CompactMenuTab(
                "MATH",
                listOf(
                    token("1", "min("),
                    token("2", "max("),
                    token("3", "mean("),
                    token("4", "median("),
                    token("5", "sum("),
                    token("6", "prod("),
                    token("7", "stdDev("),
                    token("8", "variance(")
                )
            )
        )
    )

    fun get(id: CompactMenuId): CompactMenuDefinition = when (id) {
        CompactMenuId.MATH -> math
        CompactMenuId.TEST -> test
        CompactMenuId.ANGLE -> angle
        CompactMenuId.VARS -> vars
        CompactMenuId.VARS_WINDOW -> windowVariables
        CompactMenuId.VARS_ZOOM -> zoomVariables
        CompactMenuId.Y_VARS_FUNCTION -> functionVariables
        CompactMenuId.STAT -> stat
        CompactMenuId.LIST -> list()
    }
}

/** Transient selection and return target for one compact token menu. */
class CompactMenuState(
    val definition: CompactMenuDefinition,
    val returnView: CalculatorView
) {
    private data class Frame(
        val definition: CompactMenuDefinition,
        var selectedTabIndex: Int = 0,
        val selectedItemIndices: MutableList<Int> =
            MutableList(definition.tabs.size) { 0 }
    )

    private val frames = mutableListOf(Frame(definition))
    private val frame: Frame get() = frames.last()

    val currentDefinition: CompactMenuDefinition get() = frame.definition
    val selectedTabIndex: Int get() = frame.selectedTabIndex
    val selectedTab: CompactMenuTab get() = currentDefinition.tabs[selectedTabIndex]

    var selectedItemIndex: Int
        get() = frame.selectedItemIndices[selectedTabIndex]
        set(value) {
            frame.selectedItemIndices[selectedTabIndex] =
                value.coerceIn(selectedTab.items.indices)
        }

    val selectedItem: CompactMenuItem get() = selectedTab.items[selectedItemIndex]

    fun selectPreviousTab() {
        frame.selectedTabIndex = (selectedTabIndex - 1).coerceAtLeast(0)
    }

    fun selectNextTab() {
        frame.selectedTabIndex =
            (selectedTabIndex + 1).coerceAtMost(currentDefinition.tabs.lastIndex)
    }

    fun selectPreviousItem() {
        selectedItemIndex--
    }

    fun selectNextItem() {
        selectedItemIndex++
    }

    fun selectHotkey(hotkey: String): Boolean {
        val index = selectedTab.items.indexOfFirst { it.hotkey == hotkey }
        if (index < 0) return false
        selectedItemIndex = index
        return true
    }

    fun openSubmenu(definition: CompactMenuDefinition) {
        frames += Frame(definition)
    }

    /** Returns true when a nested level was closed; false means the root is already active. */
    fun navigateBack(): Boolean {
        if (frames.size == 1) return false
        frames.removeLast()
        return true
    }
}
