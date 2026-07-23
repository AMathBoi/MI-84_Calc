package net.amathboi.mi84mod.client.calculator

import net.fabricmc.loader.api.FabricLoader
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Persistent calculator input and evaluated history. */
object CalculatorDisplayMemory {
    data class SubmittedEntry(
        val input: String,
        val result: String,
        val rawResult: BigDecimal? = null,
        val rawImaginaryResult: BigDecimal? = null
    )
    data class HistoryLine(val entryIndex: Int, val isResult: Boolean, val text: String)
    private data class EvaluationResult(
        val display: String,
        val value: BigDecimal? = null,
        val imaginaryValue: BigDecimal? = null
    )

    private const val MAX_CHARACTERS = 31
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_display_memory.txt")

    private val submittedEntries = mutableListOf<SubmittedEntry>()
    private var currentEntry = ""
    // Index between expression tokens. Function names plus their opening parenthesis are one token.
    private var cursor = 0
    // X mirrors the calculator's default graphing variable and is retained in the memory file.
    private var xValue = BigDecimal.ZERO
    // Earlier entries remain in the memory file but are no longer shown on the LCD.
    private var firstVisibleSubmittedIndex = 0

    init {
        load()
        cursor = currentEntry.length
    }

    fun current(): String = currentEntry

    fun cursorPosition(): Int = cursor

    /** Width of the token under the cursor, used to draw the block cursor over it. */
    fun cursorTokenLength(): Int =
        functionTokenStartingAt(cursor)?.length ?: if (cursor < currentEntry.length) 1 else 0

    fun submitted(): List<SubmittedEntry> = submittedEntries.drop(firstVisibleSubmittedIndex)

    /** All saved entries, including rows currently hidden because the LCD is full. */
    fun allSubmitted(): List<SubmittedEntry> = submittedEntries.toList()

    /** Returns history rows in newest-first navigation order: result, input, then the prior entry. */
    fun historyLineFromNewest(position: Int): HistoryLine? {
        if (position <= 0) return null
        val entryOffset = (position - 1) / 2
        val entryIndex = submittedEntries.lastIndex - entryOffset
        if (entryIndex < 0) return null

        val entry = submittedEntries[entryIndex]
        val isResult = position % 2 == 1
        return HistoryLine(entryIndex, isResult, if (isResult) entry.result else entry.input)
    }

    /** Adds recalled history without overwriting the token currently under the edit cursor. */
    fun appendRecalledHistory(text: String) {
        insertText(text)
    }

    fun appendDigit(digit: Char) {
        require(digit.isDigit()) { "Only digits can be added to calculator display memory." }
        if (currentEntry.length == MAX_CHARACTERS) return

        replaceTokenAtCursor(digit.toString())
    }

    /** Adds or replaces the pending arithmetic operation. */
    fun appendOperator(operator: Char) {
        require(operator in BINARY_OPERATORS) { "Unsupported calculator operator: $operator" }
        if (currentEntry.isEmpty()) {
            startWithPreviousAnswer(operator)
            return
        }
        if (!endsOperandBeforeCursor()) return
        replaceTokenAtCursor(operator.toString())
    }

    /** Adds a decimal point to the current number, supplying a leading zero when needed. */
    fun appendDecimalPoint() {
        val operand = currentEntry.substring(currentOperandStart(), cursor)
        if (operand.contains('.')) return

        val leadingZeroNeeded = canStartOperandAtCursor()
        replaceTokenAtCursor(if (leadingZeroNeeded) "0." else ".")
    }

    /** Toggles the sign of the number currently being entered. */
    fun toggleCurrentNumberSign() {
        val operandStart = currentOperandStart()
        currentEntry = if (currentEntry.length > operandStart && currentEntry[operandStart] == '-') {
            if (operandStart < cursor) cursor--
            currentEntry.removeRange(operandStart, operandStart + 1)
        } else if (currentEntry.length < MAX_CHARACTERS) {
            if (operandStart <= cursor) cursor++
            currentEntry.substring(0, operandStart) + '-' + currentEntry.substring(operandStart)
        } else {
            currentEntry
        }
        save()
    }

