package net.amathboi.mi84mod.client.calculator.input

import net.amathboi.mi84mod.client.calculator.ui.CalculatorView

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
        if (layer != ModifierLayer.NORMAL) return CalculatorCommand.Placeholder(key, layer)
        return primaryCommand(key)
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
