package net.amathboi.mi84mod.client.calculator.ui

enum class CompactMenuId {
    MATH,
    TEST,
    ANGLE
}

sealed interface CompactMenuAction {
    data class InsertToken(val token: String) : CompactMenuAction
    data class BeginFractionTemplate(val mixedNumber: Boolean) : CompactMenuAction
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

    fun get(id: CompactMenuId): CompactMenuDefinition = when (id) {
        CompactMenuId.MATH -> math
        CompactMenuId.TEST -> test
        CompactMenuId.ANGLE -> angle
    }
}

/** Transient selection and return target for one compact token menu. */
class CompactMenuState(
    val definition: CompactMenuDefinition,
    val returnView: CalculatorView
) {
    var selectedTabIndex = 0
    private val selectedItemIndices = MutableList(definition.tabs.size) { 0 }

    val selectedTab: CompactMenuTab get() = definition.tabs[selectedTabIndex]

    var selectedItemIndex: Int
        get() = selectedItemIndices[selectedTabIndex]
        set(value) {
            selectedItemIndices[selectedTabIndex] =
                value.coerceIn(selectedTab.items.indices)
        }

    val selectedItem: CompactMenuItem get() = selectedTab.items[selectedItemIndex]

    fun selectPreviousTab() {
        selectedTabIndex = (selectedTabIndex - 1).coerceAtLeast(0)
    }

    fun selectNextTab() {
        selectedTabIndex =
            (selectedTabIndex + 1).coerceAtMost(definition.tabs.lastIndex)
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
}
