package net.amathboi.mi84mod.client.calculator.controller

import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.amathboi.mi84mod.client.calculator.CalculatorDisplayMemory
import net.amathboi.mi84mod.client.calculator.ExpressionEditingTokens
import net.amathboi.mi84mod.client.calculator.GraphNavigationMath
import net.amathboi.mi84mod.client.calculator.MathDisplayToken
import net.amathboi.mi84mod.client.calculator.MathDisplayTokens
import net.amathboi.mi84mod.client.calculator.ModeSettingsMemory
import net.amathboi.mi84mod.client.calculator.RootFieldOrder
import net.amathboi.mi84mod.client.calculator.WindowSettingsMemory
import net.amathboi.mi84mod.client.calculator.YEqualsMemory
import net.amathboi.mi84mod.client.calculator.ZoomMemory
import net.amathboi.mi84mod.client.calculator.input.CalculatorCommand
import net.amathboi.mi84mod.client.calculator.input.CalculatorInputEvent
import net.amathboi.mi84mod.client.calculator.input.CalculatorKey
import net.amathboi.mi84mod.client.calculator.input.CalculatorKeyBindings
import net.amathboi.mi84mod.client.calculator.input.ModifierLayer
import net.amathboi.mi84mod.client.calculator.ui.CalculatorUiState
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuDefinitions
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuAction
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuId
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuState
import net.amathboi.mi84mod.client.calculator.ui.FractionTemplateState
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuAction
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuState
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuTab
import net.amathboi.mi84mod.client.calculator.ui.GraphWindow
import net.amathboi.mi84mod.client.calculator.ui.ListEditorState
import net.amathboi.mi84mod.client.calculator.ui.TraceState
import net.amathboi.mi84mod.client.calculator.ui.TableViewState
import net.amathboi.mi84mod.client.calculator.ui.StatPlotViewState
import net.amathboi.mi84mod.client.calculator.ui.ZoomGraphOperation
import net.amathboi.mi84mod.client.calculator.ui.ZoomGraphState
import net.amathboi.mi84mod.client.calculator.ui.ZoomMenuOption
import net.amathboi.mi84mod.client.calculator.ui.ZoomTab

/**
 * Owns calculator input routing and transient UI state without depending on Minecraft classes.
 * Rendering reads [state], while calculator mouse clicks enter through [dispatch].
 */
