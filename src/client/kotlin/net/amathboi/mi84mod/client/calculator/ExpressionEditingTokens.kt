package net.amathboi.mi84mod.client.calculator

/** Shared recognition for structured editor tokens whose visible width differs from raw storage. */
object ExpressionEditingTokens {
    data class StructuredFraction(val mixedNumber: Boolean, val fields: List<String>)

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
}
