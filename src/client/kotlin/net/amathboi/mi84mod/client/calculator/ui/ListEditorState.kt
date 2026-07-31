package net.amathboi.mi84mod.client.calculator.ui

/** Transient cursor and bottom-entry state for the reviewed STAT→Edit list table. */
class ListEditorState {
    var selectedListIndex = 0
    var selectedRowIndex = 0
    var firstVisibleListIndex = 0
    var firstVisibleRowIndex = 0
    var entry = ""
    var entryCursor = 0
    var headerEntryLocked = false
    var creatingNamedList = false
    var pendingListName = ""

    fun selectedRowNumber(): Int = selectedRowIndex + 1
}