class CalculatorController(
    val state: CalculatorUiState = CalculatorUiState()
) {
    val zoomOptions = listOf(
        ZoomMenuOption("1", "ZBox"),
        ZoomMenuOption("2", "Zoom In"),
        ZoomMenuOption("3", "Zoom Out"),
        ZoomMenuOption("4", "ZDecimal"),
        ZoomMenuOption("5", "ZSquare"),
        ZoomMenuOption("6", "ZStandard"),
        ZoomMenuOption("7", "ZTrig"),
        ZoomMenuOption("8", "ZInteger"),
        ZoomMenuOption("9", "ZoomStat"),
        ZoomMenuOption("0", "ZoomFit"),
        ZoomMenuOption("A", "ZQuadrant1"),
        ZoomMenuOption("B", "ZFrac1/2"),
        ZoomMenuOption("C", "ZFrac1/3"),
        ZoomMenuOption("D", "ZFrac1/4"),
        ZoomMenuOption("E", "ZFrac1/5"),
        ZoomMenuOption("F", "ZFrac1/8"),
        ZoomMenuOption("G", "ZFrac1/10")
    )
    val memoryOptions = listOf(
        ZoomMenuOption("1", "ZPrevious"),
        ZoomMenuOption("2", "ZoomSto"),
        ZoomMenuOption("3", "ZoomRcl"),
        ZoomMenuOption("4", "SetFactors...")
    )

    fun dispatch(
        event: CalculatorInputEvent,
        graphAspect: Double = DEFAULT_GRAPH_ASPECT
    ): DispatchResult {
        if (event.key == CalculatorKey.SECOND) {
            toggleSecondModifier()
            return DispatchResult.Handled
        }
        if (event.key == CalculatorKey.ALPHA) {
            handleAlphaModifier()
            return DispatchResult.Handled
        }

        if (state.view == CalculatorView.COMPACT_MENU &&
            handleCompactMenuPhysicalKey(event.key)
        ) {
            return DispatchResult.Handled
        }
        if (state.functionMenu != null && handleFunctionMenuPhysicalKey(event.key)) {
            return DispatchResult.Handled
        }

        val activeLayer = effectiveModifierLayer()
        if (state.view == CalculatorView.ZOOM && activeLayer == ModifierLayer.ALPHA) {
            val alphaHotkey = alphaHotkeyFor(event.key)
            if (alphaHotkey != null) {
                consumeTransientModifier()
                activateZoomHotkey(alphaHotkey, graphAspect)
                return DispatchResult.Handled
            }
        }

        val command = CalculatorKeyBindings.resolve(event.key, activeLayer)
        if (activeLayer != ModifierLayer.NORMAL) consumeTransientModifier()
        when (command) {
            is CalculatorCommand.Placeholder ->
                return DispatchResult.Placeholder(command.key, command.layer)
            is CalculatorCommand.Unsupported -> return DispatchResult.Unsupported(command.key)
            else -> Unit
        }

        when (command) {
            is CalculatorCommand.OpenView -> {
                switchView(command.view)
                return DispatchResult.Handled
            }
            is CalculatorCommand.OpenCompactMenu -> {
                openCompactMenu(command.menu)
                return DispatchResult.Handled
            }
            is CalculatorCommand.OpenFunctionMenu -> {
                openFunctionMenu(command.tab)
                return DispatchResult.Handled
            }
            CalculatorCommand.BeginFractionTemplate -> {
                openDirectFractionTemplate()
                return DispatchResult.Handled
            }
            CalculatorCommand.QuitToHome -> {
                if (state.functionMenu != null) {
                    closeFunctionMenu()
                } else {
                    switchView(CalculatorView.HOME)
                }
                return DispatchResult.Handled
            }
            CalculatorCommand.BeginTrace -> {
                beginTrace()
                return DispatchResult.Handled
            }
            CalculatorCommand.ToggleInsertMode -> {
                val editor = state.listEditor
                if (state.view == CalculatorView.LIST_EDITOR &&
                    editor != null &&
                    ListEditorController.editingHeader(editor)
                ) {
                    ListEditorController.beginNamedListCreation(editor)
                } else {
                    state.insertMode = !state.insertMode
                }
                return DispatchResult.Handled
            }
            CalculatorCommand.OpenListLiteral -> {
                if (state.view != CalculatorView.LIST_EDITOR) {
                    appendToEditor(state.view, "{")
                    return DispatchResult.Handled
                }
            }
            CalculatorCommand.CloseListLiteral -> {
                if (state.view != CalculatorView.LIST_EDITOR) {
                    appendToEditor(state.view, "}")
                    return DispatchResult.Handled
                }
            }
            is CalculatorCommand.InsertListName -> {
                appendToEditor(state.view, command.name.token)
                return DispatchResult.Handled
            }
            else -> Unit
        }

        state.functionMenu?.let {
            handleFunctionMenu(command)
            return DispatchResult.Handled
        }
        state.fractionTemplate?.let {
            handleFractionTemplate(command, it)
            return DispatchResult.Handled
        }
        if (command == CalculatorCommand.Left && reopenStructuredFractionBeforeCursor()) {
            return DispatchResult.Handled
        }
        if (command == CalculatorCommand.Right && reopenStructuredFractionAtCursor()) {
            return DispatchResult.Handled
        }
        if (command == CalculatorCommand.Right && advanceIncompleteMathNotation()) {
            return DispatchResult.Handled
        }

        when (state.view) {
            CalculatorView.HOME -> HomeViewController.handle(command, state)
            CalculatorView.Y_EQUALS -> YEqualsViewController.handle(command, state)
            CalculatorView.WINDOW -> WindowViewController.handle(command, state)
            CalculatorView.TABLE_SETUP -> TableSetupViewController.handle(command, state)
            CalculatorView.STAT_PLOT -> StatPlotViewController.handle(
                command,
                state.statPlot ?: StatPlotViewState().also { state.statPlot = it }
            )
            CalculatorView.FORMAT -> FormatViewController.handle(command)
            CalculatorView.MODE -> ModeViewController.handle(command)
            CalculatorView.ZOOM -> handleZoom(command, graphAspect)
            CalculatorView.ZOOM_FACTORS -> handleZoomFactors(command)
            CalculatorView.COMPACT_MENU -> handleCompactMenu(command)
            CalculatorView.LIST_EDITOR -> ListEditorController.handle(
                command,
                state.listEditor ?: ListEditorState().also { state.listEditor = it },
                state.insertMode
            )
            CalculatorView.TABLE -> TableViewController.handle(
                command,
                state.table ?: TableViewState().also { state.table = it },
                state.insertMode
            )
            CalculatorView.GRAPH -> handleGraph(command, graphAspect)
        }
        return DispatchResult.Handled
    }

    fun currentZoomOptions(): List<ZoomMenuOption> =
        if (state.zoomTab == ZoomTab.ZOOM) zoomOptions else memoryOptions

    fun currentZoomSelectedIndex(): Int =
        if (state.zoomTab == ZoomTab.ZOOM) state.zoomSelectedIndex else state.memorySelectedIndex

    fun readGraphWindow(): GraphWindow? {
        val values = (0..6).map { index ->
            CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(index), 0.0)
                ?: return null
        }
        val xMin = values[0]
        val xMax = values[1]
        val xScale = values[2]
        val yMin = values[3]
        val yMax = values[4]
        val yScale = values[5]
        val xResolutionDouble = values[6]
        if (!WindowSettingsMemory.supportsGraphBounds(xMin, xMax, yMin, yMax) ||
            xScale <= 0.0 || yScale <= 0.0 || xResolutionDouble <= 0.0
        ) {
            return null
        }
        return GraphWindow(
            xMin,
            xMax,
            xScale,
            yMin,
            yMax,
            yScale,
            xResolutionDouble.roundToInt().coerceAtLeast(1)
        )
    }

    private fun handleZoom(command: CalculatorCommand, graphAspect: Double) {
        when (command) {
            CalculatorCommand.Left -> state.zoomTab = ZoomTab.ZOOM
            CalculatorCommand.Right -> state.zoomTab = ZoomTab.MEMORY
            CalculatorCommand.Up -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() - 1)
            CalculatorCommand.Down -> setCurrentZoomSelectedIndex(currentZoomSelectedIndex() + 1)
            CalculatorCommand.Enter -> activateCurrentZoomOption(graphAspect)
            is CalculatorCommand.Digit -> activateZoomHotkey(command.value.toString(), graphAspect)
            else -> Unit
        }
    }

    private fun handleZoomFactors(command: CalculatorCommand) {
        when (command) {
            CalculatorCommand.Up ->
                state.factorSelectedIndex = (state.factorSelectedIndex - 1).coerceAtLeast(0)
            CalculatorCommand.Down ->
                state.factorSelectedIndex =
                    (state.factorSelectedIndex + 1).coerceAtMost(ZoomMemory.denominators().lastIndex)
            CalculatorCommand.Enter -> selectCurrentFactor()
            is CalculatorCommand.Digit -> {
                val index = command.value.digitToInt() - 1
                if (index in ZoomMemory.denominators().indices) {
                    state.factorSelectedIndex = index
                    selectCurrentFactor()
                }
            }
            else -> Unit
        }
    }

    private fun handleCompactMenu(command: CalculatorCommand) {
        val menu = state.compactMenu ?: run {
            switchView(CalculatorView.HOME)
            return
        }
        when (command) {
            CalculatorCommand.Left -> menu.selectPreviousTab()
            CalculatorCommand.Right -> menu.selectNextTab()
            CalculatorCommand.Up -> menu.selectPreviousItem()
            CalculatorCommand.Down -> menu.selectNextItem()
            CalculatorCommand.Enter -> activateCompactMenuItem(menu)
            CalculatorCommand.Clear -> {
                if (!menu.navigateBack()) closeCompactMenu(menu)
            }
            is CalculatorCommand.Digit -> {
                if (menu.selectHotkey(command.value.toString())) {
                    activateCompactMenuItem(menu)
                }
            }
            is CalculatorCommand.InsertVariable -> {
                if (menu.selectHotkey(command.variable.symbol.toString())) {
                    activateCompactMenuItem(menu)
                }
            }
            else -> Unit
        }
    }

    private fun handleFunctionMenu(command: CalculatorCommand) {
        val menu = state.functionMenu ?: return
        when (command) {
            CalculatorCommand.Left -> menu.navigateLeft()
            CalculatorCommand.Right -> menu.navigateRight()
            CalculatorCommand.Up -> menu.selectPreviousItem()
            CalculatorCommand.Down -> menu.selectNextItem()
            CalculatorCommand.Enter -> activateFunctionMenuItem(menu)
            CalculatorCommand.Clear -> closeFunctionMenu()
            is CalculatorCommand.Digit -> {
                if (menu.selectHotkey(command.value.toString())) {
                    activateFunctionMenuItem(menu)
                }
            }
            else -> Unit
        }
    }

    /**
     * Compact-menu navigation and displayed hotkeys remain physical controls while A-LOCK is
     * active. An explicit one-shot modifier still takes precedence and cannot fall through.
     */
    private fun handleCompactMenuPhysicalKey(key: CalculatorKey): Boolean {
        if (state.modifier != ModifierLayer.NORMAL) return false
        val menu = state.compactMenu ?: return false
        when (key) {
            CalculatorKey.LEFT -> menu.selectPreviousTab()
            CalculatorKey.RIGHT -> menu.selectNextTab()
            CalculatorKey.UP -> menu.selectPreviousItem()
            CalculatorKey.DOWN -> menu.selectNextItem()
            CalculatorKey.ENTER -> activateCompactMenuItem(menu)
            CalculatorKey.CLEAR -> {
                if (!menu.navigateBack()) closeCompactMenu(menu)
            }
            CalculatorKey.Y_EQUALS -> switchView(CalculatorView.Y_EQUALS)
            CalculatorKey.WINDOW -> switchView(CalculatorView.WINDOW)
            CalculatorKey.ZOOM -> switchView(CalculatorView.ZOOM)
            CalculatorKey.TRACE -> beginTrace()
            CalculatorKey.GRAPH -> switchView(CalculatorView.GRAPH)
            CalculatorKey.MODE -> switchView(CalculatorView.MODE)
            else -> {
                val hotkey = digitHotkeyFor(key) ?: return false
                if (menu.selectHotkey(hotkey)) activateCompactMenuItem(menu)
            }
        }
        return true
    }

    /**
     * Function-menu navigation remains physical while A-LOCK is active, matching compact menus.
     * Other keys continue through typed command routing so direct view changes can close the overlay.
     */
    private fun handleFunctionMenuPhysicalKey(key: CalculatorKey): Boolean {
        if (state.modifier != ModifierLayer.NORMAL) return false
        val menu = state.functionMenu ?: return false
        when (key) {
            CalculatorKey.LEFT -> menu.navigateLeft()
            CalculatorKey.RIGHT -> menu.navigateRight()
            CalculatorKey.UP -> menu.selectPreviousItem()
            CalculatorKey.DOWN -> menu.selectNextItem()
            CalculatorKey.ENTER -> activateFunctionMenuItem(menu)
            CalculatorKey.CLEAR -> closeFunctionMenu()
            else -> {
                val hotkey = digitHotkeyFor(key) ?: return false
                if (menu.selectHotkey(hotkey)) activateFunctionMenuItem(menu)
            }
        }
        return true
    }

    private fun handleGraph(command: CalculatorCommand, graphAspect: Double) {
        state.zoomGraph?.let { zoom ->
            when (command) {
                CalculatorCommand.Left -> moveZoomGraphCursor(zoom, -1, 0)
                CalculatorCommand.Right -> moveZoomGraphCursor(zoom, 1, 0)
                CalculatorCommand.Up -> moveZoomGraphCursor(zoom, 0, 1)
                CalculatorCommand.Down -> moveZoomGraphCursor(zoom, 0, -1)
                CalculatorCommand.Enter -> acceptZoomGraphSelection(zoom, graphAspect)
                else -> Unit
            }
            return
        }

        val trace = state.trace ?: return
        when (command) {
            CalculatorCommand.Left -> moveTrace(trace, -1)
            CalculatorCommand.Right -> moveTrace(trace, 1)
            CalculatorCommand.Up -> moveTraceFunction(trace, -1)
            CalculatorCommand.Down -> moveTraceFunction(trace, 1)
            else -> Unit
        }
    }

    private fun switchView(view: CalculatorView) {
        state.view = view
        state.compactMenu = null
        state.functionMenu = null
        state.fractionTemplate = null
        state.trace = null
        state.zoomGraph = null
        state.modifier = ModifierLayer.NORMAL
        state.historyNavigationPosition = 0
        state.entryRecallPosition = 0
        if (view == CalculatorView.LIST_EDITOR && state.listEditor == null) {
            state.listEditor = ListEditorState()
        }
        if (view == CalculatorView.TABLE && state.table == null) {
            state.table = TableViewState()
        }
        if (view == CalculatorView.STAT_PLOT && state.statPlot == null) {
            state.statPlot = StatPlotViewState()
        }
        if (view == CalculatorView.TABLE) {
            state.table?.let(TableViewController::open)
        }
    }

    private fun openCompactMenu(id: CompactMenuId) {
        val returnView = state.view.takeIf {
            it == CalculatorView.HOME ||
                it == CalculatorView.Y_EQUALS ||
                it == CalculatorView.WINDOW
        } ?: CalculatorView.HOME
        state.compactMenu = CompactMenuState(CompactMenuDefinitions.get(id), returnView)
        state.functionMenu = null
        state.fractionTemplate = null
        state.view = CalculatorView.COMPACT_MENU
        state.trace = null
        state.zoomGraph = null
        state.historyNavigationPosition = 0
        state.entryRecallPosition = 0
    }

    private fun activateCompactMenuItem(menu: CompactMenuState) {
        when (val action = menu.selectedItem.action) {
            is CompactMenuAction.InsertToken -> {
                val returnView = menu.returnView
                closeCompactMenu(menu)
                appendToEditor(returnView, action.token)
            }
            is CompactMenuAction.BeginFractionTemplate -> {
                val returnView = menu.returnView
                closeCompactMenu(menu)
                state.fractionTemplate =
                    FractionTemplateState(action.mixedNumber, returnView)
            }
            is CompactMenuAction.OpenSubmenu -> menu.openSubmenu(action.definition)
            CompactMenuAction.OpenListEditor -> switchView(CalculatorView.LIST_EDITOR)
            CompactMenuAction.Unavailable -> Unit
        }
    }

    private fun closeCompactMenu(menu: CompactMenuState) {
        state.view = menu.returnView
        state.compactMenu = null
        state.modifier = ModifierLayer.NORMAL
    }

    private fun openFunctionMenu(tab: FunctionMenuTab) {
        state.functionMenu?.let { existing ->
            existing.selectTab(tab)
            state.modifier = ModifierLayer.NORMAL
            return
        }

        val targetView = state.view.takeIf(::isEditableView) ?: CalculatorView.HOME
        if (targetView != state.view) switchView(targetView)
        state.compactMenu = null
        state.fractionTemplate = null
        state.functionMenu = FunctionMenuState(tab, targetView)
        state.trace = null
        state.zoomGraph = null
        state.historyNavigationPosition = 0
        state.entryRecallPosition = 0
        state.modifier = ModifierLayer.NORMAL
    }

    private fun openDirectFractionTemplate() {
        val targetView = state.view.takeIf(::isEditableView) ?: CalculatorView.HOME
        if (targetView != state.view) switchView(targetView)
        state.compactMenu = null
        state.functionMenu = null
        state.fractionTemplate =
            FractionTemplateState(mixedNumber = false, targetView = targetView)
        state.trace = null
        state.zoomGraph = null
        state.historyNavigationPosition = 0
        state.entryRecallPosition = 0
        state.modifier = ModifierLayer.NORMAL
    }

    private fun activateFunctionMenuItem(menu: FunctionMenuState) {
        when (val action = menu.selectedItem.action) {
            is FunctionMenuAction.InsertToken -> {
                closeFunctionMenu()
                appendToEditor(menu.targetView, action.token)
            }
            is FunctionMenuAction.BeginFractionTemplate -> {
                closeFunctionMenu()
                state.fractionTemplate =
                    FractionTemplateState(action.mixedNumber, menu.targetView)
            }
            FunctionMenuAction.Unavailable -> Unit
        }
    }

    private fun closeFunctionMenu() {
        state.functionMenu = null
        state.modifier = ModifierLayer.NORMAL
    }

    private fun handleFractionTemplate(
        command: CalculatorCommand,
        template: FractionTemplateState
    ) {
        when (command) {
            CalculatorCommand.Left -> if (template.moveLeft()) exitFractionTemplateLeft(template)
            CalculatorCommand.Right -> if (template.moveRight()) commitFractionTemplate(template)
            CalculatorCommand.Up -> template.moveUp()
            CalculatorCommand.Down -> if (template.moveDown()) commitFractionTemplate(template)
            CalculatorCommand.Delete -> template.deleteAtCursor()
            CalculatorCommand.Clear -> {
                if (!template.clearSelectedField()) state.fractionTemplate = null
            }
            CalculatorCommand.Enter -> commitFractionTemplate(template)
            CalculatorCommand.Negative -> template.toggleSign()
            else -> fractionTemplateText(command)?.let { template.append(it, state.insertMode) }
        }
    }

    private fun exitFractionTemplateLeft(template: FractionTemplateState) {
        val committed = commitFractionTemplate(template)
        if (!committed) {
            // The backing editor still contains the original completed fraction, if any.
            state.fractionTemplate = null
        }
        if (committed || template.originalToken != null) {
            moveEditorCursorLeft(template.targetView)
        }
    }

    private fun commitFractionTemplate(template: FractionTemplateState): Boolean {
        val expression = template.completedExpression() ?: return false
        val original = template.originalToken
        val start = template.replacementStart
        if (original == null || start == null) {
            appendToEditor(template.targetView, expression)
            state.fractionTemplate = null
            return true
        } else if (replaceStructuredFraction(template.targetView, start, original, expression)) {
            state.fractionTemplate = null
            return true
        }
        return false
    }

    private fun moveEditorCursorLeft(view: CalculatorView) {
        when (view) {
            CalculatorView.HOME -> CalculatorDisplayMemory.moveCursorLeft()
            CalculatorView.Y_EQUALS -> YEqualsMemory.moveCursorLeft()
            CalculatorView.WINDOW -> WindowSettingsMemory.moveCursorLeft()
            else -> Unit
        }
    }

    private fun reopenStructuredFractionBeforeCursor(): Boolean {
        if (state.historyNavigationPosition != 0) return false
        val (expression, cursor) = when (state.view) {
            CalculatorView.HOME ->
                CalculatorDisplayMemory.current() to CalculatorDisplayMemory.cursorPosition()
            CalculatorView.Y_EQUALS -> {
                val index = YEqualsMemory.selectedIndex
                YEqualsMemory.equation(index) to YEqualsMemory.cursor(index)
            }
            CalculatorView.WINDOW -> {
                val index = WindowSettingsMemory.selectedIndex
                WindowSettingsMemory.value(index) to WindowSettingsMemory.cursor(index)
            }
            else -> return false
        }
        val token =
            ExpressionEditingTokens.structuredFractionEndingAt(expression, cursor) ?: return false
        val start = cursor - token.length
        state.fractionTemplate =
            FractionTemplateState.reopen(token, start, state.view) ?: return false
        return true
    }

    private fun reopenStructuredFractionAtCursor(): Boolean {
        if (state.historyNavigationPosition != 0) return false
        val (expression, cursor) = when (state.view) {
            CalculatorView.HOME ->
                CalculatorDisplayMemory.current() to CalculatorDisplayMemory.cursorPosition()
            CalculatorView.Y_EQUALS -> {
                val index = YEqualsMemory.selectedIndex
                YEqualsMemory.equation(index) to YEqualsMemory.cursor(index)
            }
            CalculatorView.WINDOW -> {
                val index = WindowSettingsMemory.selectedIndex
                WindowSettingsMemory.value(index) to WindowSettingsMemory.cursor(index)
            }
            else -> return false
        }
        val token =
            ExpressionEditingTokens.structuredFractionStartingAt(expression, cursor) ?: return false
        state.fractionTemplate =
            FractionTemplateState.reopen(token, cursor, state.view, fromStart = true) ?: return false
        return true
    }

    private fun advanceIncompleteMathNotation(): Boolean {
        val (expression, cursor) = when (state.view) {
            CalculatorView.HOME ->
                CalculatorDisplayMemory.current() to CalculatorDisplayMemory.cursorPosition()
            CalculatorView.Y_EQUALS -> {
                val index = YEqualsMemory.selectedIndex
                YEqualsMemory.equation(index) to YEqualsMemory.cursor(index)
            }
            CalculatorView.WINDOW -> {
                val index = WindowSettingsMemory.selectedIndex
                WindowSettingsMemory.value(index) to WindowSettingsMemory.cursor(index)
            }
            else -> return false
        }
        if (cursor != expression.length) return false
        return when (val token = MathDisplayTokens.incompleteEndingAt(expression, cursor)) {
            is MathDisplayToken.Combinatoric -> {
                if (!token.rightOperandEntered && token.leftOperand.isNotBlank()) {
                    appendToEditor(state.view, ",")
                    true
                } else if (token.rightOperandEntered && token.rightOperand.isNotBlank()) {
                    appendToEditor(state.view, ")")
                    true
                } else {
                    false
                }
            }
            is MathDisplayToken.Root -> {
                if (token.fieldOrder != RootFieldOrder.INDEX_THEN_RADICAND) return false
                if (!token.secondFieldEntered && token.index?.isNotBlank() == true) {
                    appendToEditor(state.view, ",")
                    true
                } else if (token.secondFieldEntered && token.radicand.isNotBlank()) {
                    appendToEditor(state.view, ")")
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun replaceStructuredFraction(
        view: CalculatorView,
        start: Int,
        original: String,
        replacement: String
    ): Boolean = when (view) {
        CalculatorView.HOME ->
            CalculatorDisplayMemory.replaceStructuredFraction(start, original, replacement)
        CalculatorView.Y_EQUALS ->
            YEqualsMemory.replaceStructuredFraction(start, original, replacement)
        CalculatorView.WINDOW ->
            WindowSettingsMemory.replaceStructuredFraction(start, original, replacement)
        else -> false
    }

    private fun appendToEditor(view: CalculatorView, text: String) {
        when (view) {
            CalculatorView.HOME -> CalculatorDisplayMemory.appendMenuToken(text, state.insertMode)
            CalculatorView.Y_EQUALS -> YEqualsMemory.append(text, state.insertMode)
            CalculatorView.WINDOW -> WindowSettingsMemory.append(text, state.insertMode)
            else -> Unit
        }
    }

    private fun fractionTemplateText(command: CalculatorCommand): String? = when (command) {
        is CalculatorCommand.Digit -> command.value.toString()
        is CalculatorCommand.Operator -> command.value.toString()
        is CalculatorCommand.Function -> "${command.name}("
        is CalculatorCommand.InsertVariable -> command.variable.symbol.toString()
        CalculatorCommand.Decimal -> "."
        CalculatorCommand.Square -> "^2"
        CalculatorCommand.Reciprocal -> "^-1"
        CalculatorCommand.OpenParenthesis -> "("
        CalculatorCommand.CloseParenthesis -> ")"
        CalculatorCommand.Comma -> ","
        CalculatorCommand.Variable -> "X"
        CalculatorCommand.InsertAns -> "Ans"
        CalculatorCommand.InsertImaginaryUnit -> "i"
        CalculatorCommand.InsertPi -> "π"
        CalculatorCommand.InsertEuler -> "e"
        CalculatorCommand.InsertInverseSine -> "sin⁻¹("
        CalculatorCommand.InsertInverseCosine -> "cos⁻¹("
        CalculatorCommand.InsertInverseTangent -> "tan⁻¹("
        CalculatorCommand.InsertTenPower -> "10^("
        CalculatorCommand.InsertEulerPower -> "e^("
        CalculatorCommand.InsertSquareRoot -> "sqrt("
        CalculatorCommand.InsertScientificExponent -> "EE"
        CalculatorCommand.OpenListLiteral -> "{"
        CalculatorCommand.CloseListLiteral -> "}"
        is CalculatorCommand.InsertListName -> command.name.token
        else -> null
    }

    private fun isEditableView(view: CalculatorView): Boolean =
        view == CalculatorView.HOME ||
            view == CalculatorView.Y_EQUALS ||
            view == CalculatorView.WINDOW

    private fun toggleSecondModifier() {
        state.modifier = if (state.modifier == ModifierLayer.SECOND) {
            ModifierLayer.NORMAL
        } else {
            ModifierLayer.SECOND
        }
    }

    private fun handleAlphaModifier() {
        when {
            state.alphaLocked -> {
                state.alphaLocked = false
                state.modifier = ModifierLayer.NORMAL
            }
            state.modifier == ModifierLayer.SECOND -> {
                state.alphaLocked = true
                state.modifier = ModifierLayer.NORMAL
            }
            state.modifier == ModifierLayer.ALPHA ->
                state.modifier = ModifierLayer.NORMAL
            else -> state.modifier = ModifierLayer.ALPHA
        }
    }

    private fun effectiveModifierLayer(): ModifierLayer =
        if (state.modifier != ModifierLayer.NORMAL) {
            state.modifier
        } else if (state.alphaLocked) {
            ModifierLayer.ALPHA
        } else {
            ModifierLayer.NORMAL
        }

    private fun consumeTransientModifier() {
        state.modifier = ModifierLayer.NORMAL
    }

    private fun beginTrace() {
        val graphWindow = readGraphWindow()
        state.trace = graphWindow?.let { TraceState(0, (it.xMin + it.xMax) / 2.0) }
        state.view = CalculatorView.GRAPH
        state.compactMenu = null
        state.functionMenu = null
        state.fractionTemplate = null
        state.zoomGraph = null
        state.modifier = ModifierLayer.NORMAL
        state.historyNavigationPosition = 0
        state.entryRecallPosition = 0
    }

    private fun setCurrentZoomSelectedIndex(index: Int) {
        if (state.zoomTab == ZoomTab.ZOOM) {
            state.zoomSelectedIndex = index.coerceIn(zoomOptions.indices)
        } else {
            state.memorySelectedIndex = index.coerceIn(memoryOptions.indices)
        }
    }

    private fun activateZoomHotkey(hotkey: String, graphAspect: Double) {
        val optionIndex = currentZoomOptions().indexOfFirst { it.hotkey == hotkey }
        if (optionIndex < 0) return
        setCurrentZoomSelectedIndex(optionIndex)
        activateCurrentZoomOption(graphAspect)
    }

    private fun activateCurrentZoomOption(graphAspect: Double) {
        if (state.zoomTab == ZoomTab.ZOOM) {
            activateZoomOption(state.zoomSelectedIndex, graphAspect)
        } else {
            activateMemoryOption(state.memorySelectedIndex)
        }
    }

    private fun activateZoomOption(index: Int, graphAspect: Double) {
        when (index) {
            0 -> beginZoomGraphOperation(ZoomGraphOperation.BOX)
            1 -> beginZoomGraphOperation(ZoomGraphOperation.IN)
            2 -> beginZoomGraphOperation(ZoomGraphOperation.OUT)
            3 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-4.7", "4.7", "1", "-3.1", "3.1", "1")
            }
            4 -> applySquareWindow(graphAspect)
            5 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("-10", "10", "1", "-10", "10", "1")
            }
            6 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow(
                    "-6.28318530718",
                    "6.28318530718",
                    "1.57079632679",
                    "-4",
                    "4",
                    "1"
                )
            }
            7 -> applyIntegerWindow()
            8, 9 -> applyZoomFit()
            10 -> applyImmediateZoom {
                WindowSettingsMemory.setGraphWindow("0", "10", "1", "0", "10", "1")
            }
            in 11..16 -> {
                val denominator = listOf(2, 3, 4, 5, 8, 10)[index - 11]
                applyFractionalWindow(denominator)
            }
        }
    }

    private fun activateMemoryOption(index: Int) {
        when (index) {
            0 -> if (ZoomMemory.restorePrevious()) openGraph()
            1 -> {
                ZoomMemory.storeCurrent()
                state.view = CalculatorView.GRAPH
            }
            2 -> if (ZoomMemory.recallStored()) openGraph()
            3 -> {
                state.factorSelectedIndex = ZoomMemory.selectedDenominatorIndex()
                state.view = CalculatorView.ZOOM_FACTORS
            }
        }
    }

    private fun selectCurrentFactor() {
        ZoomMemory.selectDenominator(state.factorSelectedIndex)
        state.view = CalculatorView.ZOOM
        state.zoomTab = ZoomTab.MEMORY
    }

    private fun applyImmediateZoom(change: () -> Boolean) {
        val previousWindow = WindowSettingsMemory.snapshot()
        if (!change()) return
        ZoomMemory.rememberPrevious(previousWindow)
        state.trace = null
        state.zoomGraph = null
        state.view = CalculatorView.GRAPH
    }

    private fun applySquareWindow(graphAspect: Double) {
        val graphWindow = readGraphWindow() ?: return
        val xCenter = (graphWindow.xMin + graphWindow.xMax) / 2.0
        val yCenter = (graphWindow.yMin + graphWindow.yMax) / 2.0
        val xSpan = graphWindow.xMax - graphWindow.xMin
        val ySpan = graphWindow.yMax - graphWindow.yMin
        val squareXSpan = max(xSpan, ySpan * graphAspect)
        val squareYSpan = max(ySpan, xSpan / graphAspect)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(xCenter - squareXSpan / 2.0),
                formatWindowValue(xCenter + squareXSpan / 2.0),
                WindowSettingsMemory.value(2),
                formatWindowValue(yCenter - squareYSpan / 2.0),
                formatWindowValue(yCenter + squareYSpan / 2.0),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyIntegerWindow() {
        val graphWindow = readGraphWindow() ?: return
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                GraphNavigationMath.integerBound(graphWindow.xMin, RoundingMode.FLOOR),
                GraphNavigationMath.integerBound(graphWindow.xMax, RoundingMode.CEILING),
                "1",
                GraphNavigationMath.integerBound(graphWindow.yMin, RoundingMode.FLOOR),
                GraphNavigationMath.integerBound(graphWindow.yMax, RoundingMode.CEILING),
                "1",
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyFractionalWindow(denominator: Int) {
        val graphWindow = readGraphWindow() ?: return
        val spacing = formatWindowValue(1.0 / denominator)
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(graphWindow.xMin),
                formatWindowValue(graphWindow.xMax),
                spacing,
                formatWindowValue(graphWindow.yMin),
                formatWindowValue(graphWindow.yMax),
                spacing,
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun applyZoomFit() {
        val graphWindow = readGraphWindow() ?: return
        val expressions = YEqualsMemory.subscripts.indices
            .map(YEqualsMemory::equation)
            .filter(String::isNotEmpty)
        if (expressions.isEmpty()) return

        val values = mutableListOf<Double>()
        repeat(101) { sampleIndex ->
            val graphX =
                graphWindow.xMin + sampleIndex / 100.0 * (graphWindow.xMax - graphWindow.xMin)
            expressions.forEach { expression ->
                CalculatorDisplayMemory.evaluateForGraph(expression, graphX)
                    ?.takeIf(Double::isFinite)
                    ?.let(values::add)
            }
        }
        if (values.isEmpty()) return
        var yMin = values.minOrNull() ?: return
        var yMax = values.maxOrNull() ?: return
        val padding = if (yMin == yMax) max(1.0, abs(yMin) * 0.1) else (yMax - yMin) * 0.05
        yMin -= padding
        yMax += padding
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                WindowSettingsMemory.value(0),
                WindowSettingsMemory.value(1),
                WindowSettingsMemory.value(2),
                formatWindowValue(yMin),
                formatWindowValue(yMax),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun beginZoomGraphOperation(operation: ZoomGraphOperation) {
        val graphWindow = readGraphWindow() ?: return
        state.zoomGraph = ZoomGraphState(
            operation,
            (graphWindow.xMin + graphWindow.xMax) / 2.0,
            (graphWindow.yMin + graphWindow.yMax) / 2.0
        )
        state.trace = null
        state.view = CalculatorView.GRAPH
    }

    private fun moveTraceFunction(trace: TraceState, direction: Int) {
        var candidate = trace.functionIndex + direction
        while (candidate in YEqualsMemory.subscripts.indices) {
            if (YEqualsMemory.equation(candidate).isNotEmpty()) {
                trace.functionIndex = candidate
                return
            }
            candidate += direction
        }
    }

    private fun moveTrace(trace: TraceState, direction: Int) {
        val graphWindow = readGraphWindow() ?: return
        val step = CalculatorDisplayMemory.evaluateForGraph(WindowSettingsMemory.value(8), 0.0)
        if (step != null && step.isFinite() && step > 0.0) {
            trace.x = GraphNavigationMath.clampedTraceX(
                trace.x,
                direction,
                step,
                graphWindow.xMin,
                graphWindow.xMax
            )
        }
    }

    private fun moveZoomGraphCursor(zoom: ZoomGraphState, xDirection: Int, yDirection: Int) {
        val graphWindow = readGraphWindow() ?: return
        val xStep = (graphWindow.xMax - graphWindow.xMin) / 40.0
        val yStep = (graphWindow.yMax - graphWindow.yMin) / 26.0
        zoom.x = (zoom.x + xDirection * xStep).coerceIn(graphWindow.xMin, graphWindow.xMax)
        zoom.y = (zoom.y + yDirection * yStep).coerceIn(graphWindow.yMin, graphWindow.yMax)
    }

    private fun acceptZoomGraphSelection(zoom: ZoomGraphState, graphAspect: Double) {
        val graphWindow = readGraphWindow() ?: return
        if (zoom.operation == ZoomGraphOperation.BOX && zoom.anchorX == null) {
            zoom.anchorX = zoom.x
            zoom.anchorY = zoom.y
            return
        }
        val newBounds = when (zoom.operation) {
            ZoomGraphOperation.BOX -> {
                val anchorX = zoom.anchorX ?: return
                val anchorY = zoom.anchorY ?: return
                val xMin = min(anchorX, zoom.x)
                val xMax = max(anchorX, zoom.x)
                val yMin = min(anchorY, zoom.y)
                val yMax = max(anchorY, zoom.y)
                if (xMin == xMax || yMin == yMax) return
                listOf(xMin, xMax, yMin, yMax)
            }
            ZoomGraphOperation.IN, ZoomGraphOperation.OUT -> {
                val factor = ZoomMemory.selectedDenominator().toDouble()
                val directionFactor =
                    if (zoom.operation == ZoomGraphOperation.IN) 1.0 / factor else factor
                val halfWidth = (graphWindow.xMax - graphWindow.xMin) * directionFactor / 2.0
                val halfHeight = (graphWindow.yMax - graphWindow.yMin) * directionFactor / 2.0
                listOf(
                    zoom.x - halfWidth,
                    zoom.x + halfWidth,
                    zoom.y - halfHeight,
                    zoom.y + halfHeight
                )
            }
        }
        applyImmediateZoom {
            WindowSettingsMemory.setGraphWindow(
                formatWindowValue(newBounds[0]),
                formatWindowValue(newBounds[1]),
                WindowSettingsMemory.value(2),
                formatWindowValue(newBounds[2]),
                formatWindowValue(newBounds[3]),
                WindowSettingsMemory.value(5),
                WindowSettingsMemory.value(6)
            )
        }
    }

    private fun openGraph() {
        state.view = CalculatorView.GRAPH
        state.zoomGraph = null
    }

    private fun alphaHotkeyFor(key: CalculatorKey): String? = when (key) {
        CalculatorKey.MATH -> "A"
        CalculatorKey.APPS -> "B"
        CalculatorKey.PROGRAM -> "C"
        CalculatorKey.RECIPROCAL -> "D"
        CalculatorKey.SIN -> "E"
        CalculatorKey.COS -> "F"
        CalculatorKey.TAN -> "G"
        else -> null
    }

    private fun digitHotkeyFor(key: CalculatorKey): String? = when (key) {
        CalculatorKey.DIGIT_0 -> "0"
        CalculatorKey.DIGIT_1 -> "1"
        CalculatorKey.DIGIT_2 -> "2"
        CalculatorKey.DIGIT_3 -> "3"
        CalculatorKey.DIGIT_4 -> "4"
        CalculatorKey.DIGIT_5 -> "5"
        CalculatorKey.DIGIT_6 -> "6"
        CalculatorKey.DIGIT_7 -> "7"
        CalculatorKey.DIGIT_8 -> "8"
        CalculatorKey.DIGIT_9 -> "9"
        else -> null
    }

    private fun formatWindowValue(value: Double): String =
        java.math.BigDecimal.valueOf(value)
            .round(java.math.MathContext(12))
            .stripTrailingZeros()
            .toPlainString()

    companion object {
        private const val DEFAULT_GRAPH_ASPECT = 397.0 / 240.0
    }
}
