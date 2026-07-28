package net.amathboi.mi84mod.client.calculator

/** Structured LCD-only views of linear evaluator tokens. Raw expressions remain unchanged. */
sealed interface MathDisplayToken {
    val start: Int
    val endExclusive: Int

    data class Fraction(
        override val start: Int,
        override val endExclusive: Int,
        val whole: String?,
        val numerator: String,
        val denominator: String
    ) : MathDisplayToken

    data class Root(
        override val start: Int,
        override val endExclusive: Int,
        val radicand: String,
        val index: String?,
        val fieldOrder: RootFieldOrder,
        val secondFieldEntered: Boolean,
        val complete: Boolean
    ) : MathDisplayToken

    data class Combinatoric(
        override val start: Int,
        override val endExclusive: Int,
        val leftOperand: String,
        val operator: Char,
        val rightOperand: String,
        val rightOperandEntered: Boolean,
        val complete: Boolean
    ) : MathDisplayToken
}

enum class RootFieldOrder {
    RADICAND_ONLY,
    RADICAND_THEN_INDEX,
    INDEX_THEN_RADICAND
}

enum class MathCursorField {
    ROOT_INDEX,
    COMBINATORIC_OPERAND
}

/** Finds the first fraction, radical, permutation, or combination presentation token. */
object MathDisplayTokens {
    private data class FunctionKind(
        val name: String,
        val type: Type
    )

    private enum class Type {
        FRACTION,
        MIXED,
        SQUARE_ROOT,
        CUBE_ROOT,
        NTH_ROOT,
        INDEXED_ROOT,
        PERMUTATION,
        COMBINATION
    }

    private val functions = listOf(
        FunctionKind("cubeRoot", Type.CUBE_ROOT),
        FunctionKind("nthRoot", Type.NTH_ROOT),
        FunctionKind("root", Type.INDEXED_ROOT),
        FunctionKind("mixed", Type.MIXED),
        FunctionKind("sqrt", Type.SQUARE_ROOT),
        FunctionKind("frac", Type.FRACTION),
        FunctionKind("nPr", Type.PERMUTATION),
        FunctionKind("nCr", Type.COMBINATION)
    )

    fun firstIn(expression: String): MathDisplayToken? {
        expression.indices.forEach { start ->
            functions.firstOrNull { function ->
                expression.startsWith("${function.name}(", start)
            }?.let { function ->
                parse(expression, start, function)?.let { return it }
            }
        }
        return null
    }

    fun incompleteEndingAt(expression: String, endExclusive: Int): MathDisplayToken? {
        if (endExclusive !in 0..expression.length) return null
        val prefix = expression.substring(0, endExclusive)
        var result: MathDisplayToken? = null
        prefix.indices.forEach { start ->
            functions.firstOrNull { function ->
                prefix.startsWith("${function.name}(", start)
            }?.let { function ->
                val token = parse(prefix, start, function)
                if (token != null && !isComplete(token) && token.endExclusive == prefix.length) {
                    result = token
                }
            }
        }
        return result
    }

    fun cursorFieldAt(expression: String, cursor: Int): MathCursorField? {
        if (cursor !in 0..expression.length) return null
        var result: MathCursorField? = null
        var latestStart = -1
        expression.indices.forEach { start ->
            val name = listOf("nthRoot", "root", "nPr", "nCr").firstOrNull {
                expression.startsWith("$it(", start)
            } ?: return@forEach
            val openIndex = start + name.length
            val closeIndex = matchingClose(expression, openIndex) ?: expression.length
            val contentStart = openIndex + 1
            if (cursor !in contentStart..closeIndex || start < latestStart) return@forEach
            val commaIndex = topLevelComma(expression, contentStart, closeIndex)
            val field = when (name) {
                "nPr", "nCr" -> when {
                    commaIndex == null -> MathCursorField.COMBINATORIC_OPERAND
                    cursor <= commaIndex -> MathCursorField.COMBINATORIC_OPERAND
                    else -> MathCursorField.COMBINATORIC_OPERAND
                }
                "root" ->
                    if (commaIndex == null || cursor <= commaIndex) MathCursorField.ROOT_INDEX
                    else null
                "nthRoot" ->
                    if (commaIndex != null && cursor > commaIndex) MathCursorField.ROOT_INDEX
                    else null
                else -> null
            }
            if (field != null) {
                latestStart = start
                result = field
            }
        }
        return result
    }

    private fun parse(
        expression: String,
        start: Int,
        function: FunctionKind
    ): MathDisplayToken? {
        val openIndex = start + function.name.length
        val closeIndex = matchingClose(expression, openIndex)
        val complete = closeIndex != null
        val endExclusive = closeIndex?.plus(1) ?: expression.length
        val argumentEnd = closeIndex ?: expression.length
        val arguments = splitTopLevelArguments(expression.substring(openIndex + 1, argumentEnd))

        return when (function.type) {
            Type.FRACTION, Type.MIXED -> {
                if (!complete) return null
                val expectedSize = if (function.type == Type.MIXED) 3 else 2
                if (arguments.size != expectedSize) return null
                MathDisplayToken.Fraction(
                    start,
                    endExclusive,
                    arguments.getOrNull(0).takeIf { function.type == Type.MIXED },
                    arguments[if (function.type == Type.MIXED) 1 else 0],
                    arguments[if (function.type == Type.MIXED) 2 else 1]
                )
            }
            Type.SQUARE_ROOT, Type.CUBE_ROOT -> {
                if (arguments.size != 1) return null
                MathDisplayToken.Root(
                    start,
                    endExclusive,
                    arguments.single(),
                    if (function.type == Type.CUBE_ROOT) "3" else null,
                    RootFieldOrder.RADICAND_ONLY,
                    secondFieldEntered = false,
                    complete
                )
            }
            Type.NTH_ROOT -> {
                if (arguments.size > 2) return null
                MathDisplayToken.Root(
                    start,
                    endExclusive,
                    arguments.firstOrNull().orEmpty(),
                    arguments.getOrNull(1).orEmpty(),
                    RootFieldOrder.RADICAND_THEN_INDEX,
                    secondFieldEntered = arguments.size == 2,
                    complete
                )
            }
            Type.INDEXED_ROOT -> {
                if (arguments.size > 2) return null
                MathDisplayToken.Root(
                    start,
                    endExclusive,
                    arguments.getOrNull(1).orEmpty(),
                    arguments.firstOrNull().orEmpty(),
                    RootFieldOrder.INDEX_THEN_RADICAND,
                    secondFieldEntered = arguments.size == 2,
                    complete
                )
            }
            Type.PERMUTATION, Type.COMBINATION -> {
                if (arguments.size > 2) return null
                MathDisplayToken.Combinatoric(
                    start,
                    endExclusive,
                    arguments.firstOrNull().orEmpty(),
                    if (function.type == Type.PERMUTATION) 'P' else 'C',
                    arguments.getOrNull(1).orEmpty(),
                    rightOperandEntered = arguments.size == 2,
                    complete
                )
            }
        }
    }

    private fun isComplete(token: MathDisplayToken): Boolean = when (token) {
        is MathDisplayToken.Fraction -> true
        is MathDisplayToken.Root -> token.complete
        is MathDisplayToken.Combinatoric -> token.complete
    }

    private fun matchingClose(expression: String, openIndex: Int): Int? {
        var depth = 0
        for (index in openIndex until expression.length) {
            when (expression[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun topLevelComma(
        expression: String,
        contentStart: Int,
        contentEnd: Int
    ): Int? {
        var depth = 0
        for (index in contentStart until contentEnd) {
            when (expression[index]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) return index
            }
        }
        return null
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
