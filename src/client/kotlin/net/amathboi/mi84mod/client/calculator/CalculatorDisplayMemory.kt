package net.amathboi.mi84mod.client.calculator

import net.fabricmc.loader.api.FabricLoader
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
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
    // Index between expression tokens. Multi-character entry tokens are traversed as one token.
    private var cursor = 0
    // X mirrors the calculator's default graphing variable and is retained in the memory file.
    private var xValue = BigDecimal.ZERO
    // Earlier entries remain in the memory file but are no longer shown on the LCD.
    private var firstVisibleSubmittedIndex = 0

    init {
        load()
        CalculatorVariableMemory.initializeLegacyX(xValue)
        cursor = currentEntry.length
    }

    fun current(): String = currentEntry

    fun cursorPosition(): Int = cursor

    /** Width of the token under the cursor, used to draw the block cursor over it. */
    fun cursorTokenLength(): Int =
        entryTokenStartingAt(cursor)?.length ?: if (cursor < currentEntry.length) 1 else 0

    fun submitted(): List<SubmittedEntry> = submittedEntries.drop(firstVisibleSubmittedIndex)

    /** All saved entries, including rows currently hidden because the LCD is full. */
    fun allSubmitted(): List<SubmittedEntry> = submittedEntries.toList()

    fun submittedInputFromNewest(position: Int): String? =
        submittedEntries.getOrNull(submittedEntries.size - position)?.input

    /** ENTRY replaces the active edit line with one previously submitted input. */
    fun replaceCurrentWithSubmittedInput(text: String) {
        currentEntry = text.take(MAX_CHARACTERS)
        cursor = currentEntry.length
        save()
    }

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

    fun appendDigit(digit: Char, insertMode: Boolean = false) {
        require(digit.isDigit()) { "Only digits can be added to calculator display memory." }
        if (currentEntry.length == MAX_CHARACTERS) return

        appendText(digit.toString(), insertMode)
    }

    /** Adds or replaces the pending arithmetic operation. */
    fun appendOperator(operator: Char, insertMode: Boolean = false) {
        require(operator in BINARY_OPERATORS) { "Unsupported calculator operator: $operator" }
        if (currentEntry.isEmpty()) {
            startWithPreviousAnswer(operator)
            return
        }
        if (!endsOperandBeforeCursor()) return
        appendText(operator.toString(), insertMode)
    }

    /**
     * Adds a decimal point, supplying a leading zero when needed. A second decimal remains visible
     * and is reported as a syntax error on submission instead of being silently ignored.
     */
    fun appendDecimalPoint(insertMode: Boolean = false) {
        val leadingZeroNeeded = canStartOperandAtCursor()
        appendText(if (leadingZeroNeeded) "0." else ".", insertMode)
    }

    /** Toggles the sign of the number currently being entered. */
    fun toggleCurrentNumberSign() {
        scientificExponentSignIndex()?.let { signIndex ->
            currentEntry = if (currentEntry.getOrNull(signIndex) == '-') {
                if (signIndex < cursor) cursor--
                currentEntry.removeRange(signIndex, signIndex + 1)
            } else if (currentEntry.length < MAX_CHARACTERS) {
                if (signIndex <= cursor) cursor++
                currentEntry.substring(0, signIndex) + '-' + currentEntry.substring(signIndex)
            } else {
                currentEntry
            }
            save()
            return
        }

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
    fun squareCurrentOperand(insertMode: Boolean = false) {
        if (currentEntry.isEmpty()) startWithPreviousAnswer("^2") else appendPower("2", insertMode)
    }

    /** Appends a reciprocal operation to the current operand, or applies it to Ans when blank. */
    fun reciprocalCurrentOperand(insertMode: Boolean = false) {
        if (currentEntry.isEmpty()) startWithPreviousAnswer("^-1") else appendPower("-1", insertMode)
    }

    fun appendOpenParenthesis(insertMode: Boolean = false) {
        if (canInsertPrimaryAtCursor()) {
            appendText("(", insertMode)
        }
    }

    fun appendCloseParenthesis(insertMode: Boolean = false) {
        if (endsOperandBeforeCursor() && currentEntry.count { it == '(' } > currentEntry.count { it == ')' }) {
            appendText(")", insertMode)
        }
    }

    /** Adds the calculator's X variable wherever a new operand may begin. */
    fun appendXVariable(insertMode: Boolean = false) {
        appendVariable(CalculatorVariable.X, insertMode)
    }

    fun appendVariable(variable: CalculatorVariable, insertMode: Boolean = false) {
        if (canInsertPrimaryAtCursor()) appendText(variable.symbol.toString(), insertMode)
    }

    /** Adds a store operator after an expression; the destination is selected with the X key. */
    fun appendStoreOperator(insertMode: Boolean = false) {
        if (endsOperandBeforeCursor()) appendText(STORE_OPERATOR, insertMode)
    }

    /** Adds a scientific function and its opening parenthesis when an operand may begin. */
    fun appendFunction(function: String, insertMode: Boolean = false) {
        require(function in FUNCTIONS) { "Unsupported calculator function: $function" }
        if (canInsertPrimaryAtCursor()) {
            appendText("$function(", insertMode)
        }
    }

    fun appendAns(insertMode: Boolean = false) = appendPrimaryToken(ANSWER_TOKEN, insertMode)

    fun appendImaginaryUnit(insertMode: Boolean = false) =
        appendPrimaryToken(COMPLEX_UNIT.toString(), insertMode)

    fun appendPi(insertMode: Boolean = false) = appendPrimaryToken(PI_TOKEN.toString(), insertMode)

    fun appendEuler(insertMode: Boolean = false) =
        appendPrimaryToken(EULER_TOKEN.toString(), insertMode)

    fun appendInverseSine(insertMode: Boolean = false) = appendFunction(INVERSE_SINE, insertMode)

    fun appendInverseCosine(insertMode: Boolean = false) = appendFunction(INVERSE_COSINE, insertMode)

    fun appendInverseTangent(insertMode: Boolean = false) =
        appendFunction(INVERSE_TANGENT, insertMode)

    fun appendSquareRoot(insertMode: Boolean = false) = appendFunction(SQUARE_ROOT, insertMode)

    fun appendTenPower(insertMode: Boolean = false) = appendPowerTemplate("10", insertMode)

    fun appendEulerPower(insertMode: Boolean = false) =
        appendPowerTemplate(EULER_TOKEN.toString(), insertMode)

    /**
     * Adds the calculator EE marker only after a complete decimal mantissa. The exponent itself is
     * entered with digits and may be negated with the (−) key.
     */
    fun appendScientificExponent(insertMode: Boolean = false) {
        val operand = currentEntry.substring(currentOperandStart(), cursor).removePrefix("-")
        if (operand.isNotEmpty() &&
            operand.last().isDigit() &&
            SCIENTIFIC_EXPONENT_TOKEN !in operand &&
            operand.toBigDecimalOrNull() != null
        ) {
            appendText(SCIENTIFIC_EXPONENT_TOKEN, insertMode)
        }
    }

    /**
     * Keeps comma entry visible for future multi-argument functions. It currently evaluates as an
     * error because no multi-argument functions have been implemented.
     */
    fun appendComma(insertMode: Boolean = false) {
        appendText(",", insertMode)
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
            recordSubmission(SubmittedEntry(currentEntry, SYNTAX_ERROR))
            currentEntry = ""
            cursor = 0
            save()
            return
        }

        if (currentEntry.contains(STORE_OPERATOR)) {
            val result = evaluateAssignment(currentEntry)
            recordSubmission(SubmittedEntry(currentEntry, result.display, result.value, result.imaginaryValue))
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
        recordSubmission(SubmittedEntry(currentEntry, result.display, result.value, result.imaginaryValue))
        currentEntry = ""
        cursor = 0
        save()
    }

    /** Moves over a whole multi-character entry token instead of its individual characters. */
    fun moveCursorLeft() {
        if (cursor == 0) return
        cursor = entryTokenEndingAt(cursor)?.let { cursor - it.length } ?: cursor - 1
    }

    /** Moves over a whole multi-character entry token instead of its individual characters. */
    fun moveCursorRight() {
        if (cursor == currentEntry.length) return
        cursor = entryTokenStartingAt(cursor)?.let { cursor + it.length } ?: cursor + 1
    }

    /** Deletes the token beneath the cursor; this is a forward-delete key, not backspace. */
    fun deleteAtCursor() {
        if (cursor == currentEntry.length) return
        val token = entryTokenStartingAt(cursor)
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
            realVariableValues(BigDecimal.valueOf(graphX))
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
                    complexVariableValues(),
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
        val destination = expression.substring(storeIndex + STORE_OPERATOR.length)
            .singleOrNull()
            ?.let(CalculatorVariable::fromSymbol)
            ?: error("A scalar variable destination is required")
        val result = evaluate(completeFunctionParentheses(expression.substring(0, storeIndex)))
        val realValue = result.value ?: return EvaluationResult(result.display)
        CalculatorVariableMemory.set(destination, realValue, result.imaginaryValue)
        if (destination == CalculatorVariable.X) xValue = realValue
        result
    } catch (exception: CalculatorEvaluationException) {
        EvaluationResult(exception.message ?: SYNTAX_ERROR)
    } catch (_: Exception) {
        EvaluationResult(SYNTAX_ERROR)
    }

    private fun evaluateValue(expression: String): BigDecimal =
        ExpressionParser(
            completeFunctionParentheses(expression),
            latestAnswer(),
            realVariableValues()
        ).parse()

    private fun realVariableValues(xOverride: BigDecimal? = null): Map<CalculatorVariable, BigDecimal?> =
        CalculatorVariable.entries.associateWith { variable ->
            if (variable == CalculatorVariable.X && xOverride != null) {
                xOverride
            } else {
                CalculatorVariableMemory.value(variable).let { value ->
                    value.real.takeIf { value.imaginary == null }
                }
            }
        }

    private fun complexVariableValues(): Map<CalculatorVariable, ComplexNumber> =
        CalculatorVariable.entries.associateWith { variable ->
            CalculatorVariableMemory.value(variable).let { value ->
                ComplexNumber(value.real.toDouble(), value.imaginary?.toDouble() ?: 0.0)
            }
        }

    private fun format(value: BigDecimal): String = ModeSettingsMemory.formatNumber(value)

    private fun complexEvaluationResult(value: ComplexNumber): EvaluationResult {
        val rawRealValue = BigDecimal.valueOf(value.real)
        if (value.imaginary == 0.0) {
            val realValue = BigDecimal.valueOf(normalizeComplexUnit(value.real))
            return EvaluationResult(format(realValue), realValue)
        }

        val (displayReal, displayImaginary) = normalizeComplexDisplayParts(value)
        if (displayImaginary == 0.0) {
            return EvaluationResult(
                format(BigDecimal.valueOf(displayReal)),
                rawRealValue
            )
        }
        return EvaluationResult(
            formatRectangularComplex(displayReal, displayImaginary),
            rawRealValue.takeUnless { displayReal == 0.0 } ?: BigDecimal.ZERO,
            BigDecimal.valueOf(value.imaginary)
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
            imaginary == 0.0 -> realText.ifEmpty { "0" }
            real == 0.0 && imaginary < 0.0 -> "-$imaginaryText"
            real == 0.0 -> imaginaryText
            imaginary < 0.0 -> "$realText-$imaginaryText"
            else -> "$realText+$imaginaryText"
        }
    }

    /**
     * Removes only a component that is tiny relative to the other component. A small standalone
     * value survives, while numerical residue beside a much larger component is stored as zero.
     */
    private fun normalizeComplexDisplayParts(value: ComplexNumber): Pair<Double, Double> {
        var real = normalizeComplexUnit(value.real)
        var imaginary = normalizeComplexUnit(value.imaginary)
        if (real != 0.0 && imaginary != 0.0) {
            if (kotlin.math.abs(real) < kotlin.math.abs(imaginary) * COMPLEX_ZERO_EPSILON) real = 0.0
            if (kotlin.math.abs(imaginary) < kotlin.math.abs(real) * COMPLEX_ZERO_EPSILON) imaginary = 0.0
        }
        return real to imaginary
    }

    private fun normalizeComplexUnit(value: Double): Double = when {
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

    private fun recordSubmission(entry: SubmittedEntry) {
        while (submittedEntries.size >= MAX_HISTORY_ENTRIES) {
            submittedEntries.removeAt(0)
            firstVisibleSubmittedIndex = (firstVisibleSubmittedIndex - 1).coerceAtLeast(0)
        }
        submittedEntries += entry
    }

    private fun appendPower(exponent: String, insertMode: Boolean) {
        if (endsOperandBeforeCursor()) appendText("^$exponent", insertMode)
    }

    private fun appendPrimaryToken(token: String, insertMode: Boolean) {
        if (canInsertPrimaryAtCursor()) appendText(token, insertMode)
    }

    private fun appendPowerTemplate(base: String, insertMode: Boolean) {
        if (canInsertPrimaryAtCursor()) appendText("$base^(", insertMode)
    }

    private fun appendText(text: String, insertMode: Boolean = false) {
        if (insertMode) insertText(text) else replaceTokenAtCursor(text)
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

    /** Primary tokens may also follow an operand directly because the parser supports implicit multiplication. */
    private fun canInsertPrimaryAtCursor(): Boolean =
        canStartOperandAtCursor() || endsOperandBeforeCursor()

    private fun endsOperandBeforeCursor(): Boolean =
        cursor > 0 && currentEntry[cursor - 1].let {
            it.isDigit() || it == ')' || isVariableSymbol(it) || it == COMPLEX_UNIT ||
                it == PI_TOKEN || it == EULER_TOKEN ||
                currentEntry.substring(0, cursor).endsWith(ANSWER_TOKEN)
        }

    private fun endsOperand(): Boolean = endsOperand(currentEntry)

    private fun endsOperand(expression: String): Boolean =
        expression.lastOrNull()?.let {
            it.isDigit() || it == ')' || isVariableSymbol(it) || it == COMPLEX_UNIT ||
                it == PI_TOKEN || it == EULER_TOKEN || expression.endsWith(ANSWER_TOKEN)
        } == true

    /**
     * Lets function entry omit only its final closing parentheses: `sin(X` becomes `sin(X)`.
     * Ordinary grouping parentheses are deliberately left incomplete so malformed expressions still
     * report a syntax error.
     */
    private fun completeFunctionParentheses(expression: String): String {
        val unclosedParentheses = mutableListOf<Boolean>()
        expression.forEachIndexed { index, character ->
            when (character) {
                '(' -> unclosedParentheses += AUTO_CLOSING_PREFIXES.any { prefix ->
                    index >= prefix.length && expression.regionMatches(
                        index - prefix.length,
                        prefix,
                        0,
                        prefix.length
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
                '-' -> if (index > 0 && endsOperandAt(index)) {
                    return index + 1
                }
            }
        }
        return 0
    }

    private fun endsOperandAt(endExclusive: Int): Boolean {
        val previous = currentEntry.getOrNull(endExclusive - 1) ?: return false
        return previous.isDigit() || previous == '.' || previous == ')' || isVariableSymbol(previous) ||
            previous == COMPLEX_UNIT || previous == PI_TOKEN || previous == EULER_TOKEN ||
            currentEntry.substring(0, endExclusive).endsWith(ANSWER_TOKEN)
    }

    private fun isVariableSymbol(character: Char): Boolean =
        CalculatorVariable.fromSymbol(character) != null

    private fun scientificExponentSignIndex(): Int? {
        val markerIndex = currentEntry.lastIndexOf(
            SCIENTIFIC_EXPONENT_TOKEN,
            startIndex = (cursor - 1).coerceAtLeast(0)
        )
        if (markerIndex < currentOperandStart()) return null

        val signIndex = markerIndex + SCIENTIFIC_EXPONENT_TOKEN.length
        if (cursor < signIndex) return null
        val exponentPrefix = currentEntry.substring(signIndex, cursor)
        val digits = exponentPrefix.removePrefix("-")
        return signIndex.takeIf {
            (exponentPrefix.isEmpty() || exponentPrefix == "-" || digits.all(Char::isDigit)) &&
                !digits.contains(SCIENTIFIC_EXPONENT_TOKEN)
        }
    }

    private fun entryTokenStartingAt(position: Int): String? =
        ENTRY_TOKENS.firstOrNull { currentEntry.startsWith(it, position) }

    private fun entryTokenEndingAt(position: Int): String? =
        ENTRY_TOKENS.firstOrNull { token ->
            position >= token.length &&
                currentEntry.regionMatches(position - token.length, token, 0, token.length)
        }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
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
                }.takeLast(MAX_HISTORY_ENTRIES)
            } else {
                // Read the number-only format used before arithmetic support was added.
                currentEntry = savedLines.firstOrNull()?.take(MAX_CHARACTERS)?.filter(Char::isDigit).orEmpty()
                submittedEntries += savedLines.drop(1)
                    .map { it.take(MAX_CHARACTERS).filter(Char::isDigit) }
                    .filter(String::isNotEmpty)
                    .map { SubmittedEntry(it, it) }
                    .takeLast(MAX_HISTORY_ENTRIES)
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            listOf(CURRENT_PREFIX + currentEntry, X_PREFIX + xValue.toPlainString()) + submittedEntries.map {
                ENTRY_PREFIX + it.input + '\t' + it.result + '\t' +
                    (it.rawResult?.toPlainString() ?: "") + '\t' +
                    (it.rawImaginaryResult?.toPlainString() ?: "")
            }
        }
    }

    private const val CURRENT_PREFIX = "current\t"
    private const val X_PREFIX = "x\t"
    private const val ENTRY_PREFIX = "entry\t"
    private const val SYNTAX_ERROR = "Error: Syntax"
    private const val DIVISION_BY_ZERO_ERROR = "Error: Division by zero"
    private const val RESULT_TOO_LARGE_ERROR = "Error: Result too large"
    private const val BINARY_OPERATORS = "+-*/^"
    private const val STORE_OPERATOR = "->"
    private const val ANSWER_TOKEN = "Ans"
    private const val SCIENTIFIC_EXPONENT_TOKEN = "EE"
    private const val COMPLEX_UNIT = 'i'
    private const val PI_TOKEN = 'π'
    private const val EULER_TOKEN = 'e'
    private const val INVERSE_SINE = "sin⁻¹"
    private const val INVERSE_COSINE = "cos⁻¹"
    private const val INVERSE_TANGENT = "tan⁻¹"
    private const val SQUARE_ROOT = "sqrt"
    private const val COMPLEX_ZERO_EPSILON = 1.0e-12
    private const val MAX_POWER_RESULT_CHARACTERS = 4_096L
    private const val MAX_SCIENTIFIC_EXPONENT = 4_000
    private const val MAX_HISTORY_ENTRIES = 1_000
    private val FUNCTIONS = listOf(
        INVERSE_SINE,
        INVERSE_COSINE,
        INVERSE_TANGENT,
        SQUARE_ROOT,
        "sin",
        "cos",
        "tan",
        "log",
        "ln"
    )
    private val TRIG_FUNCTIONS = setOf("sin", "cos", "tan")
    private val AUTO_CLOSING_PREFIXES = FUNCTIONS + listOf("10^", "$EULER_TOKEN^")
    private val ENTRY_TOKENS =
        (FUNCTIONS.map { "$it(" } + listOf("10^(", "$EULER_TOKEN^(", ANSWER_TOKEN, SCIENTIFIC_EXPONENT_TOKEN))
            .sortedByDescending(String::length)
    private val QUARTER_TURN_DEGREES = BigDecimal("90")
    private val FOUR = BigDecimal("4")
    private val CALCULATION_CONTEXT = MathContext(34, RoundingMode.HALF_UP)

    private class CalculatorEvaluationException(message: String) : IllegalArgumentException(message)

    /** Recursive-descent evaluator with standard arithmetic precedence and parenthesis support. */
    private class ExpressionParser(
        private val expression: String,
        private val previousAnswer: BigDecimal?,
        private val variableValues: Map<CalculatorVariable, BigDecimal?>
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
                result = if (expression[index++] == '+') {
                    result.add(parseProduct(), CALCULATION_CONTEXT)
                } else {
                    result.subtract(parseProduct(), CALCULATION_CONTEXT)
                }
            }
            return result
        }

        private fun parseProduct(): BigDecimal {
            var result = parseUnary()
            while (index < expression.length) {
                result = when {
                    expression[index] == '*' -> {
                        index++
                        result.multiply(parseUnary(), CALCULATION_CONTEXT)
                    }
                    expression[index] == '/' -> {
                        index++
                        val divisor = parseUnary()
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                            throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                        }
                        result.divide(divisor, CALCULATION_CONTEXT)
                    }
                    startsPrimaryAt(index) -> result.multiply(parseUnary(), CALCULATION_CONTEXT)
                    else -> return result
                }
            }
            return result
        }

        /** Supports calculator notation such as 8X, 2(X+1), and 3sin(X). */
        private fun startsPrimaryAt(position: Int): Boolean =
            position < expression.length && (
                expression[position].isDigit() || expression[position] == '.' ||
                    expression[position] == '(' ||
                    CalculatorVariable.fromSymbol(expression[position]) != null ||
                    expression[position] == PI_TOKEN || expression[position] == EULER_TOKEN ||
                    expression.startsWith(ANSWER_TOKEN, position) ||
                    FUNCTIONS.any { expression.startsWith(it, position) }
                )

        private fun parsePower(): BigDecimal {
            val result = parsePrimary()
            return if (index < expression.length && expression[index] == '^') {
                index++
                val exponent = parseUnary()
                if (result.compareTo(BigDecimal.ZERO) == 0 && exponent.compareTo(BigDecimal.ZERO) < 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                if (exponent.stripTrailingZeros().scale() <= 0) {
                    integerPower(result, exponent)
                } else {
                    val powered = Math.pow(result.toDouble(), exponent.toDouble())
                    if (!powered.isFinite()) throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
                    BigDecimal.valueOf(powered)
                }
            } else {
                result
            }
        }

        private fun parseUnary(): BigDecimal =
            if (index < expression.length && expression[index] == '-') {
                index++
                parseUnary().negate(CALCULATION_CONTEXT)
            } else {
                parsePower()
            }

        private fun integerPower(base: BigDecimal, exponent: BigDecimal): BigDecimal {
            val integerExponent = try {
                exponent.intValueExact()
            } catch (_: ArithmeticException) {
                throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
            }
            if (base.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
            if (base.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ONE
            if (base.compareTo(BigDecimal.ONE.negate()) == 0) {
                return if (integerExponent % 2 == 0) BigDecimal.ONE else BigDecimal.ONE.negate()
            }

            val magnitude = kotlin.math.abs(integerExponent.toLong())
            val estimatedPrecision = base.precision().toLong() * magnitude
            val estimatedScale = base.scale().toLong() * magnitude
            val estimatedPlainCharacters = if (estimatedScale >= 0L) {
                maxOf(estimatedPrecision, estimatedScale) + 2L
            } else {
                estimatedPrecision - estimatedScale + 1L
            }
            if (estimatedPlainCharacters > MAX_POWER_RESULT_CHARACTERS) {
                throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
            }

            val powered = base.pow(magnitude.toInt(), CALCULATION_CONTEXT)
            return if (integerExponent >= 0) {
                powered
            } else {
                BigDecimal.ONE.divide(powered, CALCULATION_CONTEXT)
            }
        }

        private fun parsePrimary(): BigDecimal {
            if (expression.startsWith(ANSWER_TOKEN, index)) {
                index += ANSWER_TOKEN.length
                return checkNotNull(previousAnswer) { "No previous answer is available" }
            }

            if (index < expression.length) {
                CalculatorVariable.fromSymbol(expression[index])?.let { variable ->
                    index++
                    return checkNotNull(variableValues[variable]) {
                        "Complex variable cannot be evaluated as a real value"
                    }
                }
            }

            if (index < expression.length && expression[index] == PI_TOKEN) {
                index++
                return BigDecimal.valueOf(Math.PI)
            }

            if (index < expression.length && expression[index] == EULER_TOKEN) {
                index++
                return BigDecimal.valueOf(Math.E)
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

            return parseNumber()
        }

        private fun parseNumber(): BigDecimal {
            val mantissaStart = index
            while (index < expression.length && (expression[index].isDigit() || expression[index] == '.')) {
                index++
            }
            check(mantissaStart != index) { "Expected a number" }
            val mantissa = BigDecimal(expression.substring(mantissaStart, index))

            if (!expression.startsWith(SCIENTIFIC_EXPONENT_TOKEN, index)) return mantissa
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
                ?: throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
            val exponent = if (negative) -magnitude else magnitude
            if (exponent !in -MAX_SCIENTIFIC_EXPONENT..MAX_SCIENTIFIC_EXPONENT) {
                throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
            }
            return mantissa.scaleByPowerOfTen(exponent)
        }

        private fun evaluateFunction(function: String, argument: BigDecimal): BigDecimal {
            val value = argument.toDouble()
            val usesDegrees = ModeSettingsMemory.usesDegrees()
            val angle = if (usesDegrees) Math.toRadians(value) else value
            val result = when (function) {
                "sin" -> Math.sin(angle)
                "cos" -> Math.cos(angle)
                "tan" -> Math.tan(angle)
                "log" -> Math.log10(value)
                "ln" -> Math.log(value)
                INVERSE_SINE -> inverseAngle(Math.asin(value), usesDegrees)
                INVERSE_COSINE -> inverseAngle(Math.acos(value), usesDegrees)
                INVERSE_TANGENT -> inverseAngle(Math.atan(value), usesDegrees)
                SQUARE_ROOT -> Math.sqrt(value)
                else -> error("Unsupported calculator function: $function")
            }
            val normalizedResult = normalizeDegreeTrigIdentity(function, argument, result, usesDegrees)
            check(normalizedResult.isFinite()) { "Function result is outside the supported range" }
            return BigDecimal.valueOf(normalizedResult)
        }

        private fun inverseAngle(radians: Double, usesDegrees: Boolean): Double =
            if (usesDegrees) Math.toDegrees(radians) else radians

        private fun normalizeDegreeTrigIdentity(
            function: String,
            degrees: BigDecimal,
            result: Double,
            usesDegrees: Boolean
        ): Double {
            if (!usesDegrees || function !in TRIG_FUNCTIONS) return result
            val (quarterTurns, remainder) = degrees.divideAndRemainder(QUARTER_TURN_DEGREES)
            if (remainder.compareTo(BigDecimal.ZERO) != 0) return result
            val quadrant = Math.floorMod(quarterTurns.remainder(FOUR).toInt(), 4)

            return when (function) {
                "sin" -> when (quadrant) {
                    1 -> 1.0
                    3 -> -1.0
                    else -> 0.0
                }
                "cos" -> when (quadrant) {
                    0 -> 1.0
                    2 -> -1.0
                    else -> 0.0
                }
                "tan" -> if (quadrant % 2 == 0) 0.0 else result
                else -> result
            }
        }
    }
}
