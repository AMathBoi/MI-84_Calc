package net.amathboi.mi84mod.client.calculator.ui

enum class CompactMenuId {
    TEST,
    ANGLE
}

data class CompactMenuItem(
    val hotkey: String,
    val label: String,
    val insertedToken: String? = null
) {
    val available: Boolean get() = insertedToken != null
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
    val test = CompactMenuDefinition(
        CompactMenuId.TEST,
        listOf(
            CompactMenuTab(
                "TEST",
                listOf("=", "≠", ">", "≥", "<", "≤").mapIndexed { index, token ->
                    CompactMenuItem((index + 1).toString(), token, token)
                }
            ),
            CompactMenuTab(
                "LOGIC",
                listOf("and", "or", "xor", "not(").mapIndexed { index, token ->
                    CompactMenuItem((index + 1).toString(), token, token)
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
                    CompactMenuItem("1", "°", "°"),
                    CompactMenuItem("2", "'"),
                    CompactMenuItem("3", "ʳ", "ʳ"),
                    CompactMenuItem("4", "►DMS"),
                    CompactMenuItem("5", "R►Pr(", "R►Pr("),
                    CompactMenuItem("6", "R►Pθ(", "R►Pθ("),
                    CompactMenuItem("7", "P►Rx(", "P►Rx("),
                    CompactMenuItem("8", "P►Ry(", "P►Ry(")
                )
            )
        )
    )

    fun get(id: CompactMenuId): CompactMenuDefinition = when (id) {
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