    /** Appends a square operation to the current operand, or applies it to Ans when blank. */
    fun squareCurrentOperand() {
        if (currentEntry.isEmpty()) startWithPreviousAnswer("^2") else appendPower("2")
    }

    /** Appends a reciprocal operation to the current operand, or applies it to Ans when blank. */
    fun reciprocalCurrentOperand() {
        if (currentEntry.isEmpty()) startWithPreviousAnswer("^-1") else appendPower("-1")
    }

    fun appendOpenParenthesis() {
        if (canStartOperandAtCursor()) {
            appendText("(")
        }
    }

    fun appendCloseParenthesis() {
        if (endsOperandBeforeCursor() && currentEntry.count { it == '(' } > currentEntry.count { it == ')' }) {
            appendText(")")
        }
    }

    /** Adds the calculator's X variable wherever a new operand may begin. */
    fun appendXVariable() {
        if (canStartOperandAtCursor()) appendText("X")
    }

    /** Adds a store operator after an expression; the destination is selected with the X key. */
    fun appendStoreOperator() {
        if (endsOperandBeforeCursor()) appendText(STORE_OPERATOR)
    }

    /** Adds a scientific function and its opening parenthesis when an operand may begin. */
    fun appendFunction(function: String) {
        require(function in FUNCTIONS) { "Unsupported calculator function: $function" }
        if (canStartOperandAtCursor()) {
            appendText("$function(")
        }
    }

    /**
     * Keeps comma entry visible for future multi-argument functions. It currently evaluates as an
     * error because no multi-argument functions have been implemented.
     */
    fun appendComma() {
        appendText(",")
    }

    /**
     * Clears the active expression. When it is already empty, hides all LCD history without
     * deleting it from the persistent memory file.
     */
    fun clearCurrent() {
        if (currentEntry.isNotEmpty()) {
            currentEntry = ""
            cursor = 0
            save()
        } else {
            firstVisibleSubmittedIndex = submittedEntries.size
        }
    }

    /** Evaluates the entered expression and adds both the input and answer to display history. */
    fun submit() {
        if (currentEntry.isEmpty()) return

        if (currentEntry.contains(',')) {
            submittedEntries += SubmittedEntry(currentEntry, SYNTAX_ERROR)
            currentEntry = ""
            cursor = 0
            save()
            return
        }

        if (currentEntry.contains(STORE_OPERATOR)) {
            val result = evaluateAssignment(currentEntry)
            submittedEntries += SubmittedEntry(currentEntry, result.display, result.value, result.imaginaryValue)
            currentEntry = ""
            cursor = 0
            save()
            return
        }

        val completedEntry = completeFunctionParentheses(currentEntry)
        val result = if (!endsOperand(completedEntry) || completedEntry.count { it == '(' } != completedEntry.count { it == ')' }) {
            EvaluationResult(SYNTAX_ERROR)
        } else {
            evaluate(completedEntry)
        }
        submittedEntries += SubmittedEntry(currentEntry, result.display, result.value, result.imaginaryValue)
        currentEntry = ""
        cursor = 0
        save()
    }

    /** Moves over a whole function token (for example, `log(`) instead of its individual letters. */
    fun moveCursorLeft() {
        if (cursor == 0) return
        cursor = functionTokenEndingAt(cursor)?.let { cursor - it.length } ?: cursor - 1
    }

    /** Moves over a whole function token (for example, `log(`) instead of its individual letters. */
    fun moveCursorRight() {
        if (cursor == currentEntry.length) return
        cursor = functionTokenStartingAt(cursor)?.let { cursor + it.length } ?: cursor + 1
    }

    /** Deletes the token beneath the cursor; this is a forward-delete key, not backspace. */
    fun deleteAtCursor() {
        if (cursor == currentEntry.length) return
        val token = functionTokenStartingAt(cursor)
        val length = token?.length ?: 1
        currentEntry = currentEntry.removeRange(cursor, cursor + length)
        save()
    }

    /** Hides the oldest submitted expression when the display needs another row. */
    fun discardOldestSubmitted() {
        if (firstVisibleSubmittedIndex < submittedEntries.size) {
            firstVisibleSubmittedIndex++
        }
    }

