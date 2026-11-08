package net.cybercoretech.ghostdeck.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.GhostdeckJson
import net.cybercoretech.ghostdeck.model.validationError

sealed interface LoadResult {
    data class Loaded(val config: GhostdeckConfig) : LoadResult
    data object Missing : LoadResult

    /**
     * The file was unreadable, invalid, or from a newer schema. It was moved to [movedTo], never deleted;
     * [movedTo] is null when even the move failed (read-only or full storage) and the file is still in place.
     */
    data class Corrupt(val movedTo: File?, val reason: String) : LoadResult
}

/**
 * Persists the single [GhostdeckConfig] document as `dir/config.json`.
 *
 * Writes are atomic (tmp file, fsync, rename). Not thread-safe: the caller must
 * serialise access (the app uses one single-threaded dispatcher).
 */
class ConfigStore(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    val file: File = File(dir, "config.json")
    private val tmp: File = File(dir, "config.json.tmp")

    fun load(): LoadResult {
        if (!file.exists()) return LoadResult.Missing
        val reason = try {
            val config = GhostdeckJson.decodeFromString(GhostdeckConfig.serializer(), file.readText())
            if (config.schema != 1) "unsupported schema ${config.schema}"
            else config.validationError() ?: return LoadResult.Loaded(config)
        } catch (e: Exception) {
            // SerializationException, and IllegalArgumentException from GridSize/Dock validation.
            e.message ?: e.javaClass.simpleName
        }
        val aside = File(dir, "config.corrupt-${clock()}.json")
        val moved = try {
            Files.move(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING)
            aside
        } catch (_: Exception) {
            null // still report it: the caller starts fresh and shows the banner either way
        }
        return LoadResult.Corrupt(moved, reason)
    }

    fun save(config: GhostdeckConfig) {
        dir.mkdirs()
        FileOutputStream(tmp).use { out ->
            out.write(GhostdeckJson.encodeToString(GhostdeckConfig.serializer(), config).toByteArray())
            out.flush()
            out.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
