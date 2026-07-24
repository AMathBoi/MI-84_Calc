package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** Typed scalar variables accepted by the Alpha A-Z and θ input layer. */
enum class CalculatorVariable(val symbol: Char) {
    A('A'),
    B('B'),
    C('C'),
    D('D'),
    E('E'),
    F('F'),
    G('G'),
    H('H'),
    I('I'),
    J('J'),
    K('K'),
    L('L'),
    M('M'),
    N('N'),
    O('O'),
    P('P'),
    Q('Q'),
    R('R'),
    S('S'),
    T('T'),
    U('U'),
    V('V'),
    W('W'),
    X('X'),
    Y('Y'),
    Z('Z'),
    THETA('θ');

    companion object {
        fun fromSymbol(symbol: Char): CalculatorVariable? = entries.firstOrNull { it.symbol == symbol }
    }
}

data class CalculatorScalarValue(
    val real: BigDecimal,
    val imaginary: BigDecimal? = null
)

/**
 * Persistent scalar A-Z/θ storage. Every variable defaults to real zero. X is mirrored to the
 * legacy display-memory `x` line so existing `expression->X` saves remain compatible.
 */
object CalculatorVariableMemory {
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_scalar_variables.txt")
    private val values = CalculatorVariable.entries.associateWith {
        CalculatorScalarValue(BigDecimal.ZERO)
    }.toMutableMap()
    private val loadedVariables = mutableSetOf<CalculatorVariable>()

    init {
        load()
    }

    fun value(variable: CalculatorVariable): CalculatorScalarValue = values.getValue(variable)

    fun set(variable: CalculatorVariable, real: BigDecimal, imaginary: BigDecimal? = null) {
        values[variable] = CalculatorScalarValue(
            real,
            imaginary?.takeUnless { it.compareTo(BigDecimal.ZERO) == 0 }
        )
        loadedVariables += variable
        save()
    }

    /**
     * Imports X from the legacy display-memory line only when the scalar-variable file has no X.
     * The legacy line remains in place and continues to be written for backward compatibility.
     */
    fun initializeLegacyX(value: BigDecimal) {
        if (CalculatorVariable.X in loadedVariables) return
        values[CalculatorVariable.X] = CalculatorScalarValue(value)
        if (value.compareTo(BigDecimal.ZERO) != 0) save()
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines.forEach { line ->
                val parts = line.split('\t', limit = 3)
                val variable = parts.getOrNull(0)
                    ?.singleOrNull()
                    ?.let(CalculatorVariable::fromSymbol)
                    ?: return@forEach
                val real = parts.getOrNull(1)?.toBigDecimalOrNull() ?: return@forEach
                val imaginary = parts.getOrNull(2)
                    ?.takeIf(String::isNotEmpty)
                    ?.toBigDecimalOrNull()
                values[variable] = CalculatorScalarValue(
                    real,
                    imaginary?.takeUnless { it.compareTo(BigDecimal.ZERO) == 0 }
                )
                loadedVariables += variable
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            CalculatorVariable.entries.map { variable ->
                val value = values.getValue(variable)
                "${variable.symbol}\t${value.real.toPlainString()}\t" +
                    (value.imaginary?.toPlainString() ?: "")
            }
        }
    }
}
