package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** The six built-in list variables. User-defined names live alongside them in [CalculatorListMemory]. */
enum class CalculatorListName {
    L1, L2, L3, L4, L5, L6;

    val token: String get() = name

    companion object {
        fun fromToken(token: String): CalculatorListName? = entries.firstOrNull { it.token == token }
    }
}

/** A list element keeps the same real/rectangular-complex representation as scalar memory. */
data class CalculatorListValue(val values: List<CalculatorScalarValue>) {
    init { require(values.size <= MAX_LIST_LENGTH) { "List is too long" } }

    val dimension: Int get() = values.size
    val hasComplexValues: Boolean get() = values.any { it.imaginary != null }

    companion object { const val MAX_LIST_LENGTH = 999 }
}

/**
 * Persistent built-in list storage. The tab-delimited format is deliberately local to this new
 * file: one line per L1-L6, with elements encoded as real[,imaginary].
 */
object CalculatorListMemory {
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_lists.txt")
    private val values = CalculatorListName.entries.associateWith { CalculatorListValue(emptyList()) }
        .toMutableMap()
    private val namedValues = linkedMapOf<String, CalculatorListValue>()
    private val listOrder = CalculatorListName.entries.map(CalculatorListName::token).toMutableList()

    init { load() }

    fun value(name: CalculatorListName): CalculatorListValue = values.getValue(name)

    fun names(): List<String> = listOrder.toList()

    /** Named lists retain table order, but menus place them after the built-in L1–L6 entries. */
    fun namedNames(): List<String> = listOrder.filter { it in namedValues }

    fun value(name: String): CalculatorListValue? =
        CalculatorListName.fromToken(name)?.let(::value) ?: namedValues[name]

    /** Named-list references cannot collide with Alpha scalar variables in expression storage. */
    fun referenceToken(name: String): String =
        if (CalculatorListName.fromToken(name) != null) name else "@$name"

    fun set(name: CalculatorListName, value: CalculatorListValue) {
        values[name] = value
        save()
    }

    fun set(name: String, value: CalculatorListValue): Boolean {
        val builtIn = CalculatorListName.fromToken(name)
        when {
            builtIn != null -> values[builtIn] = value
            name in namedValues -> namedValues[name] = value
            else -> return false
        }
        save()
        return true
    }

    fun clear(name: CalculatorListName) = set(name, CalculatorListValue(emptyList()))

    fun clear(name: String): Boolean = set(name, CalculatorListValue(emptyList()))

    fun createNamed(name: String, before: String? = null): Boolean {
        if (!isValidNamedList(name) || name in namedValues || CalculatorListName.fromToken(name) != null) return false
        namedValues[name] = CalculatorListValue(emptyList())
        val index = before?.let(listOrder::indexOf)?.takeIf { it >= 0 } ?: listOrder.size
        listOrder.add(index, name)
        save()
        return true
    }

    fun isValidNamedList(name: String): Boolean =
        name.length in 1..5 && name.all { it in 'A'..'Z' }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.firstOrNull() == ORDER_PREFIX) return@forEach
                if (fields.firstOrNull() == NAMED_PREFIX) {
                    val name = fields.getOrNull(1)?.takeIf(::isValidNamedList) ?: return@forEach
                    val parsed = fields.drop(2).mapNotNull(::parseValue)
                    if (parsed.size == fields.size - 2 && parsed.size <= CalculatorListValue.MAX_LIST_LENGTH) {
                        namedValues[name] = CalculatorListValue(parsed)
                    }
                    return@forEach
                }
                val name = fields.firstOrNull()?.let(CalculatorListName::fromToken) ?: return@forEach
                val parsed = fields.drop(1).mapNotNull(::parseValue)
                if (parsed.size == fields.size - 1 && parsed.size <= CalculatorListValue.MAX_LIST_LENGTH) {
                    values[name] = CalculatorListValue(parsed)
                }
            }
            val savedOrder = lines.firstOrNull { it.startsWith("$ORDER_PREFIX\t") }
                ?.split('\t')
                ?.drop(1)
                .orEmpty()
            if (savedOrder.isNotEmpty()) {
                listOrder.clear()
                savedOrder.filter { name ->
                    CalculatorListName.fromToken(name) != null || name in namedValues
                }.distinct().forEach(listOrder::add)
                (CalculatorListName.entries.map(CalculatorListName::token) + namedValues.keys)
                    .filterNot(listOrder::contains)
                    .forEach(listOrder::add)
            } else {
                namedValues.keys.forEach(listOrder::add)
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            listOf((listOf(ORDER_PREFIX) + listOrder).joinToString("\t")) +
                CalculatorListName.entries.map { name ->
                (listOf(name.token) + value(name).values.map(::formatValue)).joinToString("\t")
            } + listOrder.filter { it in namedValues }.map { name ->
                val value = namedValues.getValue(name)
                (listOf(NAMED_PREFIX, name) + value.values.map(::formatValue)).joinToString("\t")
            }
        }
    }

    private fun parseValue(text: String): CalculatorScalarValue? {
        val parts = text.split(',', limit = 2)
        val real = parts[0].toBigDecimalOrNull() ?: return null
        val imaginary = parts.getOrNull(1)?.toBigDecimalOrNull()
        return CalculatorScalarValue(real, imaginary)
    }

    private fun formatValue(value: CalculatorScalarValue): String =
        value.real.toPlainString() + (value.imaginary?.let { ",$it" } ?: "")

    private const val NAMED_PREFIX = "user"
    private const val ORDER_PREFIX = "order"
}
