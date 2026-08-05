package net.amathboi.mi84mod.client.calculator

/** Shared recognition for structured editor tokens whose visible width differs from raw storage. */
object ExpressionEditingTokens {
    data class StructuredFraction(val mixedNumber: Boolean, val fields: List<String>)
    data class TextEdit(val text: String, val cursor: Int)

    private val yFunctionSubscripts = listOf("₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉")

    /**
     * Shared scalar/list function vocabulary. Keeping it here lets editing and expression
     * transformations recognize complete function identifiers instead of treating the Alpha
     * letters inside names such as `logBASE` as independent variables.
     */
    val functionNames = listOf(
        "sin⁻¹",
        "cos⁻¹",
        "tan⁻¹",
        "R►Pr",
        "R►Pθ",
        "P►Rx",
        "P►Ry",
        "mixed",
        "logBASE",
        "nthRoot",
        "root",
        "cubeRoot",
        "remainder",
        "frac",
        "sqrt",
        "nPr",
        "nCr",
        "iPart",
        "fPart",
        "round",
        "abs",
        "min",
        "max",
        "lcm",
        "gcd",
        "int",
        "SortA",
        "SortD",
        "dim",
        "Fill",
        "seq",
        "cumSum",
        "ΔList",
        "augment",
        "mean",
        "median",
        "sum",
        "prod",
        "stdDev",
        "variance",
        "not",
        "sin",
        "cos",
        "tan",
        "log",
        "ln"
    )

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

    /**
     * Replaces only complete scalar-variable tokens. Function names, Ans, graph variables, and
     * list references remain intact even when their storage text contains the same Alpha letter.
     */
    fun substituteScalarVariable(
        expression: String,
        variable: CalculatorVariable,
        replacement: String
    ): String {
        if (variable.symbol !in expression) return expression

        val substituted = StringBuilder(expression.length + replacement.length)
        var index = 0
        while (index < expression.length) {
            val protectedToken = protectedSubstitutionTokenStartingAt(expression, index)
            if (protectedToken != null) {
                substituted.append(protectedToken)
                index += protectedToken.length
            } else {
                val character = expression[index++]
                if (character == variable.symbol) substituted.append(replacement)
                else substituted.append(character)
            }
        }
        return substituted.toString()
    }

    private fun protectedSubstitutionTokenStartingAt(expression: String, position: Int): String? {
        namedVariableStartingAt(expression, position)?.let { return it }
        functionNames.firstOrNull { function ->
            expression.startsWith(function, position) &&
                expression.getOrNull(position + function.length) == '('
        }?.let { return it }
        listOf("Ans", "EE").firstOrNull { expression.startsWith(it, position) }?.let { return it }
        if (expression.getOrNull(position) == 'L' && expression.getOrNull(position + 1) in '1'..'6') {
            return expression.substring(position, position + 2)
        }
        if (expression.getOrNull(position) == '@') {
            var end = position + 1
            while (expression.getOrNull(end) in 'A'..'Z') end++
            if (end > position + 1) return expression.substring(position, end)
        }
        return null
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
