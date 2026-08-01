package net.amathboi.mi84mod.client.calculator

import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** Persistent FORMAT choices; unavailable future options remain visible but cannot be selected. */
object FormatSettingsMemory {
    data class FormatSetting(
        val options: List<String>,
        val defaultOption: String
    )

    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_format_settings.txt")
    private val settings = listOf(
        FormatSetting(listOf("RectGC", "PolarGC"), "RectGC"),
        FormatSetting(listOf("CoordOn", "CoordOff"), "CoordOn"),
        FormatSetting(listOf("GridOff", "GridDot", "GridLine"), "GridOff"),
        FormatSetting(listOf("AxesOn", "AxesOff"), "AxesOn"),
        FormatSetting(listOf("LabelOff", "LabelOn"), "LabelOff"),
        FormatSetting(listOf("ExprOn", "ExprOff"), "ExprOn")
    )
    private val selectedOptions = settings.map { setting ->
        setting.options.indexOf(setting.defaultOption)
    }.toMutableList()

    var selectedSettingIndex = 0
        private set

    init {
        load()
    }

    fun size(): Int = settings.size

    fun options(index: Int): List<String> = settings[index].options

    fun selectedOptionIndex(index: Int): Int = selectedOptions[index]

    fun selectedOption(index: Int): String = settings[index].options[selectedOptions[index]]

    fun optionAvailable(settingIndex: Int, optionIndex: Int): Boolean =
        !(settingIndex == COORDINATE_FORMAT_INDEX && optionIndex == POLAR_GC_OPTION_INDEX)

    fun selectPreviousSetting() {
        selectedSettingIndex = (selectedSettingIndex - 1).coerceAtLeast(0)
    }

    fun selectNextSetting() {
        selectedSettingIndex = (selectedSettingIndex + 1).coerceAtMost(settings.lastIndex)
    }

    fun selectPreviousOption() = moveOption(-1)

    fun selectNextOption() = moveOption(1)

    fun usesPolarCoordinates(): Boolean = selectedOption(COORDINATE_FORMAT_INDEX) == "PolarGC"

    fun showsCoordinates(): Boolean = selectedOption(COORDINATE_DISPLAY_INDEX) == "CoordOn"

    fun gridStyle(): String = selectedOption(GRID_INDEX)

    fun showsAxes(): Boolean = selectedOption(AXES_INDEX) == "AxesOn"

    fun showsLabels(): Boolean = selectedOption(LABEL_INDEX) == "LabelOn"

    fun showsExpressions(): Boolean = selectedOption(EXPRESSION_INDEX) == "ExprOn"

    private fun moveOption(direction: Int) {
        val setting = settings[selectedSettingIndex]
        val candidate = (selectedOptions[selectedSettingIndex] + direction)
            .coerceIn(setting.options.indices)
        if (!optionAvailable(selectedSettingIndex, candidate)) return
        selectedOptions[selectedSettingIndex] = candidate
        save()
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines.take(settings.size).forEachIndexed { index, savedOption ->
                val optionIndex = settings[index].options.indexOf(savedOption)
                if (optionIndex >= 0 && optionAvailable(index, optionIndex)) {
                    selectedOptions[index] = optionIndex
                }
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            settings.indices.map(::selectedOption)
        }
    }

    private const val COORDINATE_FORMAT_INDEX = 0
    private const val POLAR_GC_OPTION_INDEX = 1
    private const val COORDINATE_DISPLAY_INDEX = 1
    private const val GRID_INDEX = 2
    private const val AXES_INDEX = 3
    private const val LABEL_INDEX = 4
    private const val EXPRESSION_INDEX = 5
}