    /** Evaluates a saved Y= expression at the supplied X value for graph rendering. */
    fun evaluateForGraph(expression: String, graphX: Double): Double? = runCatching {
        ExpressionParser(
            completeFunctionParentheses(expression),
            latestAnswer(),
            BigDecimal.valueOf(graphX)
        ).parse().toDouble()
    }.getOrNull()?.takeIf(Double::isFinite)

    private fun evaluate(expression: String): EvaluationResult {
        val realEvaluation = runCatching { evaluateValue(expression) }
        realEvaluation.getOrNull()?.let { value -> return EvaluationResult(format(value), value) }

        if (ModeSettingsMemory.usesRectangularComplexFormat()) {
            val complexEvaluation = runCatching {
                ComplexExpressionEvaluator(
                    expression,
                    latestComplexAnswer(),
                    xValue.toDouble(),
                    ModeSettingsMemory.usesDegrees()
                ).parse()
            }
            complexEvaluation.getOrNull()?.let(::complexEvaluationResult)?.let { return it }
            (complexEvaluation.exceptionOrNull() as? ComplexEvaluationException)?.message?.let {
                return EvaluationResult(it)
            }
        }

        val realException = realEvaluation.exceptionOrNull()
        return if (realException is CalculatorEvaluationException) {
            EvaluationResult(realException.message ?: SYNTAX_ERROR)
        } else {
            EvaluationResult(SYNTAX_ERROR)
        }
    }

    private fun evaluateAssignment(expression: String): EvaluationResult = try {
        val storeIndex = expression.indexOf(STORE_OPERATOR)
        check(storeIndex > 0 && expression.indexOf(STORE_OPERATOR, storeIndex + STORE_OPERATOR.length) == -1) {
            "Only one store operation is allowed"
        }
        check(expression.substring(storeIndex + STORE_OPERATOR.length) == "X") {
            "Only X can be assigned"
        }

        val value = evaluateValue(expression.substring(0, storeIndex))
        xValue = value
        EvaluationResult(format(value), value)
    } catch (exception: CalculatorEvaluationException) {
        EvaluationResult(exception.message ?: SYNTAX_ERROR)
    } catch (_: Exception) {
        EvaluationResult(SYNTAX_ERROR)
    }

    private fun evaluateValue(expression: String): BigDecimal =
        ExpressionParser(completeFunctionParentheses(expression), latestAnswer(), xValue).parse()

    private fun format(value: BigDecimal): String = ModeSettingsMemory.formatNumber(value)

    private fun complexEvaluationResult(value: ComplexNumber): EvaluationResult {
        val normalizedReal = normalizeComplexPart(value.real)
        val normalizedImaginary = normalizeComplexPart(value.imaginary)
        if (normalizedImaginary == 0.0) {
            val realValue = BigDecimal.valueOf(normalizedReal)
            return EvaluationResult(format(realValue), realValue)
        }

        val realValue = BigDecimal.valueOf(normalizedReal)
        val imaginaryValue = BigDecimal.valueOf(normalizedImaginary)
        return EvaluationResult(
            formatRectangularComplex(normalizedReal, normalizedImaginary),
            realValue,
            imaginaryValue
        )
    }

    private fun formatRectangularComplex(real: Double, imaginary: Double): String {
        val realText = if (real == 0.0) "" else ModeSettingsMemory.formatNumber(real)
        val imaginaryMagnitude = kotlin.math.abs(imaginary)
        val imaginaryCoefficient = if (imaginaryMagnitude == 1.0) {
            ""
        } else {
            ModeSettingsMemory.formatNumber(imaginaryMagnitude)
        }
        val imaginaryText = "$imaginaryCoefficient${COMPLEX_UNIT}"
        return when {
            real == 0.0 && imaginary < 0.0 -> "-$imaginaryText"
            real == 0.0 -> imaginaryText
            imaginary < 0.0 -> "$realText-$imaginaryText"
            else -> "$realText+$imaginaryText"
        }
    }

