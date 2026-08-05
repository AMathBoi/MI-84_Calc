package net.amathboi.mi84mod.client.calculator

import kotlin.math.atan2
import kotlin.math.abs
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
        val scale = maxOf(abs(other.real), abs(other.imaginary))
        if (scale == 0.0) throw ComplexEvaluationException(DIVISION_BY_ZERO_ERROR)
        if (!scale.isFinite()) throw ComplexEvaluationException(RESULT_TOO_LARGE_ERROR)

        // Scaling both operands by the denominator's largest component avoids overflowing c²+d²
        // or underflowing it to zero. Every complex division path, including tan and log-base
        // conversion, routes through this operator.
        val denominatorReal = other.real / scale
        val denominatorImaginary = other.imaginary / scale
        val scaledReal = real / scale
        val scaledImaginary = imaginary / scale
        val denominator =
            denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary
        val quotient = ComplexNumber(
            (scaledReal * denominatorReal + scaledImaginary * denominatorImaginary) / denominator,
            (scaledImaginary * denominatorReal - scaledReal * denominatorImaginary) / denominator
        )
        if (!quotient.isFinite()) throw ComplexEvaluationException(RESULT_TOO_LARGE_ERROR)
        if (quotient.real == 0.0 && quotient.imaginary == 0.0 &&
            (real != 0.0 || imaginary != 0.0)
        ) {
            throw ComplexEvaluationException(RESULT_OUT_OF_RANGE_ERROR)
        }
        return quotient
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

    fun squareRoot() = pow(ComplexNumber(0.5))

    fun inverseSine(): ComplexNumber {
        val insideLogarithm =
            IMAGINARY_UNIT * this + (ComplexNumber(1.0) - this * this).squareRoot()
        return -(IMAGINARY_UNIT * insideLogarithm.logarithm())
    }

    fun inverseCosine() = ComplexNumber(Math.PI / 2.0) - inverseSine()

    fun inverseTangent(): ComplexNumber {
        val left = (ComplexNumber(1.0) - IMAGINARY_UNIT * this).logarithm()
        val right = (ComplexNumber(1.0) + IMAGINARY_UNIT * this).logarithm()
        return ComplexNumber(0.0, 0.5) * (left - right)
    }

    fun isFinite(): Boolean = real.isFinite() && imaginary.isFinite()

    fun magnitude(): Double = hypot(real, imaginary)

    private companion object {
        val IMAGINARY_UNIT = ComplexNumber(0.0, 1.0)
        const val DIVISION_BY_ZERO_ERROR = "Error: Division by zero"
        const val RESULT_TOO_LARGE_ERROR = "Error: Result too large"
        const val RESULT_OUT_OF_RANGE_ERROR = "Error: Result out of range"
    }
}

internal class ComplexEvaluationException(message: String) : IllegalArgumentException(message)

