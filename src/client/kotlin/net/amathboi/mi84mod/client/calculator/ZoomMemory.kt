package net.amathboi.mi84mod.client.calculator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader

/** Zoom Previous, ZoomSto/ZoomRcl, and the custom fractional denominator. */
object ZoomMemory {
    private val memoryFile: Path =
        FabricLoader.getInstance().configDir.resolve("mi84_calc_zoom_memory.txt")
    private val denominatorChoices = listOf(2, 3, 4, 5, 8, 10)

    private var previousWindow: List<String>? = null
    private var storedWindow: List<String>? = null
    private var denominatorIndex = 0

    init {
        load()
    }

    fun rememberPrevious(window: List<String>) {
        previousWindow = window.toList()
    }

    fun restorePrevious(): Boolean {
        val previous = previousWindow ?: return false
        val current = WindowSettingsMemory.snapshot()
        WindowSettingsMemory.restore(previous)
        previousWindow = current
        return true
    }

    fun storeCurrent() {
        storedWindow = WindowSettingsMemory.snapshot()
        save()
    }

    fun recallStored(): Boolean {
        val stored = storedWindow ?: return false
        rememberPrevious(WindowSettingsMemory.snapshot())
        WindowSettingsMemory.restore(stored)
        return true
    }

    fun denominators(): List<Int> = denominatorChoices

    fun selectedDenominatorIndex(): Int = denominatorIndex

    fun selectedDenominator(): Int = denominatorChoices[denominatorIndex]

    fun selectDenominator(index: Int) {
        denominatorIndex = index.coerceIn(denominatorChoices.indices)
        save()
    }

    private fun load() {
        if (!Files.exists(memoryFile)) return
        runCatching {
            val lines = Files.readAllLines(memoryFile, StandardCharsets.UTF_8)
            lines.firstOrNull()?.toIntOrNull()?.let { savedDenominator ->
                denominatorChoices.indexOf(savedDenominator)
                    .takeIf { it >= 0 }
                    ?.let { denominatorIndex = it }
            }
            if (lines.size >= WindowSettingsMemory.size() + 1) {
                storedWindow = lines.drop(1).take(WindowSettingsMemory.size())
            }
        }
    }

    private fun save() {
        runCatching {
            Files.createDirectories(memoryFile.parent)
            Files.write(
                memoryFile,
                listOf(selectedDenominator().toString()) + storedWindow.orEmpty(),
                StandardCharsets.UTF_8
            )
        }
    }
}
