package net.amathboi.mi84mod.client.calculator

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/** Double-precision principal-value complex arithmetic used when a real evaluation is impossible. */
internal data class ComplexNumber(val real: Double, val imaginary: Double = 0.0) {
    operator fun plus(other: ComplexNumber) =
        ComplexNumber(real + other.real, imaginary + other.imaginary)

    operator fun minus(other: ComplexNumber) =
        ComplexNumber(real - other.real, imaginary - other.imaginary)

    operator fun times(other: ComplexNumber) = ComplexNumber(
        real * other.real - imaginary * other.imaginary,
        real * other.imaginary + imaginary * other.real
    )

    operator fun div(other: ComplexNumber): ComplexNumber {
        val denominator = other.real * other.real + other.imaginary * other.imaginary
        if (denominator == 0.0) throw ComplexEvaluationException("Error: Division by zero")
        return ComplexNumber(
            (real * other.real + imaginary * other.imaginary) / denominator,
            (imaginary * other.real - real * other.imaginary) / denominator
        )
    }

    operator fun unaryMinus() = ComplexNumber(-real, if (imaginary == 0.0) 0.0 else -imaginary)

    fun logarithm(): ComplexNumber {
        if (real == 0.0 && imaginary == 0.0) {
            throw ComplexEvaluationException("Function result is outside the supported range")
        }
        val angle = if (real < 0.0 && imaginary == 0.0) Math.PI else atan2(imaginary, real)
        return ComplexNumber(ln(hypot(real, imaginary)), angle)
    }

    fun exponential(): ComplexNumber {
        val magnitude = exp(real)
        return ComplexNumber(magnitude * cos(imaginary), magnitude * sin(imaginary))
    }

    fun pow(exponent: ComplexNumber): ComplexNumber {
        if (real == 0.0 && imaginary == 0.0) {
            return when {
                exponent.imaginary != 0.0 || exponent.real < 0.0 ->
                    throw ComplexEvaluationException("Error: Division by zero")
                exponent.real == 0.0 -> ComplexNumber(1.0)
                else -> ComplexNumber(0.0)
            }
        }
        return (exponent * logarithm()).exponential()
    }

    fun sine() = ComplexNumber(
        sin(real) * cosh(imaginary),
        cos(real) * sinh(imaginary)
    )

    fun cosine() = ComplexNumber(
        cos(real) * cosh(imaginary),
        -sin(real) * sinh(imaginary)
    )

    fun isFinite(): Boolean = real.isFinite() && imaginary.isFinite()
}

internal class ComplexEvaluationException(message: String) : IllegalArgumentException(message)

/** Recursive-descent parser mirroring the calculator's real parser for complex fallback results. */
internal class ComplexExpressionEvaluator(
    private val expression: String,
    private val previousAnswer: ComplexNumber?,
    private val xValue: Double,
    private val degrees: Boolean
) {
    private var index = 0

    fun parse(): ComplexNumber {
        val result = parseSum()
        check(index == expression.length) { "Unexpected expression content" }
        check(result.isFinite()) { "Function result is outside the supported range" }
        return result
    }

    private fun parseSum(): ComplexNumber {
        var result = parseProduct()
        while (index < expression.length && expression[index] in "+-") {
            result = if (expression[index++] == '+') result + parseProduct() else result - parseProduct()
        }
        return result
    }

    private fun parseProduct(): ComplexNumber {
        var result = parseUnary()
        while (index < expression.length) {
            result = when {
                expression[index] == '*' -> {
                    index++
                    result * parseUnary()
                }
                expression[index] == '/' -> {
                    index++
                    result / parseUnary()
                }
                startsPrimaryAt(index) -> result * parseUnary()
                else -> return result
            }
        }
        return result
    }

    private fun startsPrimaryAt(position: Int): Boolean =
        position < expression.length && (
            expression[position].isDigit() || expression[position] == '.' ||
                expression[position] == '(' || expression[position] == 'X' ||
                expression[position] == 'i' || expression.startsWith("Ans", position) ||
                FUNCTIONS.any { expression.startsWith(it, position) }
            )

    private fun parsePower(): ComplexNumber {
        val result = parsePrimary()
        return if (index < expression.length && expression[index] == '^') {
            index++
            result.pow(parseUnary())
        } else {
            result
        }
    }

    private fun parseUnary(): ComplexNumber =
        if (index < expression.length && expression[index] == '-') {
            index++
            -parseUnary()
        } else {
            parsePower()
        }

    private fun parsePrimary(): ComplexNumber {
        if (expression.startsWith("Ans", index)) {
            index += "Ans".length
            return checkNotNull(previousAnswer) { "No previous answer is available" }
        }
        if (index < expression.length && expression[index] == 'X') {
            index++
            return ComplexNumber(xValue)
        }
        if (index < expression.length && expression[index] == 'i') {
            index++
            return ComplexNumber(0.0, 1.0)
        }

        FUNCTIONS.firstOrNull { expression.startsWith(it, index) }?.let { function ->
            index += function.length
            check(index < expression.length && expression[index] == '(') {
                "Expected opening parenthesis after $function"
            }
            index++
            val argument = parseSum()
            check(index < expression.length && expression[index] == ')') { "Missing closing parenthesis" }
            index++
            return evaluateFunction(function, argument)
        }

        if (index < expression.length && expression[index] == '(') {
            index++
            val result = parseSum()
            check(index < expression.length && expression[index] == ')') { "Missing closing parenthesis" }
            index++
            return result
        }

        val start = index
        while (index < expression.length && (expression[index].isDigit() || expression[index] == '.')) index++
        check(start != index) { "Expected a number" }
        return ComplexNumber(expression.substring(start, index).toDouble())
    }

    private fun evaluateFunction(function: String, argument: ComplexNumber): ComplexNumber {
        val adjustedArgument = if (degrees && function in TRIG_FUNCTIONS) {
            argument * ComplexNumber(Math.PI / 180.0)
        } else {
            argument
        }
        val result = when (function) {
            "sin" -> adjustedArgument.sine()
            "cos" -> adjustedArgument.cosine()
            "tan" -> adjustedArgument.sine() / adjustedArgument.cosine()
            "log" -> argument.logarithm() / ComplexNumber(ln(10.0))
            "ln" -> argument.logarithm()
            else -> error("Unsupported calculator function: $function")
        }
        check(result.isFinite()) { "Function result is outside the supported range" }
        return result
    }

    private companion object {
        val FUNCTIONS = setOf("sin", "cos", "tan", "log", "ln")
        val TRIG_FUNCTIONS = setOf("sin", "cos", "tan")
    }
}