    private fun normalizeComplexPart(value: Double): Double = when {
        kotlin.math.abs(value) < COMPLEX_ZERO_EPSILON -> 0.0
        kotlin.math.abs(value - 1.0) < COMPLEX_ZERO_EPSILON -> 1.0
        kotlin.math.abs(value + 1.0) < COMPLEX_ZERO_EPSILON -> -1.0
        else -> value
    }

    /** Starts a blank expression with the prior answer and waits for the user's next operand. */
    private fun startWithPreviousAnswer(operation: Char) {
        if (hasLatestAnswer()) {
            val expression = "Ans$operation"
            if (expression.length <= MAX_CHARACTERS) {
                currentEntry = expression
                cursor = currentEntry.length
                save()
            }
        }
    }

    /** Starts a blank expression with a unary operation applied to the prior answer. */
    private fun startWithPreviousAnswer(operation: String) {
        if (hasLatestAnswer()) {
            val expression = "Ans$operation"
            if (expression.length <= MAX_CHARACTERS) {
                currentEntry = expression
                cursor = currentEntry.length
                save()
            }
        }
    }

    private fun latestAnswer(): BigDecimal? {
        val entry = latestValidEntry() ?: return null
        if (entry.rawImaginaryResult != null) return null
        return entry.rawResult ?: runCatching { BigDecimal(entry.result) }.getOrNull()
    }

    private fun latestComplexAnswer(): ComplexNumber? {
        val entry = latestValidEntry() ?: return null
        val real = entry.rawResult ?: runCatching { BigDecimal(entry.result) }.getOrNull() ?: return null
        return ComplexNumber(real.toDouble(), entry.rawImaginaryResult?.toDouble() ?: 0.0)
    }

    private fun latestValidEntry(): SubmittedEntry? = submittedEntries.asReversed().firstOrNull { entry ->
        entry.rawResult != null || runCatching { BigDecimal(entry.result) }.isSuccess
    }

    private fun hasLatestAnswer(): Boolean = latestValidEntry() != null

    private fun appendPower(exponent: String) {
        if (endsOperandBeforeCursor()) appendText("^$exponent")
    }

    private fun appendText(text: String) {
        replaceTokenAtCursor(text)
    }

    private fun insertText(text: String) {
        if (currentEntry.length + text.length > MAX_CHARACTERS) return
        currentEntry = currentEntry.substring(0, cursor) + text + currentEntry.substring(cursor)
        cursor += text.length
        save()
    }

    /**
     * Calculator entry overwrites the token under the cursor. Functions and their opening
     * parenthesis are one logical token even though the cursor is drawn over only the first letter.
     */
    private fun replaceTokenAtCursor(text: String) {
        val replacedLength = cursorTokenLength()
        val newLength = currentEntry.length - replacedLength + text.length
        if (newLength > MAX_CHARACTERS) return

        currentEntry = currentEntry.removeRange(cursor, cursor + replacedLength)
        currentEntry = currentEntry.substring(0, cursor) + text + currentEntry.substring(cursor)
        cursor += text.length
        save()
    }

    private fun canStartOperandAtCursor(): Boolean =
        cursor == 0 || currentEntry[cursor - 1] in BINARY_OPERATORS ||
            currentEntry[cursor - 1] == '(' || currentEntry.substring(0, cursor).endsWith(STORE_OPERATOR)

    private fun endsOperandBeforeCursor(): Boolean =
        cursor > 0 && currentEntry[cursor - 1].let { it.isDigit() || it == ')' || it == 'X' || it == COMPLEX_UNIT }

    private fun endsOperand(): Boolean = endsOperand(currentEntry)

    private fun endsOperand(expression: String): Boolean =
        expression.lastOrNull()?.let { it.isDigit() || it == ')' || it == 'X' || it == COMPLEX_UNIT } == true

