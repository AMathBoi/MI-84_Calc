package net.amathboi.mi84mod.client.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** Persistent calculator mode choices and the selection used by the Mode LCD view. */
object ModeSettingsMemory {
    data class ModeSetting(val category: String, val options: List<String>, val defaultOption: String)

    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_mode_settings.txt")

    private val settings = listOf(
        ModeSetting("Number Display", listOf("Normal", "Sci", "Eng"), "Normal"),
        ModeSetting("Decimal Display", listOf("Float") + (0..9).map(Int::toString), "Float"),
        // Keep the mod's existing radian behavior until the player deliberately chooses Degree.
        ModeSetting("Angle Unit", listOf("Degree", "Radian"), "Radian"),
        ModeSetting("Graphing Order", listOf("Sequential", "Simul"), "Sequential"),
        ModeSetting("Complex Number Format", listOf("Real", "a+bi", "re^(θi)"), "Real"),
        ModeSetting("Fraction Type", listOf("n/d", "Un/d"), "n/d"),
        ModeSetting("Answers", listOf("Auto", "Dec"), "Auto"),
        ModeSetting("Stat Diagnostics", listOf("Off", "On"), "Off"),
        ModeSetting("Stat Wizards", listOf("On", "Off"), "On")
    )
    private val selectedOptions = settings.map { setting ->
        setting.options.indexOf(setting.defaultOption)
    }.toMutableList()

    var selectedCategoryIndex = 0
        private set

    init {
        load()
    }

    fun size(): Int = settings.size

    fun category(index: Int): String = settings[index].category

    fun options(index: Int): List<String> = settings[index].options

    fun selectedOptionIndex(index: Int): Int = selectedOptions[index]

    fun selectedOption(index: Int): String = settings[index].options[selectedOptions[index]]

    fun selectPreviousCategory() {
        selectedCategoryIndex = (selectedCategoryIndex - 1).coerceAtLeast(0)
    }

    fun selectNextCategory() {
        selectedCategoryIndex = (selectedCategoryIndex + 1).coerceAtMost(settings.lastIndex)
    }

    fun selectPreviousOption() = moveOption(-1)

    fun selectNextOption() = moveOption(1)

    fun usesDegrees(): Boolean = selectedOption(ANGLE_UNIT_INDEX) == "Degree"

    fun usesRectangularComplexFormat(): Boolean =
        selectedOption(COMPLEX_NUMBER_FORMAT_INDEX) == "a+bi"

    /** Values shown in the always-visible LCD mode indicator, in display order. */
    fun indicatorValues(): List<String> = listOf(
        selectedOption(NUMBER_DISPLAY_INDEX),
        selectedOption(DECIMAL_DISPLAY_INDEX),
        selectedOption(ANSWERS_INDEX),
        selectedOption(COMPLEX_NUMBER_FORMAT_INDEX),
        selectedOption(ANGLE_UNIT_INDEX)
    )

    /** Applies Number Display and Decimal Display without changing the calculated value. */
    fun formatNumber(value: BigDecimal): String {
        val decimalPlaces = selectedOption(DECIMAL_DISPLAY_INDEX).toIntOrNull()
        if (value.compareTo(BigDecimal.ZERO) != 0 && value.abs() < NORMAL_SCIENTIFIC_THRESHOLD) {
            return formatExponent(value, decimalPlaces, engineering = false)
        }
        return when (selectedOption(NUMBER_DISPLAY_INDEX)) {
            "Sci" -> formatExponent(value, decimalPlaces, engineering = false)
            "Eng" -> formatExponent(value, decimalPlaces, engineering = true)
            else -> if (decimalPlaces == null) {
                value.stripTrailingZeros().toPlainString()
            } else {
                value.setScale(decimalPlaces, RoundingMode.HALF_UP).toPlainString()
            }
        }
    }

    fun formatNumber(value: Double): String =
        if (value.isFinite()) formatNumber(BigDecimal.valueOf(value)) else "ERR"

    private fun moveOption(direction: Int) {
        val setting = settings[selectedCategoryIndex]
        selectedOptions[selectedCategoryIndex] =
            (selectedOptions[selectedCategoryIndex] + direction).coerceIn(setting.options.indices)
        save()
    }

    private fun formatExponent(value: BigDecimal, decimalPlaces: Int?, engineering: Boolean): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            val zero = if (decimalPlaces == null || decimalPlaces == 0) "0" else {
                BigDecimal.ZERO.setScale(decimalPlaces).toPlainString()
            }
            return "${zero}E0"
        }

        val normalized = value.stripTrailingZeros()
        val scientificExponent = normalized.precision() - normalized.scale() - 1
        var exponent = if (engineering) Math.floorDiv(scientificExponent, 3) * 3 else scientificExponent
        val exponentStep = if (engineering) 3 else 1
        val threshold = if (engineering) BigDecimal("1000") else BigDecimal.TEN
        var mantissa = exponentMantissa(value, exponent, decimalPlaces)

        // Rounding can carry 9.99... to 10 (or 999... to 1000 in engineering mode).
        if (mantissa.abs() >= threshold) {
            exponent += exponentStep
            mantissa = exponentMantissa(value, exponent, decimalPlaces)
        }
        return "${mantissa.toPlainString()}E$exponent"
    }

    private fun exponentMantissa(value: BigDecimal, exponent: Int, decimalPlaces: Int?): BigDecimal {
        val shifted = value.movePointLeft(exponent)
        return if (decimalPlaces == null) {
            shifted.round(MathContext(FLOAT_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)).stripTrailingZeros()
        } else {
            shifted.setScale(decimalPlaces, RoundingMode.HALF_UP)
        }
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines.forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) return@forEach
                val settingIndex = settings.indexOfFirst { it.category == parts[0] }
                if (settingIndex < 0) return@forEach
                val optionIndex = settings[settingIndex].options.indexOf(parts[1])
                if (optionIndex >= 0) selectedOptions[settingIndex] = optionIndex
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            settings.indices.map { index -> "${settings[index].category}\t${selectedOption(index)}" }
        }
    }

    private const val NUMBER_DISPLAY_INDEX = 0
    private const val DECIMAL_DISPLAY_INDEX = 1
    private const val ANGLE_UNIT_INDEX = 2
    private const val COMPLEX_NUMBER_FORMAT_INDEX = 4
    private const val ANSWERS_INDEX = 6
    private const val FLOAT_SIGNIFICANT_DIGITS = 12
    private val NORMAL_SCIENTIFIC_THRESHOLD = BigDecimal("0.001")
}
