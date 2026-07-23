package net.amathboi.mi84mod.client.calculator

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import net.fabricmc.loader.impl.FabricLoaderImpl

object TestFabricEnvironment {
    val configDir: Path = Files.createTempDirectory("mi84-calculator-test")

    init {
        configureFabricLoaderPath("gameDir", configDir)
        configureFabricLoaderPath("configDir", configDir)
        Runtime.getRuntime().addShutdownHook(Thread {
            if (Files.exists(configDir)) {
                Files.walk(configDir).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        })
    }

    fun configure() = Unit

    private fun configureFabricLoaderPath(fieldName: String, path: Path) {
        FabricLoaderImpl::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(FabricLoaderImpl.INSTANCE, path)
        }
    }
}