/** Recursive-descent parser mirroring the calculator's real parser for complex fallback results. */
internal class ComplexExpressionEvaluator(
    private val expression: String,
    private val previousAnswer: ComplexNumber?,
    private val variableValues: Map<CalculatorVariable, ComplexNumber>,
    private val degrees: Boolean
) {
    private var index = 0

    fun parse(): ComplexNumber {
        val result = parseLogic()
        check(index == expression.length) { "Unexpected expression content" }
        check(result.isFinite()) { "Function result is outside the supported range" }
        return result
    }

    private fun parseLogic(): ComplexNumber {
        var result = parseAnd()
        while (true) {
            val operator = when {
                expression.startsWith("or", index) -> "or"
                expression.startsWith("xor", index) -> "xor"
                else -> return result
            }
            index += operator.length
            val right = parseAnd()
            result = booleanResult(
                if (operator == "or") isTrue(result) || isTrue(right)
                else isTrue(result) xor isTrue(right)
            )
        }
    }

    private fun parseAnd(): ComplexNumber {
        var result = parseRelation()
        while (expression.startsWith("and", index)) {
            index += 3
            val right = parseRelation()
            result = booleanResult(isTrue(result) && isTrue(right))
        }
        return result
    }

    private fun parseRelation(): ComplexNumber {
        var result = parseSum()
        while (true) {
            val operator = RELATIONAL_OPERATORS.firstOrNull {
                expression.startsWith(it, index)
            } ?: return result
            index += operator.length
            val right = parseSum()
            result = booleanResult(
                when (operator) {
                    "=" -> result == right
                    "≠" -> result != right
                    ">", "≥", "<", "≤" -> {
                        check(result.imaginary == 0.0 && right.imaginary == 0.0) {
                            "Complex values cannot be ordered"
                        }
                        when (operator) {
                            ">" -> result.real > right.real
                            "≥" -> result.real >= right.real
                            "<" -> result.real < right.real
                            else -> result.real <= right.real
                        }
                    }
                    else -> error("Unsupported relation: $operator")
                }
            )
        }
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
                expression[position] == '(' ||
                CalculatorVariable.fromSymbol(expression[position]) != null ||
                expression[position] == 'i' || expression[position] == PI_TOKEN ||
                expression[position] == EULER_TOKEN || expression.startsWith(ANSWER_TOKEN, position) ||
                FUNCTIONS.any { expression.startsWith(it, position) }
            )

    private fun parsePower(): ComplexNumber {
        val result = parsePostfix()
        return if (index < expression.length && expression[index] == '^') {
            index++
            result.pow(parseUnary())
        } else {
            result
        }
    }

    private fun parsePostfix(): ComplexNumber {
        var result = parsePrimary()
        while (index < expression.length &&
            (expression[index] == DEGREE_MARKER || expression[index] == RADIAN_MARKER)
        ) {
            result = when (expression[index++]) {
                DEGREE_MARKER ->
                    if (degrees) result else result * ComplexNumber(Math.PI / 180.0)
                RADIAN_MARKER ->
                    if (degrees) result * ComplexNumber(180.0 / Math.PI) else result
                else -> error("Unsupported angle marker")
            }
        }
        return result
    }

    private fun parseUnary(): ComplexNumber =
        if (index < expression.length && expression[index] == '-') {
            index++
            -parseUnary()
        } else {
            parsePower()
        }

    private fun parsePrimary(): ComplexNumber {
        if (expression.startsWith(ANSWER_TOKEN, index)) {
            index += ANSWER_TOKEN.length
            return checkNotNull(previousAnswer) { "No previous answer is available" }
        }
        if (index < expression.length) {
            CalculatorVariable.fromSymbol(expression[index])?.let { variable ->
                index++
                return variableValues.getValue(variable)
            }
        }
        if (index < expression.length && expression[index] == 'i') {
            index++
            return ComplexNumber(0.0, 1.0)
        }
        if (index < expression.length && expression[index] == PI_TOKEN) {
            index++
            return ComplexNumber(Math.PI)
        }
        if (index < expression.length && expression[index] == EULER_TOKEN) {
            index++
            return ComplexNumber(Math.E)
        }

        FUNCTIONS.firstOrNull { expression.startsWith(it, index) }?.let { function ->
            index += function.length
            check(index < expression.length && expression[index] == '(') {
                "Expected opening parenthesis after $function"
            }
            index++
            val arguments = parseFunctionArguments()
            check(index < expression.length && expression[index] == ')') { "Missing closing parenthesis" }
            index++
            return evaluateFunction(function, arguments)
        }

        if (index < expression.length && expression[index] == '(') {
            index++
            val result = parseLogic()
            check(index < expression.length && expression[index] == ')') { "Missing closing parenthesis" }
            index++
            return result
        }

        return parseNumber()
    }

    private fun parseNumber(): ComplexNumber {
        val mantissaStart = index
        while (index < expression.length && (expression[index].isDigit() || expression[index] == '.')) {
            index++
        }
        check(mantissaStart != index) { "Expected a number" }
        val mantissa = expression.substring(mantissaStart, index).toDouble()

        if (!expression.startsWith(SCIENTIFIC_EXPONENT_TOKEN, index)) {
            return ComplexNumber(mantissa)
        }
        index += SCIENTIFIC_EXPONENT_TOKEN.length
        val negative = index < expression.length && expression[index] == '-'
        if (negative) index++
        val exponentStart = index
        while (index < expression.length && expression[index].isDigit()) index++
        check(exponentStart != index) { "Expected scientific exponent digits" }
        check(index == expression.length || expression[index] != '.') {
            "Scientific exponent must be an integer"
        }

        val magnitude = expression.substring(exponentStart, index).toIntOrNull()
            ?: throw ComplexEvaluationException("Error: Result too large")
        val exponent = if (negative) -magnitude else magnitude
        if (magnitude > MAX_SCIENTIFIC_EXPONENT) {
            throw ComplexEvaluationException(
                if (negative) "Error: Result out of range" else "Error: Result too large"
            )
        }
        val value = mantissa * Math.pow(10.0, exponent.toDouble())
        if (!value.isFinite() || (value == 0.0 && mantissa != 0.0)) {
            throw ComplexEvaluationException("Error: Result out of range")
        }
        return ComplexNumber(value)
    }

    private fun parseFunctionArguments(): List<ComplexNumber> {
        check(index < expression.length && expression[index] != ')') {
            "A function argument is required"
        }
        val arguments = mutableListOf<ComplexNumber>()
        do {
            arguments += parseLogic()
            if (index >= expression.length || expression[index] != ',') break
            index++
            check(index < expression.length && expression[index] != ')') {
                "A function argument is required after comma"
            }
        } while (true)
        return arguments
    }

    private fun evaluateFunction(function: String, arguments: List<ComplexNumber>): ComplexNumber {
        if (function == "not") {
            check(arguments.size == 1) { "not requires one argument" }
            return booleanResult(!isTrue(arguments.single()))
        }
        if (function == "abs") {
            check(arguments.size == 1) { "abs requires one argument" }
            return ComplexNumber(arguments.single().magnitude())
        }

        check(arguments.size == 1) { "$function requires one argument" }
        val argument = arguments.single()
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
            INVERSE_SINE -> argument.inverseSine()
            INVERSE_COSINE -> argument.inverseCosine()
            INVERSE_TANGENT -> argument.inverseTangent()
            SQUARE_ROOT -> argument.squareRoot()
            else -> error("Unsupported calculator function: $function")
        }
        val angleAdjustedResult = if (degrees && function in INVERSE_TRIG_FUNCTIONS) {
            result * ComplexNumber(180.0 / Math.PI)
        } else {
            result
        }
        check(angleAdjustedResult.isFinite()) { "Function result is outside the supported range" }
        return angleAdjustedResult
    }

    private fun isTrue(value: ComplexNumber): Boolean =
        value.real != 0.0 || value.imaginary != 0.0

    private fun booleanResult(value: Boolean): ComplexNumber =
        ComplexNumber(if (value) 1.0 else 0.0)

    private companion object {
        const val ANSWER_TOKEN = "Ans"
        const val SCIENTIFIC_EXPONENT_TOKEN = "EE"
        const val PI_TOKEN = 'π'
        const val EULER_TOKEN = 'e'
        const val INVERSE_SINE = "sin⁻¹"
        const val INVERSE_COSINE = "cos⁻¹"
        const val INVERSE_TANGENT = "tan⁻¹"
        const val SQUARE_ROOT = "sqrt"
        const val DEGREE_MARKER = '°'
        const val RADIAN_MARKER = 'ʳ'
        const val MAX_SCIENTIFIC_EXPONENT = 308
        val FUNCTIONS = listOf(
            INVERSE_SINE,
            INVERSE_COSINE,
            INVERSE_TANGENT,
            SQUARE_ROOT,
            "abs",
            "not",
            "sin",
            "cos",
            "tan",
            "log",
            "ln"
        )
        val TRIG_FUNCTIONS = setOf("sin", "cos", "tan")
        val INVERSE_TRIG_FUNCTIONS = setOf(INVERSE_SINE, INVERSE_COSINE, INVERSE_TANGENT)
        val RELATIONAL_OPERATORS = listOf("≠", "≥", "≤", "=", ">", "<")
    }
}
