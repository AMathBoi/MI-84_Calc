package net.amathboi.mi84mod.client.calculator.input

import net.amathboi.mi84mod.client.calculator.CalculatorVariable
import net.amathboi.mi84mod.client.calculator.ui.CalculatorView
import net.amathboi.mi84mod.client.calculator.ui.CompactMenuId
import net.amathboi.mi84mod.client.calculator.ui.FunctionMenuTab

enum class ModifierLayer {
    NORMAL,
    SECOND,
    ALPHA
}

/** Logical commands are independent of texture coordinates and Minecraft input APIs. */
sealed interface CalculatorCommand {
    data class Digit(val value: Char) : CalculatorCommand
    data class Operator(val value: Char) : CalculatorCommand
    data class Function(val name: String) : CalculatorCommand
    data class OpenView(val view: CalculatorView) : CalculatorCommand
    data class OpenCompactMenu(val menu: CompactMenuId) : CalculatorCommand
    data class OpenFunctionMenu(val tab: FunctionMenuTab) : CalculatorCommand
    data object QuitToHome : CalculatorCommand
    data object BeginTrace : CalculatorCommand
    data object ToggleSecond : CalculatorCommand
    data object ToggleAlpha : CalculatorCommand
    data object Decimal : CalculatorCommand
    data object Negative : CalculatorCommand
    data object Square : CalculatorCommand
    data object Reciprocal : CalculatorCommand
    data object OpenParenthesis : CalculatorCommand
    data object CloseParenthesis : CalculatorCommand
    data object Comma : CalculatorCommand
    data object Variable : CalculatorCommand
    data object Store : CalculatorCommand
    data object Left : CalculatorCommand
    data object Right : CalculatorCommand
    data object Up : CalculatorCommand
    data object Down : CalculatorCommand
    data object Delete : CalculatorCommand
    data object Clear : CalculatorCommand
    data object Enter : CalculatorCommand
    data object InsertAns : CalculatorCommand
    data object InsertImaginaryUnit : CalculatorCommand
    data object InsertPi : CalculatorCommand
    data object InsertEuler : CalculatorCommand
    data object InsertInverseSine : CalculatorCommand
    data object InsertInverseCosine : CalculatorCommand
    data object InsertInverseTangent : CalculatorCommand
    data object InsertTenPower : CalculatorCommand
    data object InsertEulerPower : CalculatorCommand
    data object InsertSquareRoot : CalculatorCommand
    data object InsertScientificExponent : CalculatorCommand
    data object RecallEntry : CalculatorCommand
    data object ToggleInsertMode : CalculatorCommand
    data class InsertVariable(val variable: CalculatorVariable) : CalculatorCommand
    data class Placeholder(val key: CalculatorKey, val layer: ModifierLayer) : CalculatorCommand
    data class Unsupported(val key: CalculatorKey) : CalculatorCommand
}

/** Complete command table for primary keys, with explicit placeholders for shifted layers. */
object CalculatorKeyBindings {
    fun resolve(key: CalculatorKey, layer: ModifierLayer): CalculatorCommand {
        if (key == CalculatorKey.SECOND) return CalculatorCommand.ToggleSecond
        if (key == CalculatorKey.ALPHA) return CalculatorCommand.ToggleAlpha
        if (key == CalculatorKey.MODE && layer == ModifierLayer.SECOND) {
            return CalculatorCommand.QuitToHome
        }
        return when (layer) {
            ModifierLayer.NORMAL -> primaryCommand(key)
            ModifierLayer.SECOND -> secondCommand(key)
            ModifierLayer.ALPHA -> alphaCommand(key)
        }
    }

