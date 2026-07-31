package net.amathboi.mi84mod.client.calculator

/** Parser for the nonvisual `{element,...}` list token. Element evaluation belongs to the caller. */
object CalculatorListLiteral {
    fun parse(text: String, evaluateElement: (String) -> CalculatorScalarValue): CalculatorListValue {
        require(text.startsWith('{') && text.endsWith('}')) { "Invalid list literal" }
        val content = text.substring(1, text.lastIndex)
        if (content.isBlank()) return CalculatorListValue(emptyList())
        val elements = splitTopLevel(content)
        require(elements.none { it.isBlank() }) { "Invalid list literal" }
        return CalculatorListValue(elements.map(evaluateElement))
    }

    private fun splitTopLevel(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, character ->
            when (character) {
                '(', '{' -> depth++
                ')', '}' -> { depth--; require(depth >= 0) { "Invalid list literal" } }
                ',' -> if (depth == 0) { result += text.substring(start, index); start = index + 1 }
            }
        }
        require(depth == 0) { "Invalid list literal" }
        result += text.substring(start)
        return result
    }
}