    /**
     * Lets function entry omit only its final closing parentheses: `sin(X` becomes `sin(X)`.
     * Ordinary grouping parentheses are deliberately left incomplete so malformed expressions still
     * report a syntax error.
     */
    private fun completeFunctionParentheses(expression: String): String {
        val unclosedParentheses = mutableListOf<Boolean>()
        expression.forEachIndexed { index, character ->
            when (character) {
                '(' -> unclosedParentheses += FUNCTIONS.any { function ->
                    index >= function.length && expression.regionMatches(
                        index - function.length,
                        function,
                        0,
                        function.length
                    )
                }
                ')' -> if (unclosedParentheses.isNotEmpty()) unclosedParentheses.removeLast()
            }
        }

        return if (unclosedParentheses.isNotEmpty() && unclosedParentheses.all { it }) {
            expression + ")".repeat(unclosedParentheses.size)
        } else {
            expression
        }
    }

    private fun currentOperandStart(): Int {
        for (index in cursor - 1 downTo 0) {
            when (currentEntry[index]) {
                '+', '*', '/', '^', '(' -> return index + 1
                '-' -> if (index > 0 && (currentEntry[index - 1].isDigit() || currentEntry[index - 1] == ')')) {
                    return index + 1
                }
            }
        }
        return 0
    }

    private fun functionTokenStartingAt(position: Int): String? =
        FUNCTIONS.firstOrNull { currentEntry.startsWith("$it(", position) }?.plus("(")

    private fun functionTokenEndingAt(position: Int): String? =
        FUNCTIONS.firstOrNull { function ->
            position >= function.length + 1 && currentEntry.regionMatches(position - function.length - 1, "$function(", 0, function.length + 1)
        }?.plus("(")

    private fun load() {
        if (!Files.exists(memoryFile)) return

        runCatching {
            val savedLines = Files.readAllLines(memoryFile, StandardCharsets.UTF_8)
            if (savedLines.firstOrNull()?.startsWith(CURRENT_PREFIX) == true) {
                currentEntry = savedLines.first().removePrefix(CURRENT_PREFIX).take(MAX_CHARACTERS)
                savedLines.firstOrNull { it.startsWith(X_PREFIX) }?.removePrefix(X_PREFIX)?.let { savedX ->
                    xValue = runCatching { BigDecimal(savedX) }.getOrDefault(BigDecimal.ZERO)
                }
                submittedEntries += savedLines.drop(1).mapNotNull { line ->
                    line.removePrefix(ENTRY_PREFIX).split('\t', limit = 4).takeIf { line.startsWith(ENTRY_PREFIX) }
                        ?.let { parts ->
                            parts.getOrNull(0)?.let { input ->
                                parts.getOrNull(1)?.let { result ->
                                    SubmittedEntry(
                                        input,
                                        result,
                                        parts.getOrNull(2)?.toBigDecimalOrNull(),
                                        parts.getOrNull(3)?.toBigDecimalOrNull()
                                    )
                                }
                            }
                        }
                }
            } else {
                // Read the number-only format used before arithmetic support was added.
                currentEntry = savedLines.firstOrNull()?.take(MAX_CHARACTERS)?.filter(Char::isDigit).orEmpty()
                submittedEntries += savedLines.drop(1)
                    .map { it.take(MAX_CHARACTERS).filter(Char::isDigit) }
                    .filter(String::isNotEmpty)
                    .map { SubmittedEntry(it, it) }
            }
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(memoryFile.parent)
            Files.write(
                memoryFile,
                listOf(CURRENT_PREFIX + currentEntry, X_PREFIX + xValue.toPlainString()) + submittedEntries.map {
                    ENTRY_PREFIX + it.input + '\t' + it.result + '\t' +
                        (it.rawResult?.toPlainString() ?: "") + '\t' +
                        (it.rawImaginaryResult?.toPlainString() ?: "")
                },
                StandardCharsets.UTF_8
            )
        }
    }

    private const val CURRENT_PREFIX = "current\t"
    private const val X_PREFIX = "x\t"
    private const val ENTRY_PREFIX = "entry\t"
    private const val SYNTAX_ERROR = "Error: Syntax"
    private const val DIVISION_BY_ZERO_ERROR = "Error: Division by zero"
    private const val BINARY_OPERATORS = "+-*/^"
    private const val STORE_OPERATOR = "->"
    private const val COMPLEX_UNIT = 'i'
    private const val COMPLEX_ZERO_EPSILON = 1.0e-12
    private val FUNCTIONS = setOf("sin", "cos", "tan", "log", "ln")

