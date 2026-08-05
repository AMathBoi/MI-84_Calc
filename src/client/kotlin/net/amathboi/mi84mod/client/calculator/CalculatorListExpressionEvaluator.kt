package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal

/** Evaluates the reviewed single-list LIST OPS/MATH vocabulary without changing scalar parsing. */
object CalculatorListExpressionEvaluator {
    sealed interface Value {
        data class Scalar(val value: CalculatorScalarValue) : Value
        data class List(val value: CalculatorListValue, val referenceName: String? = null) : Value
    }

    class NotApplicable : IllegalArgumentException()

    fun evaluate(expression: String, scalar: (String) -> CalculatorScalarValue): Value {
        CalculatorListName.fromToken(expression)?.let {
            return Value.List(CalculatorListMemory.value(it), it.token)
        }
        if (expression.startsWith('@')) {
            val name = expression.drop(1)
            CalculatorListMemory.value(name)?.let { return Value.List(it, name) }
        }
        if (expression.startsWith('{') && expression.endsWith('}')) {
            return Value.List(CalculatorListLiteral.parse(expression) { element -> scalar(element) })
        }
        val call = functionCall(expression) ?: throw NotApplicable()
        if (call.first == "seq") return sequence(call.second, scalar)
        val arguments = splitArguments(call.second).map { parseArgument(it, scalar) }
        return when (call.first) {
            "dim" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.dimension(arguments.onlyList())))
            "Fill" -> fill(arguments)
            "cumSum" -> Value.List(CalculatorListOperations.cumulativeSum(arguments.onlyList()))
            "ΔList" -> Value.List(CalculatorListOperations.deltaList(arguments.onlyList()))
            "augment" -> {
                arguments.requireSize(2, "augment")
                Value.List(CalculatorListOperations.augment(arguments.list(0), arguments.list(1)))
            }
            "SortA" -> sort(arguments, ascending = true)
            "SortD" -> sort(arguments, ascending = false)
            "min" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.minimum(arguments.onlyList())))
            "max" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.maximum(arguments.onlyList())))
            "mean" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.mean(arguments.onlyList())))
            "median" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.median(arguments.onlyList())))
            "sum" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.sum(arguments.onlyList())))
            "prod" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.product(arguments.onlyList())))
            "stdDev" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.standardDeviation(arguments.onlyList())))
            "variance" -> Value.Scalar(CalculatorScalarValue(CalculatorListOperations.variance(arguments.onlyList())))
            else -> throw NotApplicable()
        }
    }

    private fun parseArgument(text: String, scalar: (String) -> CalculatorScalarValue): Value =
        runCatching { evaluate(text, scalar) }.getOrElse { exception ->
            if (exception is NotApplicable) Value.Scalar(scalar(text)) else throw exception
        }

    private fun functionCall(expression: String): Pair<String, String>? {
        val opening = expression.indexOf('(')
        if (opening <= 0 || !expression.endsWith(')')) return null
        return expression.substring(0, opening) to expression.substring(opening + 1, expression.length - 1)
    }

    private fun splitArguments(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, character ->
            when (character) {
                '(', '{' -> depth++
                ')', '}' -> depth--
                ',' -> if (depth == 0) { result += text.substring(start, index); start = index + 1 }
            }
            require(depth >= 0) { "Invalid list expression" }
        }
        require(depth == 0) { "Invalid list expression" }
        result += text.substring(start)
        require(result.none { it.isBlank() }) { "Invalid list expression" }
        return result
    }

    private fun List<Value>.list(index: Int): CalculatorListValue =
        (getOrNull(index) as? Value.List)?.value ?: error("List required")
    private fun List<Value>.listValue(index: Int): Value.List =
        getOrNull(index) as? Value.List ?: error("List required")
    private fun List<Value>.onlyList(): CalculatorListValue {
        requireSize(1, "List function")
        return list(0)
    }
    private fun List<Value>.scalar(index: Int): CalculatorScalarValue =
        (getOrNull(index) as? Value.Scalar)?.value ?: error("Scalar required")
    private fun List<Value>.real(index: Int): BigDecimal = scalar(index).let {
        require(it.imaginary == null) { "Real scalar required" }; it.real
    }
    private fun List<Value>.realOrNull(index: Int): BigDecimal? = getOrNull(index)?.let {
        (it as? Value.Scalar)?.value?.takeIf { value -> value.imaginary == null }?.real
    }
    private fun List<Value>.integer(index: Int): Int = real(index).intValueExact()
    private fun List<Value>.requireSize(size: Int, function: String) {
        require(this.size == size) { "$function requires $size arguments" }
    }

    private fun fill(arguments: List<Value>): Value.List {
        require(arguments.size == 2) { "Fill requires two arguments" }
        val value = arguments.scalar(0)
        val destination = arguments[1]
        return if (destination is Value.List) {
            val name = destination.referenceName ?: error("A named list is required")
            val filled = CalculatorListOperations.fill(value, destination.value.dimension)
            check(CalculatorListMemory.set(name, filled)) { "Unknown list" }
            Value.List(filled, name)
        } else {
            Value.List(CalculatorListOperations.fill(value, arguments.integer(1)))
        }
    }

    private fun sort(arguments: List<Value>, ascending: Boolean): Value.List {
        require(arguments.isNotEmpty()) { "Sort requires at least one list" }
        val lists = arguments.indices.map { index -> arguments.listValue(index) }
        require(lists.all { it.referenceName != null }) { "Sort requires named lists" }
        val sorted = CalculatorListOperations.sortTogether(lists.map(Value.List::value), ascending)
        lists.zip(sorted).forEach { (source, value) ->
            check(CalculatorListMemory.set(source.referenceName!!, value)) { "Unknown list" }
        }
        return Value.List(sorted.first(), lists.first().referenceName)
    }

    private fun sequence(
        argumentText: String,
        scalar: (String) -> CalculatorScalarValue
    ): Value.List {
        val raw = splitArguments(argumentText)
        if (raw.size in 2..3) {
            val start = scalar(raw[0]).realValue()
            val end = scalar(raw[1]).realValue()
            val step = raw.getOrNull(2)?.let(scalar)?.realValue() ?: BigDecimal.ONE
            return Value.List(CalculatorListOperations.sequence(start, end, step))
        }
        require(raw.size in 4..5) { "seq requires 4 or 5 arguments" }
        val variable = raw[1].singleOrNull()?.let(CalculatorVariable::fromSymbol)
            ?: error("seq requires a scalar variable")
        val begin = scalar(raw[2]).realValue()
        val end = scalar(raw[3]).realValue()
        val step = raw.getOrNull(4)?.let(scalar)?.realValue() ?: BigDecimal.ONE
        val indices = CalculatorListOperations.sequence(begin, end, step)
        return Value.List(
            CalculatorListValue(indices.values.map { index ->
                val substituted = ExpressionEditingTokens.substituteScalarVariable(
                    raw[0],
                    variable,
                    "(${index.real.toPlainString()})"
                )
                scalar(substituted).also {
                    require(it.imaginary == null) { "Complex list unsupported" }
                }
            })
        )
    }

    private fun CalculatorScalarValue.realValue(): BigDecimal {
        require(imaginary == null) { "Real scalar required" }
        return real
    }
}
