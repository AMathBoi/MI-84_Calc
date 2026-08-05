package net.amathboi.mi84mod.client.calculator

import net.fabricmc.loader.api.FabricLoader
import java.math.BigDecimal
import java.math.BigInteger
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

    /** Restores fraction results as structured editor tokens instead of ordinary division text. */
    fun editableTextForHistoryLine(line: HistoryLine): String {
        val entry = submittedEntries.getOrNull(line.entryIndex) ?: return line.text
        if (!line.isResult) return entry.input
        if (!FRACTION_RESULT_PATTERN.matches(entry.result)) return entry.result
        val (numerator, denominator) = entry.result.split('/', limit = 2)
        return "$FRACTION_FUNCTION($numerator,$denominator)"
    }

    /** Adds recalled history without overwriting the token currently under the edit cursor. */
    fun appendRecalledHistory(text: String) {
        insertText(text)
    }

    fun appendDigit(digit: Char, insertMode: Boolean = false) {
        require(digit.isDigit()) { "Only digits can be added to calculator display memory." }
        appendText(
            ExpressionEditingTokens.digitEntryText(currentEntry, cursor, digit),
            insertMode
        )
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
        val edit =
            ExpressionEditingTokens.toggleOperandSign(currentEntry, cursor, MAX_CHARACTERS)
                ?: return
        currentEntry = edit.text
        cursor = edit.cursor
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

    /** Adds an argument separator for supported multi-argument functions. */
    fun appendComma(insertMode: Boolean = false) {
        appendText(",", insertMode)
    }

    /** Pastes an approved compact-menu token through the active Home editor mode. */
    fun appendMenuToken(token: String, insertMode: Boolean = false) {
        appendText(token, insertMode)
    }

    /** Inserts the Phase 5 list-literal opener through the shared expression editor. */
    fun appendListLiteralOpen(insertMode: Boolean = false) = appendPrimaryToken("{", insertMode)

    fun appendListLiteralClose(insertMode: Boolean = false) {
        if (endsOperandBeforeCursor()) appendText("}", insertMode)
    }

    fun appendListName(name: CalculatorListName, insertMode: Boolean = false) =
        appendPrimaryToken(name.token, insertMode)

    fun replaceStructuredFraction(start: Int, original: String, replacement: String): Boolean {
        if (start !in 0..currentEntry.length ||
            !currentEntry.regionMatches(start, original, 0, original.length) ||
            currentEntry.length - original.length + replacement.length > MAX_CHARACTERS
        ) {
            return false
        }
        currentEntry =
            currentEntry.substring(0, start) + replacement +
                currentEntry.substring(start + original.length)
        cursor = start + replacement.length
        save()
        return true
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

        if (currentEntry.contains(STORE_OPERATOR)) {
            val result = evaluateAssignment(currentEntry)
            recordSubmission(SubmittedEntry(currentEntry, result.display, result.value, result.imaginaryValue))
            currentEntry = ""
            cursor = 0
            save()
            return
        }

        val completedEntry = completeFunctionParentheses(currentEntry)
        val listLiteral = completedEntry.startsWith('{') && completedEntry.endsWith('}')
        val result = if ((!listLiteral && !endsOperand(completedEntry)) ||
            completedEntry.count { it == '(' } != completedEntry.count { it == ')' }
        ) {
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
    fun evaluateForGraph(expression: String, graphX: Double): Double? {
        val expandedExpression = expandNamedVariables(expression) ?: return null
        return runCatching {
            val value = ExpressionParser(
                completeFunctionParentheses(expandedExpression),
                latestAnswer(),
                realVariableValues(BigDecimal.valueOf(graphX))
            ).parse()
            requireSupportedDouble(value)
        }.getOrNull()
    }

    /** Evaluates a real scalar list-cell expression without rounding the stored value. */
    fun evaluateRealForListEntry(expression: String): BigDecimal? {
        if (expression.isBlank()) return null
        val completed = completeFunctionParentheses(expression)
        if (!endsOperand(completed) || completed.count { it == '(' } != completed.count { it == ')' }) {
            return null
        }
        val result = evaluate(completed)
        return result.value?.takeIf { result.imaginaryValue == null }
    }

    private fun evaluate(expression: String): EvaluationResult {
        runCatching {
            CalculatorListExpressionEvaluator.evaluate(expression) { element ->
                val result = evaluate(element)
                val real = result.value ?: error("List elements must be scalar values")
                CalculatorScalarValue(real, result.imaginaryValue)
            }
        }.getOrNull()?.let { listValue ->
            return when (listValue) {
                is CalculatorListExpressionEvaluator.Value.List ->
                    EvaluationResult(formatListResult(listValue.value))
                is CalculatorListExpressionEvaluator.Value.Scalar ->
                    EvaluationResult(format(listValue.value.real), listValue.value.real, listValue.value.imaginary)
            }
        }
        val expandedExpression =
            expandNamedVariables(expression) ?: return EvaluationResult(SYNTAX_ERROR)
        val realEvaluation = runCatching { evaluateValue(expandedExpression) }
        realEvaluation.getOrNull()?.let { value ->
            val display = preferredFractionDisplay(expandedExpression) ?: format(value)
            return EvaluationResult(display, value)
        }
        val realFailure = realEvaluation.exceptionOrNull()
        if (
            realFailure is CalculatorEvaluationException &&
            realFailure.message in setOf(DOMAIN_ERROR, RESULT_OUT_OF_RANGE_ERROR, RESULT_TOO_LARGE_ERROR)
        ) {
            return EvaluationResult(realFailure.message ?: SYNTAX_ERROR)
        }

        if (ModeSettingsMemory.usesRectangularComplexFormat()) {
            val complexEvaluation = runCatching {
                ComplexExpressionEvaluator(
                    expandedExpression,
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

        return if (realFailure is CalculatorEvaluationException) {
            EvaluationResult(realFailure.message ?: SYNTAX_ERROR)
        } else {
            EvaluationResult(SYNTAX_ERROR)
        }
    }

    private fun preferredFractionDisplay(expression: String): String? {
        if ('.' in expression ||
            (FRACTION_FUNCTION !in expression && MIXED_FRACTION_FUNCTION !in expression)
        ) {
            return null
        }
        return runCatching {
            ExactFractionParser(expression, latestAnswer(), realVariableValues()).parse().display()
        }.getOrNull()
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

    /**
     * VARS tokens remain readable in editors, then expand to the expressions owned by their
     * backing memories just before evaluation. Recursive expansion permits Y2 to reference Y1
     * while rejecting direct or indirect cycles.
     */
    private fun expandNamedVariables(
        expression: String,
        expanding: Set<String> = emptySet()
    ): String? {
        val expanded = StringBuilder()
        var index = 0
        while (index < expression.length) {
            val token = NAMED_VARIABLE_TOKENS.firstOrNull {
                expression.startsWith(it, index)
            }
            if (token == null) {
                expanded.append(expression[index])
                index++
                continue
            }
            if (token in expanding) return null

            val valueExpression = namedVariableExpression(token) ?: return null
            val nested = expandNamedVariables(valueExpression, expanding + token) ?: return null
            expanded.append('(').append(nested).append(')')
            index += token.length
        }
        return expanded.toString()
    }

    private fun namedVariableExpression(token: String): String? = when (token) {
        "Xmin" -> WindowSettingsMemory.value(0)
        "Xmax" -> WindowSettingsMemory.value(1)
        "Xscl" -> WindowSettingsMemory.value(2)
        "Ymin" -> WindowSettingsMemory.value(3)
        "Ymax" -> WindowSettingsMemory.value(4)
        "Yscl" -> WindowSettingsMemory.value(5)
        "Xres" -> WindowSettingsMemory.value(6)
        "ΔX" -> WindowSettingsMemory.value(7)
        "TraceStep" -> WindowSettingsMemory.value(8)
        "ZXmin" -> ZoomMemory.variableWindow().getOrNull(0)
        "ZXmax" -> ZoomMemory.variableWindow().getOrNull(1)
        "ZXscl" -> ZoomMemory.variableWindow().getOrNull(2)
        "ZYmin" -> ZoomMemory.variableWindow().getOrNull(3)
        "ZYmax" -> ZoomMemory.variableWindow().getOrNull(4)
        "ZYscl" -> ZoomMemory.variableWindow().getOrNull(5)
        "ZXres" -> ZoomMemory.variableWindow().getOrNull(6)
        else -> {
            val functionIndex =
                ExpressionEditingTokens.yFunctionIndex(token)?.minus(1) ?: return null
            YEqualsMemory.equation(functionIndex).ifEmpty { null }
        }
    }

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

    /** Phase 5 list-result presentation: compact braces with space-separated elements. */
    private fun formatListResult(list: CalculatorListValue): String =
        list.values.joinToString(separator = " ", prefix = "{", postfix = "}") { value ->
            if (value.imaginary == null) format(value.real)
            else formatRectangularComplex(value.real.toDouble(), value.imaginary.toDouble())
        }

    private fun requireSupportedDouble(value: BigDecimal): Double {
        val converted = value.toDouble()
        if (converted.isInfinite()) {
            throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
        }
        if (converted == 0.0 && value.compareTo(BigDecimal.ZERO) != 0) {
            throw CalculatorEvaluationException(RESULT_OUT_OF_RANGE_ERROR)
        }
        return converted
    }

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
        cursor > 0 && (
            ExpressionEditingTokens.namedVariableEndingAt(currentEntry, cursor) != null ||
                currentEntry[cursor - 1].let {
                    it.isDigit() || it == ')' || it == '!' || isVariableSymbol(it) ||
                        it == COMPLEX_UNIT || it == PI_TOKEN || it == EULER_TOKEN ||
                        currentEntry.substring(0, cursor).endsWith(ANSWER_TOKEN)
                }
            )

    private fun endsOperand(): Boolean = endsOperand(currentEntry)

    private fun endsOperand(expression: String): Boolean =
        ExpressionEditingTokens.namedVariableEndingAt(expression, expression.length) != null ||
            expression.lastOrNull()?.let {
                it.isDigit() || it == ')' || it == '!' || isVariableSymbol(it) ||
                    it == COMPLEX_UNIT || it == PI_TOKEN || it == EULER_TOKEN ||
                    expression.endsWith(ANSWER_TOKEN)
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
        return previous.isDigit() || previous == '.' || previous == ')' || previous == '!' ||
            isVariableSymbol(previous) ||
            previous == COMPLEX_UNIT || previous == PI_TOKEN || previous == EULER_TOKEN ||
            currentEntry.substring(0, endExclusive).endsWith(ANSWER_TOKEN)
    }

    private fun isVariableSymbol(character: Char): Boolean =
        CalculatorVariable.fromSymbol(character) != null

    private fun entryTokenStartingAt(position: Int): String? =
        ExpressionEditingTokens.structuredFractionStartingAt(currentEntry, position)
            ?: ENTRY_TOKENS.firstOrNull { currentEntry.startsWith(it, position) }

    private fun entryTokenEndingAt(position: Int): String? =
        ExpressionEditingTokens.structuredFractionEndingAt(currentEntry, position)
            ?: ENTRY_TOKENS.firstOrNull { token ->
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
    private const val RESULT_OUT_OF_RANGE_ERROR = "Error: Result out of range"
    private const val DOMAIN_ERROR = "Error: Domain"
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
    private const val RECTANGULAR_TO_RADIUS = "R►Pr"
    private const val RECTANGULAR_TO_ANGLE = "R►Pθ"
    private const val POLAR_TO_X = "P►Rx"
    private const val POLAR_TO_Y = "P►Ry"
    private const val FRACTION_FUNCTION = "frac"
    private const val MIXED_FRACTION_FUNCTION = "mixed"
    private const val LOG_BASE_FUNCTION = "logBASE"
    private const val NTH_ROOT_FUNCTION = "nthRoot"
    private const val INDEXED_ROOT_FUNCTION = "root"
    private const val CUBE_ROOT_FUNCTION = "cubeRoot"
    private const val PERMUTATION_FUNCTION = "nPr"
    private const val COMBINATION_FUNCTION = "nCr"
    private const val DEGREE_MARKER = '°'
    private const val RADIAN_MARKER = 'ʳ'
    private const val COMPLEX_ZERO_EPSILON = 1.0e-12
    private const val MAX_POWER_RESULT_CHARACTERS = 4_096L
    private const val MAX_SCIENTIFIC_EXPONENT = 308
    private const val MAX_HISTORY_ENTRIES = 1_000
    private const val DEFAULT_ROUND_SIGNIFICANT_DIGITS = 10
    private val FRACTION_RESULT_PATTERN = Regex("-?\\d+/-?\\d+")
    private val FUNCTIONS = ExpressionEditingTokens.functionNames
    private val TRIG_FUNCTIONS = setOf("sin", "cos", "tan")
    private val RELATIONAL_OPERATORS = listOf("≠", "≥", "≤", "=", ">", "<")
    private val BOOLEAN_OPERATOR_TOKENS = listOf("and", "xor", "or")
    private val AUTO_CLOSING_PREFIXES = FUNCTIONS + listOf("10^", "$EULER_TOKEN^")
    private val NAMED_VARIABLE_TOKENS =
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
                listOf(ExpressionEditingTokens.yFunctionToken(index), "Y$index")
            }
            )
            .sortedByDescending(String::length)
    private val ENTRY_TOKENS =
        (
            FUNCTIONS.map { "$it(" } + BOOLEAN_OPERATOR_TOKENS +
                NAMED_VARIABLE_TOKENS +
                listOf("10^(", "$EULER_TOKEN^(", ANSWER_TOKEN, SCIENTIFIC_EXPONENT_TOKEN)
            )
            .sortedByDescending(String::length)
    private val FULL_TURN_DEGREES = BigDecimal("360")
    private val INVERSE_IDENTITY_TOLERANCE = BigDecimal("1E-14")
    private val SPECIAL_ANGLE_DEGREES =
        listOf(0, 30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 330)
    private val SQRT_TWO_OVER_TWO = Math.sqrt(0.5)
    private val SQRT_THREE = Math.sqrt(3.0)
    private val SQRT_THREE_OVER_TWO = SQRT_THREE / 2.0
    private val ONE_OVER_SQRT_THREE = 1.0 / SQRT_THREE
    private const val RADIAN_ANGLE_TOLERANCE_DEGREES = 1.0e-10
    private val MAX_GCD_LCM_ARGUMENT = BigDecimal("1000000000000").toBigInteger()
    private val CALCULATION_CONTEXT = MathContext(34, RoundingMode.HALF_UP)

    private class CalculatorEvaluationException(message: String) : IllegalArgumentException(message)

    /** Recursive-descent evaluator with calculator arithmetic, relation, and Boolean precedence. */
    private class ExpressionParser(
        private val expression: String,
        private val previousAnswer: BigDecimal?,
        private val variableValues: Map<CalculatorVariable, BigDecimal?>
    ) {
        private var index = 0

        fun parse(): BigDecimal {
            val result = parseLogic()
            check(index == expression.length) { "Unexpected expression content" }
            return result
        }

        private fun parseLogic(): BigDecimal {
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

        private fun parseAnd(): BigDecimal {
            var result = parseRelation()
            while (expression.startsWith("and", index)) {
                index += 3
                val right = parseRelation()
                result = booleanResult(isTrue(result) && isTrue(right))
            }
            return result
        }

        private fun parseRelation(): BigDecimal {
            var result = parseSum()
            while (true) {
                val operator = RELATIONAL_OPERATORS.firstOrNull {
                    expression.startsWith(it, index)
                } ?: return result
                index += operator.length
                val comparison = result.compareTo(parseSum())
                result = booleanResult(
                    when (operator) {
                        "=" -> comparison == 0
                        "≠" -> comparison != 0
                        ">" -> comparison > 0
                        "≥" -> comparison >= 0
                        "<" -> comparison < 0
                        "≤" -> comparison <= 0
                        else -> error("Unsupported relation: $operator")
                    }
                )
            }
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
            val result = parsePostfix()
            return if (index < expression.length && expression[index] == '^') {
                index++
                val exponent = parseUnary()
                if (result.compareTo(BigDecimal.ZERO) == 0 && exponent.compareTo(BigDecimal.ZERO) < 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                if (exponent.stripTrailingZeros().scale() <= 0) {
                    integerPower(result, exponent)
                } else {
                    val powered = Math.pow(
                        requireSupportedDouble(result),
                        requireSupportedDouble(exponent)
                    )
                    if (powered.isInfinite()) throw CalculatorEvaluationException(RESULT_TOO_LARGE_ERROR)
                    check(!powered.isNaN()) { "Power is not real" }
                    if (powered == 0.0 && result.compareTo(BigDecimal.ZERO) != 0) {
                        throw CalculatorEvaluationException(RESULT_OUT_OF_RANGE_ERROR)
                    }
                    BigDecimal.valueOf(powered)
                }
            } else {
                result
            }
        }

        private fun parsePostfix(): BigDecimal {
            var result = parsePrimary()
            while (index < expression.length &&
                expression[index] in charArrayOf(DEGREE_MARKER, RADIAN_MARKER, '!')
            ) {
                result = when (expression[index++]) {
                    DEGREE_MARKER ->
                        if (ModeSettingsMemory.usesDegrees()) result
                        else result.multiply(
                            BigDecimal.valueOf(Math.PI / 180.0),
                            CALCULATION_CONTEXT
                        )
                    RADIAN_MARKER ->
                        if (ModeSettingsMemory.usesDegrees()) {
                            result.multiply(
                                BigDecimal.valueOf(180.0 / Math.PI),
                                CALCULATION_CONTEXT
                            )
                        } else {
                            result
                        }
                    '!' -> factorial(result)
                    else -> error("Unsupported angle marker")
                }
            }
            return result
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

            if (index < expression.length &&
                FUNCTIONS.none { expression.startsWith(it, index) }
            ) {
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
                val arguments = parseFunctionArguments()
                check(index < expression.length && expression[index] == ')') {
                    "Missing closing parenthesis"
                }
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
            if (magnitude > MAX_SCIENTIFIC_EXPONENT) {
                throw CalculatorEvaluationException(
                    if (negative) RESULT_OUT_OF_RANGE_ERROR else RESULT_TOO_LARGE_ERROR
                )
            }
            return mantissa.scaleByPowerOfTen(exponent)
        }

        private fun parseFunctionArguments(): List<BigDecimal> {
            check(index < expression.length && expression[index] != ')') {
                "A function argument is required"
            }
            val arguments = mutableListOf<BigDecimal>()
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

        private fun evaluateFunction(function: String, arguments: List<BigDecimal>): BigDecimal {
            if (function == FRACTION_FUNCTION) {
                check(arguments.size == 2) { "frac requires two arguments" }
                val denominator = arguments[1]
                if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                return arguments[0].divide(denominator, CALCULATION_CONTEXT)
            }
            if (function == MIXED_FRACTION_FUNCTION) {
                check(arguments.size == 3) { "mixed requires three arguments" }
                val denominator = arguments[2]
                if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                val fractionalPart = arguments[1].abs().divide(denominator.abs(), CALCULATION_CONTEXT)
                return if (arguments[0].signum() < 0) {
                    arguments[0].subtract(fractionalPart, CALCULATION_CONTEXT)
                } else {
                    arguments[0].add(fractionalPart, CALCULATION_CONTEXT)
                }
            }
            if (function in COORDINATE_FUNCTIONS) {
                check(arguments.size == 2) { "$function requires two arguments" }
                val first = requireSupportedDouble(arguments[0])
                val second = requireSupportedDouble(arguments[1])
                val usesDegrees = ModeSettingsMemory.usesDegrees()
                val result = when (function) {
                    RECTANGULAR_TO_RADIUS -> Math.hypot(first, second)
                    RECTANGULAR_TO_ANGLE ->
                        inverseAngle(Math.atan2(second, first), usesDegrees)
                    POLAR_TO_X -> first * Math.cos(if (usesDegrees) Math.toRadians(second) else second)
                    POLAR_TO_Y -> first * Math.sin(if (usesDegrees) Math.toRadians(second) else second)
                    else -> error("Unsupported coordinate function: $function")
                }
                check(result.isFinite()) { "Function result is outside the supported range" }
                return BigDecimal.valueOf(normalizeCoordinateResult(result))
            }
            if (function == "not") {
                check(arguments.size == 1) { "not requires one argument" }
                return booleanResult(!isTrue(arguments.single()))
            }
            if (function == "abs") {
                check(arguments.size == 1) { "abs requires one argument" }
                return arguments.single().abs()
            }
            if (function == "round") {
                check(arguments.size in 1..2) { "round requires one or two arguments" }
                val scale = arguments.getOrNull(1)?.let(::decimalPlaceCount)
                return if (scale == null) {
                    arguments.first().round(
                        MathContext(DEFAULT_ROUND_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)
                    )
                } else {
                    arguments.first().setScale(scale, RoundingMode.HALF_UP)
                }
            }
            if (function == "iPart") {
                check(arguments.size == 1) { "iPart requires one argument" }
                return arguments.single().setScale(0, RoundingMode.DOWN)
            }
            if (function == "fPart") {
                check(arguments.size == 1) { "fPart requires one argument" }
                val value = arguments.single()
                return value.subtract(value.setScale(0, RoundingMode.DOWN), CALCULATION_CONTEXT)
            }
            if (function == "int") {
                check(arguments.size == 1) { "int requires one argument" }
                return arguments.single().setScale(0, RoundingMode.FLOOR)
            }
            if (function == "min" || function == "max") {
                check(arguments.size == 2) { "$function requires two arguments" }
                return if ((function == "min" && arguments[1] < arguments[0]) ||
                    (function == "max" && arguments[1] > arguments[0])
                ) arguments[1] else arguments[0]
            }
            if (function == "gcd" || function == "lcm") {
                check(arguments.size == 2) { "$function requires two arguments" }
                val left = nonnegativeGcdLcmArgument(arguments[0])
                val right = nonnegativeGcdLcmArgument(arguments[1])
                val gcd = left.gcd(right)
                return BigDecimal(
                    if (function == "gcd") gcd
                    else if (left.signum() == 0 || right.signum() == 0) java.math.BigInteger.ZERO
                    else left.divide(gcd).multiply(right)
                )
            }
            if (function == "remainder") {
                check(arguments.size == 2) { "remainder requires two arguments" }
                val dividend = arguments[0].toBigIntegerExact()
                val divisor = arguments[1].toBigIntegerExact()
                check(dividend.signum() >= 0) { "remainder requires a nonnegative dividend" }
                if (divisor.signum() == 0) {
                    throw CalculatorEvaluationException(DIVISION_BY_ZERO_ERROR)
                }
                check(divisor.signum() > 0) { "remainder requires a positive divisor" }
                return BigDecimal(dividend.remainder(divisor))
            }
            if (function == LOG_BASE_FUNCTION) {
                check(arguments.size == 2) { "logBASE requires two arguments" }
                val value = requireSupportedDouble(arguments[0])
                val base = requireSupportedDouble(arguments[1])
                check(value > 0.0 && base > 0.0 && base != 1.0) {
                    "logBASE requires a positive value and positive base other than 1"
                }
                val result = Math.log(value) / Math.log(base)
                check(result.isFinite()) { "Function result is outside the supported range" }
                return BigDecimal.valueOf(result)
            }
            if (function == NTH_ROOT_FUNCTION || function == INDEXED_ROOT_FUNCTION) {
                check(arguments.size == 2) { "$function requires an index and value" }
                val valueArgument =
                    if (function == INDEXED_ROOT_FUNCTION) arguments[1] else arguments[0]
                val rootArgument =
                    if (function == INDEXED_ROOT_FUNCTION) arguments[0] else arguments[1]
                val value = requireSupportedDouble(valueArgument)
                val root = requireSupportedDouble(rootArgument)
                check(root != 0.0) { "nthRoot index cannot be zero" }
                val result = if (value < 0.0) {
                    val integerRoot = rootArgument.intValueExact()
                    check(integerRoot % 2 != 0) { "Even root of a negative value is not real" }
                    -Math.pow(-value, 1.0 / integerRoot)
                } else {
                    Math.pow(value, 1.0 / root)
                }
                check(result.isFinite()) { "Function result is outside the supported range" }
                return BigDecimal.valueOf(normalizeCoordinateResult(result))
            }
            if (function == CUBE_ROOT_FUNCTION) {
                check(arguments.size == 1) { "cubeRoot requires one argument" }
                val result = Math.cbrt(requireSupportedDouble(arguments.single()))
                check(result.isFinite()) { "Function result is outside the supported range" }
                return BigDecimal.valueOf(normalizeCoordinateResult(result))
            }
            if (function == PERMUTATION_FUNCTION || function == COMBINATION_FUNCTION) {
                check(arguments.size == 2) { "$function requires two arguments" }
                val n = combinatoricArgument(arguments[0])
                val r = combinatoricArgument(arguments[1])
                check(r <= n) { "$function requires r no larger than n" }
                val result = if (function == PERMUTATION_FUNCTION) {
                    ((n - r + 1)..n).fold(BigInteger.ONE) { product, factor ->
                        product.multiply(BigInteger.valueOf(factor.toLong()))
                    }
                } else {
                    val smallerR = minOf(r, n - r)
                    (1..smallerR).fold(BigInteger.ONE) { result, step ->
                        result.multiply(BigInteger.valueOf((n - smallerR + step).toLong()))
                            .divide(BigInteger.valueOf(step.toLong()))
                    }
                }
                return BigDecimal(result)
            }

            check(arguments.size == 1) { "$function requires one argument" }
            val argument = arguments.single()
            val usesDegrees = ModeSettingsMemory.usesDegrees()
            exactForwardTrig(function, argument, usesDegrees)?.let {
                return BigDecimal.valueOf(it)
            }
            exactInverseTrig(function, argument, usesDegrees)?.let {
                return BigDecimal.valueOf(it)
            }

            val value = requireSupportedDouble(argument)
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
            check(result.isFinite()) { "Function result is outside the supported range" }
            return BigDecimal.valueOf(result)
        }

        private fun decimalPlaceCount(value: BigDecimal): Int {
            val count = value.intValueExact()
            check(count in 0..9) { "Decimal-place count must be from 0 through 9" }
            return count
        }

        private fun normalizeCoordinateResult(value: Double): Double {
            val nearestInteger = Math.rint(value)
            return when {
                kotlin.math.abs(value) < ANGLE_IDENTITY_EPSILON -> 0.0
                kotlin.math.abs(value - nearestInteger) < ANGLE_IDENTITY_EPSILON -> nearestInteger
                else -> value
            }
        }

        private fun nonnegativeGcdLcmArgument(value: BigDecimal): java.math.BigInteger {
            val integer = value.toBigIntegerExact()
            check(integer.signum() >= 0 && integer <= MAX_GCD_LCM_ARGUMENT) {
                "gcd and lcm arguments must be nonnegative integers no larger than 1E12"
            }
            return integer
        }

        private fun combinatoricArgument(value: BigDecimal): Int {
            val integer = value.intValueExact()
            check(integer in 0..MAX_COMBINATORIC_ARGUMENT) {
                "Combinatoric arguments must be integers from 0 through $MAX_COMBINATORIC_ARGUMENT"
            }
            return integer
        }

        private fun factorial(value: BigDecimal): BigDecimal {
            val integer = value.intValueExact()
            check(integer in 0..MAX_FACTORIAL_ARGUMENT) {
                "Factorial requires an integer from 0 through $MAX_FACTORIAL_ARGUMENT"
            }
            return BigDecimal(
                (2..integer).fold(BigInteger.ONE) { result, factor ->
                    result.multiply(BigInteger.valueOf(factor.toLong()))
                }
            )
        }

        private fun isTrue(value: BigDecimal): Boolean = value.compareTo(BigDecimal.ZERO) != 0

        private fun booleanResult(value: Boolean): BigDecimal =
            if (value) BigDecimal.ONE else BigDecimal.ZERO

        private fun inverseAngle(radians: Double, usesDegrees: Boolean): Double =
            if (usesDegrees) Math.toDegrees(radians) else radians

        private fun exactForwardTrig(
            function: String,
            argument: BigDecimal,
            usesDegrees: Boolean
        ): Double? {
            if (function !in TRIG_FUNCTIONS) return null
            val degrees = specialAngleDegrees(argument, usesDegrees) ?: return null
            return when (function) {
                "sin" -> when (degrees) {
                    0, 180 -> 0.0
                    30, 150 -> 0.5
                    45, 135 -> SQRT_TWO_OVER_TWO
                    60, 120 -> SQRT_THREE_OVER_TWO
                    90 -> 1.0
                    210, 330 -> -0.5
                    225, 315 -> -SQRT_TWO_OVER_TWO
                    240, 300 -> -SQRT_THREE_OVER_TWO
                    270 -> -1.0
                    else -> null
                }
                "cos" -> when (degrees) {
                    0 -> 1.0
                    30, 330 -> SQRT_THREE_OVER_TWO
                    45, 315 -> SQRT_TWO_OVER_TWO
                    60, 300 -> 0.5
                    90, 270 -> 0.0
                    120, 240 -> -0.5
                    135, 225 -> -SQRT_TWO_OVER_TWO
                    150, 210 -> -SQRT_THREE_OVER_TWO
                    180 -> -1.0
                    else -> null
                }
                "tan" -> when (degrees) {
                    90, 270 -> throw CalculatorEvaluationException(DOMAIN_ERROR)
                    0, 180 -> 0.0
                    30, 210 -> ONE_OVER_SQRT_THREE
                    45, 225 -> 1.0
                    60, 240 -> SQRT_THREE
                    120, 300 -> -SQRT_THREE
                    135, 315 -> -1.0
                    150, 330 -> -ONE_OVER_SQRT_THREE
                    else -> null
                }
                else -> null
            }
        }

        private fun exactInverseTrig(
            function: String,
            argument: BigDecimal,
            usesDegrees: Boolean
        ): Double? {
            val identities = when (function) {
                INVERSE_SINE -> listOf(
                    -1.0 to -90,
                    -SQRT_THREE_OVER_TWO to -60,
                    -SQRT_TWO_OVER_TWO to -45,
                    -0.5 to -30,
                    0.0 to 0,
                    0.5 to 30,
                    SQRT_TWO_OVER_TWO to 45,
                    SQRT_THREE_OVER_TWO to 60,
                    1.0 to 90
                )
                INVERSE_COSINE -> listOf(
                    -1.0 to 180,
                    -SQRT_THREE_OVER_TWO to 150,
                    -SQRT_TWO_OVER_TWO to 135,
                    -0.5 to 120,
                    0.0 to 90,
                    0.5 to 60,
                    SQRT_TWO_OVER_TWO to 45,
                    SQRT_THREE_OVER_TWO to 30,
                    1.0 to 0
                )
                INVERSE_TANGENT -> listOf(
                    -SQRT_THREE to -60,
                    -1.0 to -45,
                    -ONE_OVER_SQRT_THREE to -30,
                    0.0 to 0,
                    ONE_OVER_SQRT_THREE to 30,
                    1.0 to 45,
                    SQRT_THREE to 60
                )
                else -> return null
            }
            val degrees = identities.firstOrNull { (knownValue, _) ->
                argument.subtract(BigDecimal.valueOf(knownValue)).abs() <=
                    INVERSE_IDENTITY_TOLERANCE
            }?.second ?: return null
            return if (usesDegrees) degrees.toDouble() else Math.toRadians(degrees.toDouble())
        }

        private fun specialAngleDegrees(argument: BigDecimal, usesDegrees: Boolean): Int? {
            if (usesDegrees) {
                var normalized = argument.remainder(FULL_TURN_DEGREES)
                if (normalized.signum() < 0) normalized = normalized.add(FULL_TURN_DEGREES)
                return SPECIAL_ANGLE_DEGREES.firstOrNull {
                    normalized.compareTo(BigDecimal.valueOf(it.toLong())) == 0
                }
            }

            val normalized =
                ((Math.toDegrees(requireSupportedDouble(argument)) % 360.0) + 360.0) % 360.0
            return SPECIAL_ANGLE_DEGREES.firstOrNull {
                kotlin.math.abs(normalized - it) <= RADIAN_ANGLE_TOLERANCE_DEGREES ||
                    kotlin.math.abs(normalized - it - 360.0) <= RADIAN_ANGLE_TOLERANCE_DEGREES
            }
        }
    }

    private class ExactFraction private constructor(
        val numerator: BigInteger,
        val denominator: BigInteger
    ) {
        fun add(other: ExactFraction): ExactFraction =
            of(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator)
            )

        fun subtract(other: ExactFraction): ExactFraction =
            add(of(other.numerator.negate(), other.denominator))

        fun multiply(other: ExactFraction): ExactFraction =
            of(numerator.multiply(other.numerator), denominator.multiply(other.denominator))

        fun divide(other: ExactFraction): ExactFraction {
            check(other.numerator.signum() != 0) { "Division by zero" }
            return of(numerator.multiply(other.denominator), denominator.multiply(other.numerator))
        }

        fun negate(): ExactFraction = of(numerator.negate(), denominator)

        fun abs(): ExactFraction = of(numerator.abs(), denominator)

        fun pow(exponent: Int): ExactFraction = when {
            exponent == 0 -> ONE
            exponent > 0 -> of(numerator.pow(exponent), denominator.pow(exponent))
            else -> {
                check(numerator.signum() != 0) { "Division by zero" }
                of(denominator.pow(-exponent), numerator.pow(-exponent))
            }
        }

        fun display(): String =
            if (denominator == BigInteger.ONE) numerator.toString()
            else "$numerator/$denominator"

        companion object {
            val ONE = of(BigInteger.ONE, BigInteger.ONE)

            fun of(numerator: BigInteger, denominator: BigInteger): ExactFraction {
                check(denominator.signum() != 0) { "Division by zero" }
                val sign = if (denominator.signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
                val signedNumerator = numerator.multiply(sign)
                val positiveDenominator = denominator.multiply(sign)
                val gcd = signedNumerator.gcd(positiveDenominator)
                return ExactFraction(
                    signedNumerator.divide(gcd),
                    positiveDenominator.divide(gcd)
                )
            }

            fun fromDecimal(value: BigDecimal): ExactFraction {
                val stripped = value.stripTrailingZeros()
                return if (stripped.scale() >= 0) {
                    of(stripped.unscaledValue(), BigInteger.TEN.pow(stripped.scale()))
                } else {
                    of(
                        stripped.unscaledValue().multiply(BigInteger.TEN.pow(-stripped.scale())),
                        BigInteger.ONE
                    )
                }
            }
        }
    }

    /** Exact rational subset used only when a completed expression contains a FRAC template. */
    private class ExactFractionParser(
        private val expression: String,
        private val previousAnswer: BigDecimal?,
        private val variableValues: Map<CalculatorVariable, BigDecimal?>
    ) {
        private var index = 0

        fun parse(): ExactFraction {
            val result = parseSum()
            check(index == expression.length) { "Unsupported exact-fraction expression" }
            return result
        }

        private fun parseSum(): ExactFraction {
            var result = parseProduct()
            while (index < expression.length && expression[index] in "+-") {
                result = if (expression[index++] == '+') {
                    result.add(parseProduct())
                } else {
                    result.subtract(parseProduct())
                }
            }
            return result
        }

        private fun parseProduct(): ExactFraction {
            var result = parseUnary()
            while (index < expression.length) {
                result = when {
                    expression[index] == '*' -> {
                        index++
                        result.multiply(parseUnary())
                    }
                    expression[index] == '/' -> {
                        index++
                        result.divide(parseUnary())
                    }
                    startsPrimaryAt(index) -> result.multiply(parseUnary())
                    else -> return result
                }
            }
            return result
        }

        private fun parseUnary(): ExactFraction =
            if (index < expression.length && expression[index] == '-') {
                index++
                parseUnary().negate()
            } else {
                parsePower()
            }

        private fun parsePower(): ExactFraction {
            val base = parsePrimary()
            if (index >= expression.length || expression[index] != '^') return base
            index++
            val exponent = parseUnary()
            check(exponent.denominator == BigInteger.ONE) { "Fractional powers are not exact" }
            val integerExponent = exponent.numerator.intValueExact()
            check(integerExponent in -1_000..1_000) { "Exact exponent is too large" }
            return base.pow(integerExponent)
        }

        private fun parsePrimary(): ExactFraction {
            if (expression.startsWith(ANSWER_TOKEN, index)) {
                index += ANSWER_TOKEN.length
                return ExactFraction.fromDecimal(
                    checkNotNull(previousAnswer) { "No previous answer is available" }
                )
            }

            if (expression.startsWith(FRACTION_FUNCTION, index) ||
                expression.startsWith(MIXED_FRACTION_FUNCTION, index)
            ) {
                val function = if (expression.startsWith(MIXED_FRACTION_FUNCTION, index)) {
                    MIXED_FRACTION_FUNCTION
                } else {
                    FRACTION_FUNCTION
                }
                index += function.length
                check(index < expression.length && expression[index++] == '(')
                val arguments = parseArguments()
                check(index < expression.length && expression[index++] == ')')
                return when (function) {
                    FRACTION_FUNCTION -> {
                        check(arguments.size == 2)
                        arguments[0].divide(arguments[1])
                    }
                    MIXED_FRACTION_FUNCTION -> {
                        check(arguments.size == 3)
                        val part = arguments[1].abs().divide(arguments[2].abs())
                        if (arguments[0].numerator.signum() < 0) {
                            arguments[0].subtract(part)
                        } else {
                            arguments[0].add(part)
                        }
                    }
                    else -> error("Unsupported exact function")
                }
            }

            if (index < expression.length) {
                CalculatorVariable.fromSymbol(expression[index])?.let { variable ->
                    index++
                    return ExactFraction.fromDecimal(
                        checkNotNull(variableValues[variable]) {
                            "Complex variables are not exact real fractions"
                        }
                    )
                }
            }

            if (index < expression.length && expression[index] == '(') {
                index++
                val result = parseSum()
                check(index < expression.length && expression[index++] == ')')
                return result
            }

            val start = index
            while (index < expression.length && expression[index].isDigit()) index++
            check(start != index) { "Expected an exact integer" }
            return ExactFraction.of(
                BigInteger(expression.substring(start, index)),
                BigInteger.ONE
            )
        }

        private fun parseArguments(): List<ExactFraction> {
            val arguments = mutableListOf<ExactFraction>()
            do {
                arguments += parseSum()
                if (index >= expression.length || expression[index] != ',') break
                index++
            } while (true)
            return arguments
        }

        private fun startsPrimaryAt(position: Int): Boolean =
            position < expression.length && (
                expression[position].isDigit() ||
                    expression[position] == '(' ||
                    CalculatorVariable.fromSymbol(expression[position]) != null ||
                    expression.startsWith(ANSWER_TOKEN, position) ||
                    expression.startsWith(FRACTION_FUNCTION, position) ||
                    expression.startsWith(MIXED_FRACTION_FUNCTION, position)
                )
    }

    private val COORDINATE_FUNCTIONS = setOf(
        RECTANGULAR_TO_RADIUS,
        RECTANGULAR_TO_ANGLE,
        POLAR_TO_X,
        POLAR_TO_Y
    )
    private const val ANGLE_IDENTITY_EPSILON = 1.0e-12
    private const val MAX_COMBINATORIC_ARGUMENT = 10_000
    private const val MAX_FACTORIAL_ARGUMENT = 1_000
}