    private class CalculatorEvaluationException(message: String) : IllegalArgumentException(message)

    /** Recursive-descent evaluator with standard arithmetic precedence and parenthesis support. */
    private class ExpressionParser(
        private val expression: String,
        private val previousAnswer: BigDecimal?,
        private val xValue: BigDecimal
    ) {
        private var index = 0

        fun parse(): BigDecimal {
            val result = parseSum()
            check(index == expression.length) { "Unexpected expression content" }
            return result
        }

        private fun parseSum(): BigDecimal {
            var result = parseProduct()
            while (index < expression.length && expression[index] in "+-") {
                result = if (expression[index++] == '+') result + parseProduct() else result - parseProduct()
            }
            return result
        }

        private fun parseProduct(): BigDecimal {
            var result = parsePower()
            while (index < expression.length) {
                result = when {
                    expression[index] == '*' -> {
                        index++
                        result * parsePower()
                    }
                    expression[index] == '/' -> {
                        index++
                        val divisor = parsePower()
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                            throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                        }
                        result.divide(divisor, 10, RoundingMode.HALF_UP)
                    }
                    startsPrimaryAt(index) -> result * parsePower()
                    else -> return result
                }
            }
            return result
        }

        /** Supports calculator notation such as 8X, 2(X+1), and 3sin(X). */
        private fun startsPrimaryAt(position: Int): Boolean =
            position < expression.length && (
                expression[position].isDigit() || expression[position] == '.' ||
                    expression[position] == '(' || expression[position] == 'X' ||
                    expression.startsWith("Ans", position) ||
                    FUNCTIONS.any { expression.startsWith(it, position) }
                )

        private fun parsePower(): BigDecimal {
            var result = parseUnary()
            if (index < expression.length && expression[index] == '^') {
                index++
                val exponent = parsePower()
                if (result.compareTo(BigDecimal.ZERO) == 0 && exponent.compareTo(BigDecimal.ZERO) < 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                result = if (exponent.scale() <= 0) {
                    val integerExponent = exponent.intValueExact()
                    if (integerExponent >= 0) result.pow(integerExponent) else {
                        BigDecimal.ONE.divide(result.pow(-integerExponent), 10, RoundingMode.HALF_UP)
                    }
                } else {
                    BigDecimal.valueOf(Math.pow(result.toDouble(), exponent.toDouble()))
                }
            }
            return result
        }

        private fun parseUnary(): BigDecimal =
            if (index < expression.length && expression[index] == '-') {
                index++
                parseUnary().negate()
            } else {
                parsePrimary()
            }

        private fun parsePrimary(): BigDecimal {
            if (expression.startsWith("Ans", index)) {
                index += "Ans".length
                return checkNotNull(previousAnswer) { "No previous answer is available" }
            }

            if (index < expression.length && expression[index] == 'X') {
                index++
                return xValue
            }

            FUNCTIONS.firstOrNull { expression.startsWith(it, index) }?.let { function ->
                index += function.length
                check(index < expression.length && expression[index] == '(') {
                    "Expected opening parenthesis after $function"
                }
                index++
                val argument = parseSum()
                check(index < expression.length && expression[index] == ')') {
                    "Missing closing parenthesis"
                }
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
            return BigDecimal(expression.substring(start, index))
        }

        private fun evaluateFunction(function: String, argument: BigDecimal): BigDecimal {
            val value = argument.toDouble()
            val angle = if (ModeSettingsMemory.usesDegrees()) Math.toRadians(value) else value
            val result = when (function) {
                "sin" -> Math.sin(angle)
                "cos" -> Math.cos(angle)
                "tan" -> Math.tan(angle)
                "log" -> Math.log10(value)
                "ln" -> Math.log(value)
                else -> error("Unsupported calculator function: $function")
            }
            check(result.isFinite()) { "Function result is outside the supported range" }
            return BigDecimal.valueOf(result)
        }
    }
}
