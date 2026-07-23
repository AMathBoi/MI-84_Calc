package net.amathboi.mi84mod.client.calculator

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.slf4j.LoggerFactory

/** Shared, logged persistence that never truncates the last good file before replacement. */
object CalculatorPersistence {
    private val logger = LoggerFactory.getLogger("mi84_calc/persistence")

    fun load(path: Path, consume: (List<String>) -> Unit) {
        if (!Files.exists(path)) return
        try {
            consume(Files.readAllLines(path, StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            logger.error("Failed to load calculator state from {}", path, exception)
        }
    }

    fun save(path: Path, lines: () -> List<String>) {
        val parent = path.toAbsolutePath().parent
        var temporaryFile: Path? = null
        try {
            Files.createDirectories(parent)
            temporaryFile = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
            Files.write(temporaryFile, lines(), StandardCharsets.UTF_8)
            FileChannel.open(temporaryFile, StandardOpenOption.WRITE).use { channel ->
                channel.force(true)
            }
            try {
                Files.move(
                    temporaryFile,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                logger.warn("Atomic replacement is unavailable for {}; using a regular replacement", path)
                Files.move(temporaryFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
            temporaryFile = null
        } catch (exception: Exception) {
            logger.error("Failed to save calculator state to {}", path, exception)
        } finally {
            temporaryFile?.let { candidate ->
                try {
                    Files.deleteIfExists(candidate)
                } catch (cleanupException: Exception) {
                    logger.warn("Failed to remove temporary calculator state file {}", candidate, cleanupException)
                }
            }
        }
    }
}