    private fun secondCommand(key: CalculatorKey): CalculatorCommand = when (key) {
        CalculatorKey.NEGATIVE -> CalculatorCommand.InsertAns
        CalculatorKey.DECIMAL -> CalculatorCommand.InsertImaginaryUnit
        CalculatorKey.POWER -> CalculatorCommand.InsertPi
        CalculatorKey.DIVIDE -> CalculatorCommand.InsertEuler
        CalculatorKey.SIN -> CalculatorCommand.InsertInverseSine
        CalculatorKey.COS -> CalculatorCommand.InsertInverseCosine
        CalculatorKey.TAN -> CalculatorCommand.InsertInverseTangent
        CalculatorKey.LOG -> CalculatorCommand.InsertTenPower
        CalculatorKey.LN -> CalculatorCommand.InsertEulerPower
        CalculatorKey.SQUARE -> CalculatorCommand.InsertSquareRoot
        CalculatorKey.COMMA -> CalculatorCommand.InsertScientificExponent
        CalculatorKey.ENTER -> CalculatorCommand.RecallEntry
        CalculatorKey.DELETE -> CalculatorCommand.ToggleInsertMode
        CalculatorKey.MATH -> CalculatorCommand.OpenCompactMenu(CompactMenuId.TEST)
        CalculatorKey.APPS -> CalculatorCommand.OpenCompactMenu(CompactMenuId.ANGLE)
        else -> CalculatorCommand.Placeholder(key, ModifierLayer.SECOND)
    }

