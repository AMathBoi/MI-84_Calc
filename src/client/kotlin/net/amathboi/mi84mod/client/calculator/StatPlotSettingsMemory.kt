package net.amathboi.mi84mod.client.calculator

import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

enum class StatPlotType(val displayName: String) {
    SCATTER("Scatter"),
    LINE("Line"),
    HISTOGRAM("Hist"),
    MODIFIED_BOX("ModBox"),
    BOX("Box"),
    RELATIVE_FREQUENCY("RelFreq")
}

enum class StatPlotMark(val displayName: String) {
    OPEN_SQUARE("□"),
    PLUS("+"),
    DOT("•"),
    SMALL_DOT("·")
}

enum class StatPlotDataAxis {
    X,
    Y
}

data class StatPlotSetting(
    var enabled: Boolean,
    var type: StatPlotType,
    var xList: String,
    var yList: String,
    var mark: StatPlotMark,
    var dataAxis: StatPlotDataAxis,
    val color: Int
)

/** Persistent Plot1/2/3 configuration. Plot drawing is intentionally owned by a later change. */
object StatPlotSettingsMemory {
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_stat_plot_settings.txt")
    private val colors = listOf(
        0xFF55AAFF.toInt(),
        0xFFFF5555.toInt(),
        0xFF000000.toInt()
    )
    private val plots = colors.map { color ->
        StatPlotSetting(
            enabled = false,
            type = StatPlotType.SCATTER,
            xList = "L1",
            yList = "L2",
            mark = StatPlotMark.OPEN_SQUARE,
            dataAxis = StatPlotDataAxis.X,
            color = color
        )
    }.toMutableList()

    init {
        load()
    }

    fun size(): Int = plots.size

    fun plot(index: Int): StatPlotSetting = plots[index]

    fun typeRendersOnGraph(type: StatPlotType): Boolean =
        type == StatPlotType.SCATTER || type == StatPlotType.LINE

    fun setEnabled(index: Int, enabled: Boolean) {
        plots[index].enabled = enabled
        save()
    }

    fun setAllEnabled(enabled: Boolean) {
        plots.forEach { it.enabled = enabled }
        save()
    }

    fun cycleType(index: Int, direction: Int) {
        val values = StatPlotType.entries
        val selected = (plots[index].type.ordinal + direction).coerceIn(values.indices)
        plots[index].type = values[selected]
        save()
    }

    fun cycleMark(index: Int, direction: Int) {
        val values = StatPlotMark.entries
        val selected = (plots[index].mark.ordinal + direction).coerceIn(values.indices)
        plots[index].mark = values[selected]
        save()
    }

    fun cycleXList(index: Int, direction: Int) = cycleList(index, direction, xList = true)

    fun cycleYList(index: Int, direction: Int) = cycleList(index, direction, xList = false)

    fun cycleDataAxis(index: Int, direction: Int) {
        plots[index].dataAxis = if (direction > 0) StatPlotDataAxis.Y else StatPlotDataAxis.X
        save()
    }

    fun availableLists(): List<String> = CalculatorListMemory.names()

    private fun cycleList(index: Int, direction: Int, xList: Boolean) {
        val names = availableLists()
        if (names.isEmpty()) return
        val current = if (xList) plots[index].xList else plots[index].yList
        val currentIndex = names.indexOf(current).takeIf { it >= 0 } ?: 0
        val selected = (currentIndex + direction).coerceIn(names.indices)
        if (xList) plots[index].xList = names[selected] else plots[index].yList = names[selected]
        save()
    }

    private fun load() {
        CalculatorPersistence.load(memoryFile) { savedLines ->
            savedLines.take(plots.size).forEachIndexed { index, line ->
                val parts = line.split('\t')
                if (parts.size !in 5..6) return@forEachIndexed
                plots[index].enabled = parts[0] == "On"
                StatPlotType.entries.firstOrNull { it.name == parts[1] }?.let {
                    plots[index].type = it
                }
                parts[2].takeIf { CalculatorListMemory.value(it) != null }?.let {
                    plots[index].xList = it
                }
                parts[3].takeIf { CalculatorListMemory.value(it) != null }?.let {
                    plots[index].yList = it
                }
                StatPlotMark.entries.firstOrNull { it.name == parts[4] }?.let {
                    plots[index].mark = it
                }
                parts.getOrNull(5)?.let { savedAxis ->
                    StatPlotDataAxis.entries.firstOrNull { it.name == savedAxis }?.let {
                        plots[index].dataAxis = it
                    }
                }
            }
        }
    }

    private fun save() {
        CalculatorPersistence.save(memoryFile) {
            plots.map { plot ->
                listOf(
                    if (plot.enabled) "On" else "Off",
                    plot.type.name,
                    plot.xList,
                    plot.yList,
                    plot.mark.name,
                    plot.dataAxis.name
                ).joinToString("\t")
            }
        }
    }
}
