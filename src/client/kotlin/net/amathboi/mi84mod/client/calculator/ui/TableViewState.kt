package net.amathboi.mi84mod.client.calculator.ui

import java.math.BigDecimal

/** Transient navigation, Ask values, and bottom-entry state for the graph TABLE view. */
class TableViewState {
    var selectedColumnIndex = 0
    var selectedRowIndex = 0
    var headerSelected = false
    // X stays fixed; this is the packed index of the first Y column shown beside it.
    var firstVisibleColumnIndex = 1
    var firstVisibleRowIndex = 0
    var entry = ""
    var entryCursor = 0
    var headerEntryLocked = false
    val askedXValues = mutableListOf<BigDecimal>()
    val requestedDependentCells = mutableSetOf<Pair<Int, Int>>()
}