    private fun alphaCommand(key: CalculatorKey): CalculatorCommand = when (key) {
        CalculatorKey.Y_EQUALS -> CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FRAC)
        CalculatorKey.WINDOW -> CalculatorCommand.OpenFunctionMenu(FunctionMenuTab.FUNC)
        CalculatorKey.MATH -> CalculatorCommand.InsertVariable(CalculatorVariable.A)
        CalculatorKey.APPS -> CalculatorCommand.InsertVariable(CalculatorVariable.B)
        CalculatorKey.PROGRAM -> CalculatorCommand.InsertVariable(CalculatorVariable.C)
        CalculatorKey.RECIPROCAL -> CalculatorCommand.InsertVariable(CalculatorVariable.D)
        CalculatorKey.SIN -> CalculatorCommand.InsertVariable(CalculatorVariable.E)
        CalculatorKey.COS -> CalculatorCommand.InsertVariable(CalculatorVariable.F)
        CalculatorKey.TAN -> CalculatorCommand.InsertVariable(CalculatorVariable.G)
        CalculatorKey.POWER -> CalculatorCommand.InsertVariable(CalculatorVariable.H)
        CalculatorKey.SQUARE -> CalculatorCommand.InsertVariable(CalculatorVariable.I)
        CalculatorKey.COMMA -> CalculatorCommand.InsertVariable(CalculatorVariable.J)
        CalculatorKey.OPEN_PARENTHESIS -> CalculatorCommand.InsertVariable(CalculatorVariable.K)
        CalculatorKey.CLOSE_PARENTHESIS -> CalculatorCommand.InsertVariable(CalculatorVariable.L)
        CalculatorKey.DIVIDE -> CalculatorCommand.InsertVariable(CalculatorVariable.M)
        CalculatorKey.LOG -> CalculatorCommand.InsertVariable(CalculatorVariable.N)
        CalculatorKey.DIGIT_7 -> CalculatorCommand.InsertVariable(CalculatorVariable.O)
        CalculatorKey.DIGIT_8 -> CalculatorCommand.InsertVariable(CalculatorVariable.P)
        CalculatorKey.DIGIT_9 -> CalculatorCommand.InsertVariable(CalculatorVariable.Q)
        CalculatorKey.MULTIPLY -> CalculatorCommand.InsertVariable(CalculatorVariable.R)
        CalculatorKey.LN -> CalculatorCommand.InsertVariable(CalculatorVariable.S)
        CalculatorKey.DIGIT_4 -> CalculatorCommand.InsertVariable(CalculatorVariable.T)
        CalculatorKey.DIGIT_5 -> CalculatorCommand.InsertVariable(CalculatorVariable.U)
        CalculatorKey.DIGIT_6 -> CalculatorCommand.InsertVariable(CalculatorVariable.V)
        CalculatorKey.SUBTRACT -> CalculatorCommand.InsertVariable(CalculatorVariable.W)
        CalculatorKey.STORE -> CalculatorCommand.InsertVariable(CalculatorVariable.X)
        CalculatorKey.DIGIT_1 -> CalculatorCommand.InsertVariable(CalculatorVariable.Y)
        CalculatorKey.DIGIT_2 -> CalculatorCommand.InsertVariable(CalculatorVariable.Z)
        CalculatorKey.DIGIT_3 -> CalculatorCommand.InsertVariable(CalculatorVariable.THETA)
        else -> CalculatorCommand.Placeholder(key, ModifierLayer.ALPHA)
    }

    private fun primaryCommand(key: CalculatorKey): CalculatorCommand = when (key) {
        CalculatorKey.Y_EQUALS -> CalculatorCommand.OpenView(CalculatorView.Y_EQUALS)
        CalculatorKey.WINDOW -> CalculatorCommand.OpenView(CalculatorView.WINDOW)
        CalculatorKey.ZOOM -> CalculatorCommand.OpenView(CalculatorView.ZOOM)
        CalculatorKey.TRACE -> CalculatorCommand.BeginTrace
        CalculatorKey.GRAPH -> CalculatorCommand.OpenView(CalculatorView.GRAPH)
        CalculatorKey.MODE -> CalculatorCommand.OpenView(CalculatorView.MODE)
        CalculatorKey.DELETE -> CalculatorCommand.Delete
        CalculatorKey.DOWN -> CalculatorCommand.Down
        CalculatorKey.UP -> CalculatorCommand.Up
        CalculatorKey.VARIABLE -> CalculatorCommand.Variable
        CalculatorKey.LEFT -> CalculatorCommand.Left
        CalculatorKey.RIGHT -> CalculatorCommand.Right
        CalculatorKey.CLEAR -> CalculatorCommand.Clear
        CalculatorKey.RECIPROCAL -> CalculatorCommand.Reciprocal
        CalculatorKey.SIN -> CalculatorCommand.Function("sin")
        CalculatorKey.COS -> CalculatorCommand.Function("cos")
        CalculatorKey.TAN -> CalculatorCommand.Function("tan")
        CalculatorKey.POWER -> CalculatorCommand.Operator('^')
        CalculatorKey.SQUARE -> CalculatorCommand.Square
        CalculatorKey.COMMA -> CalculatorCommand.Comma
        CalculatorKey.OPEN_PARENTHESIS -> CalculatorCommand.OpenParenthesis
        CalculatorKey.CLOSE_PARENTHESIS -> CalculatorCommand.CloseParenthesis
        CalculatorKey.DIVIDE -> CalculatorCommand.Operator('/')
        CalculatorKey.LOG -> CalculatorCommand.Function("log")
        CalculatorKey.DIGIT_7 -> CalculatorCommand.Digit('7')
        CalculatorKey.DIGIT_8 -> CalculatorCommand.Digit('8')
        CalculatorKey.DIGIT_9 -> CalculatorCommand.Digit('9')
        CalculatorKey.MULTIPLY -> CalculatorCommand.Operator('*')
        CalculatorKey.LN -> CalculatorCommand.Function("ln")
        CalculatorKey.DIGIT_4 -> CalculatorCommand.Digit('4')
        CalculatorKey.DIGIT_5 -> CalculatorCommand.Digit('5')
        CalculatorKey.DIGIT_6 -> CalculatorCommand.Digit('6')
        CalculatorKey.SUBTRACT -> CalculatorCommand.Operator('-')
        CalculatorKey.STORE -> CalculatorCommand.Store
        CalculatorKey.DIGIT_1 -> CalculatorCommand.Digit('1')
        CalculatorKey.DIGIT_2 -> CalculatorCommand.Digit('2')
        CalculatorKey.DIGIT_3 -> CalculatorCommand.Digit('3')
        CalculatorKey.ADD -> CalculatorCommand.Operator('+')
        CalculatorKey.DIGIT_0 -> CalculatorCommand.Digit('0')
        CalculatorKey.DECIMAL -> CalculatorCommand.Decimal
        CalculatorKey.NEGATIVE -> CalculatorCommand.Negative
        CalculatorKey.ENTER -> CalculatorCommand.Enter
        CalculatorKey.SECOND, CalculatorKey.ALPHA -> error("Modifier keys are resolved before primary commands")
        CalculatorKey.STAT,
        CalculatorKey.MATH,
        CalculatorKey.APPS,
        CalculatorKey.PROGRAM,
        CalculatorKey.VARS,
        CalculatorKey.ON -> CalculatorCommand.Unsupported(key)
    }
}
