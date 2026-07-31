package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.sqrt

/** Pure list operations. Menu routing and list-editor presentation deliberately do not live here. */
object CalculatorListOperations {
    private val mc = MathContext.DECIMAL128

    fun dimension(list: CalculatorListValue): BigDecimal = BigDecimal.valueOf(list.dimension.toLong())

    fun fill(value: CalculatorScalarValue, dimension: Int): CalculatorListValue {
        require(dimension in 0..CalculatorListValue.MAX_LIST_LENGTH) { "Invalid dimension" }
        return CalculatorListValue(List(dimension) { value })
    }

    fun sequence(start: BigDecimal, end: BigDecimal, step: BigDecimal = BigDecimal.ONE): CalculatorListValue {
        require(step.compareTo(BigDecimal.ZERO) != 0) { "Invalid step" }
        require((end - start).signum() == 0 || (end - start).signum() == step.signum()) { "Invalid step" }
        val result = mutableListOf<CalculatorScalarValue>()
        var current = start
        while ((step.signum() > 0 && current <= end) || (step.signum() < 0 && current >= end)) {
            require(result.size < CalculatorListValue.MAX_LIST_LENGTH) { "List is too long" }
            result += CalculatorScalarValue(current)
            current = current.add(step, mc)
        }
        return CalculatorListValue(result)
    }

    fun cumulativeSum(list: CalculatorListValue): CalculatorListValue {
        requireReal(list)
        var sum = BigDecimal.ZERO
        return CalculatorListValue(list.values.map { value ->
            sum = sum.add(value.real, mc)
            CalculatorScalarValue(sum)
        })
    }

    fun deltaList(list: CalculatorListValue): CalculatorListValue {
        requireReal(list)
        return CalculatorListValue(list.values.zipWithNext { left, right ->
            CalculatorScalarValue(right.real.subtract(left.real, mc))
        })
    }

    fun augment(first: CalculatorListValue, second: CalculatorListValue): CalculatorListValue =
        CalculatorListValue(first.values + second.values)

    fun sortAscending(list: CalculatorListValue): CalculatorListValue {
        requireReal(list)
        return CalculatorListValue(list.values.sortedBy { it.real })
    }

    fun sortDescending(list: CalculatorListValue): CalculatorListValue {
        requireReal(list)
        return CalculatorListValue(list.values.sortedByDescending { it.real })
    }

    /** Sorts the primary list and applies the same permutation to every dependent list. */
    fun sortTogether(lists: List<CalculatorListValue>, ascending: Boolean): List<CalculatorListValue> {
        require(lists.isNotEmpty()) { "A list is required" }
        lists.forEach(::requireReal)
        val dimension = lists.first().dimension
        require(lists.all { it.dimension == dimension }) { "Invalid dimension" }
        val indices = lists.first().values.indices.sortedWith { left, right ->
            val comparison = lists.first().values[left].real.compareTo(lists.first().values[right].real)
            if (ascending) comparison else -comparison
        }
        return lists.map { list -> CalculatorListValue(indices.map(list.values::get)) }
    }

    fun sum(list: CalculatorListValue): BigDecimal = reals(list).fold(BigDecimal.ZERO) { a, b -> a.add(b, mc) }
    fun product(list: CalculatorListValue): BigDecimal = reals(list).fold(BigDecimal.ONE) { a, b -> a.multiply(b, mc) }
    fun minimum(list: CalculatorListValue): BigDecimal = reals(list).minOrNull() ?: error("Empty list")
    fun maximum(list: CalculatorListValue): BigDecimal = reals(list).maxOrNull() ?: error("Empty list")
    fun mean(list: CalculatorListValue): BigDecimal {
        val values = reals(list)
        require(values.isNotEmpty()) { "Empty list" }
        return values.fold(BigDecimal.ZERO) { a, b -> a.add(b, mc) }.divide(BigDecimal.valueOf(values.size.toLong()), mc)
    }
    fun median(list: CalculatorListValue): BigDecimal {
        val values = reals(list).sorted()
        require(values.isNotEmpty()) { "Empty list" }
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else values[middle - 1].add(values[middle], mc).divide(BigDecimal("2"), mc)
    }
    fun variance(list: CalculatorListValue): BigDecimal {
        val values = reals(list)
        require(values.size >= 2) { "Invalid dimension" }
        val average = mean(list)
        return values.fold(BigDecimal.ZERO) { total, value ->
            total.add(value.subtract(average, mc).pow(2, mc), mc)
        }.divide(BigDecimal.valueOf((values.size - 1).toLong()), mc)
    }
    fun standardDeviation(list: CalculatorListValue): BigDecimal =
        BigDecimal.valueOf(sqrt(variance(list).toDouble())).setScale(14, RoundingMode.HALF_UP).stripTrailingZeros()

    private fun reals(list: CalculatorListValue): List<BigDecimal> {
        requireReal(list)
        return list.values.map { it.real }
    }
    private fun requireReal(list: CalculatorListValue) {
        require(!list.hasComplexValues) { "Complex list unsupported" }
    }
}
