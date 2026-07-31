package net.amathboi.mi84mod.client.calculator

/** Shared recognition for structured editor tokens whose visible width differs from raw storage. */
object ExpressionEditingTokens {
    data class StructuredFraction(val mixedNumber: Boolean, val fields: List<String>)
    data class TextEdit(val text: String, val cursor: Int)

    private val yFunctionSubscripts = listOf("₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉")

    private val namedVariableTokens =
        (
            listOf(
                "TraceStep",
                "ZXmin",
                "ZXmax",
                "ZXscl",
                "ZYmin",
                "ZYmax",
                "ZYscl",
                "ZXres",
                "Xmin",
                "Xmax",
                "Xscl",
                "Ymin",
                "Ymax",
                "Yscl",
                "Xres",
                "ΔX"
            ) + (1..9).flatMap { index ->
                listOf(yFunctionToken(index), "Y$index")
            }
            )
            .sortedByDescending(String::length)

    /** New insertions use a distinct token; raw Y1-Y9 remain readable for existing save files. */
    fun yFunctionToken(index: Int): String =
        "Y${yFunctionSubscripts[index - 1]}"

    fun yFunctionIndex(token: String): Int? {
        if (token.length != 2 || token[0] != 'Y') return null
        return token[1].digitToIntOrNull()?.takeIf { it in 1..9 }
            ?: yFunctionSubscripts.indexOf(token.substring(1))
                .takeIf { it >= 0 }
                ?.plus(1)
    }

    /**
     * Y followed by a typed digit means scalar multiplication. Named Y functions enter through
     * [yFunctionToken], so their identity no longer depends on an ambiguous raw Y-plus-digit pair.
     */
    fun digitEntryText(expression: String, cursor: Int, digit: Char): String =
        if (expression.getOrNull(cursor - 1) == 'Y' &&
            namedVariableEndingAt(expression, cursor) == null
        ) {
            "*$digit"
        } else {
            digit.toString()
        }

    /** Shared calculator-style sign toggle used by Home, Y=, and Window editors. */
    fun toggleOperandSign(
        expression: String,
        cursor: Int,
        maximumLength: Int
    ): TextEdit? {
        scientificExponentSignIndex(expression, cursor)?.let { signIndex ->
            return if (expression.getOrNull(signIndex) == '-') {
                TextEdit(
                    expression.removeRange(signIndex, signIndex + 1),
                    cursor - if (signIndex < cursor) 1 else 0
                )
            } else if (expression.length < maximumLength) {
                TextEdit(
                    expression.substring(0, signIndex) + '-' + expression.substring(signIndex),
                    cursor + if (signIndex <= cursor) 1 else 0
                )
            } else {
                null
            }
        }

        val operandStart = currentOperandStart(expression, cursor)
        return if (expression.getOrNull(operandStart) == '-') {
            TextEdit(
                expression.removeRange(operandStart, operandStart + 1),
                cursor - if (operandStart < cursor) 1 else 0
            )
        } else if (expression.length < maximumLength) {
            TextEdit(
                expression.substring(0, operandStart) + '-' + expression.substring(operandStart),
                cursor + if (operandStart <= cursor) 1 else 0
            )
        } else {
            null
        }
    }

    fun namedVariableStartingAt(expression: String, position: Int): String? =
        namedVariableTokens.firstOrNull { expression.startsWith(it, position) }

    fun namedVariableEndingAt(expression: String, position: Int): String? =
        namedVariableTokens.firstOrNull { token ->
            position >= token.length &&
                expression.regionMatches(position - token.length, token, 0, token.length)
        }

    fun structuredFractionStartingAt(expression: String, position: Int): String? {
        val function = when {
            expression.startsWith("frac(", position) -> "frac"
            expression.startsWith("mixed(", position) -> "mixed"
            else -> return null
        }
        val openIndex = position + function.length
        var depth = 0
        for (index in openIndex until expression.length) {
            when (expression[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return expression.substring(position, index + 1)
                }
            }
        }
        return null
    }

    fun structuredFractionEndingAt(expression: String, position: Int): String? {
        if (position <= 0 || expression.getOrNull(position - 1) != ')') return null
        var searchIndex = 0
        while (searchIndex < position) {
            val fracIndex = expression.indexOf("frac(", searchIndex)
            val mixedIndex = expression.indexOf("mixed(", searchIndex)
            val start = listOf(fracIndex, mixedIndex).filter { it >= 0 }.minOrNull() ?: return null
            val token = structuredFractionStartingAt(expression, start)
            if (token != null && start + token.length == position) return token
            searchIndex = start + 1
        }
        return null
    }

    fun parseStructuredFraction(token: String): StructuredFraction? {
        val function = when {
            token.startsWith("frac(") -> "frac"
            token.startsWith("mixed(") -> "mixed"
            else -> return null
        }
        if (structuredFractionStartingAt(token, 0) != token) return null
        val arguments = splitTopLevelArguments(
            token.substring(function.length + 1, token.lastIndex)
        )
        val expectedSize = if (function == "mixed") 3 else 2
        if (arguments.size != expectedSize) return null
        return StructuredFraction(function == "mixed", arguments)
    }

    private fun splitTopLevelArguments(arguments: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        arguments.forEachIndexed { index, character ->
            when (character) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    result += arguments.substring(start, index)
                    start = index + 1
                }
            }
        }
        result += arguments.substring(start)
        return result
    }

    private fun scientificExponentSignIndex(expression: String, cursor: Int): Int? {
        val markerIndex = expression.lastIndexOf("EE", startIndex = (cursor - 1).coerceAtLeast(0))
        if (markerIndex < currentOperandStart(expression, cursor)) return null

        val signIndex = markerIndex + 2
        if (cursor < signIndex) return null
        val exponentPrefix = expression.substring(signIndex, cursor)
        val digits = exponentPrefix.removePrefix("-")
        return signIndex.takeIf {
            (exponentPrefix.isEmpty() || exponentPrefix == "-" || digits.all(Char::isDigit))
        }
    }

    private fun currentOperandStart(expression: String, cursor: Int): Int {
        for (index in cursor - 1 downTo 0) {
            when (expression[index]) {
                '+', '*', '/', '^', '(', ',', '=', '≠', '≥', '≤', '>', '<' -> return index + 1
                '-' -> if (index > 0 && endsOperandAt(expression, index)) return index + 1
            }
        }
        return 0
    }

    private fun endsOperandAt(expression: String, endExclusive: Int): Boolean {
        if (namedVariableEndingAt(expression, endExclusive) != null ||
            structuredFractionEndingAt(expression, endExclusive) != null
        ) {
            return true
        }
        val previous = expression.getOrNull(endExclusive - 1) ?: return false
        return previous.isDigit() || previous == '.' || previous == ')' || previous == '!' ||
            CalculatorVariable.fromSymbol(previous) != null ||
            previous == 'i' || previous == 'π' || previous == 'e' ||
            expression.substring(0, endExclusive).endsWith("Ans")
    }
}
