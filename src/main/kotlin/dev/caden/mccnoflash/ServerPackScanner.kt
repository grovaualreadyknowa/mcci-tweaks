package dev.caden.mccnoflash

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import net.fabricmc.loader.api.FabricLoader

object ServerPackScanner {
    private val TS_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())
    private const val MAX_MATCHES = 30

    @JvmStatic
    fun scanLatest(state: MccNoFlashState): ScanResult {
        val gameDir = FabricLoader.getInstance().gameDir
        val serverPackDir = gameDir.resolve("server-resource-packs")
        val downloadsDir = gameDir.resolve("downloads")

        val candidate = (candidatesIn(serverPackDir, depth = 1) + candidatesIn(downloadsDir, depth = 4))
            .maxByOrNull { lastModifiedMillis(it.path) }
            ?: return ScanResult.noPackFound(gameDir)

        val keywords = state.discoveryKeywords()
        val matches = try {
            ZipFile(candidate.path.toFile()).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && containsAny(it.name.lowercase(Locale.ROOT), keywords) }
                    .map { it.name }
                    .toList()
            }
        } catch (e: IOException) {
            return ScanResult.error(candidate.searchRoot, candidate.path, e.message)
        }

        val ordered = state.sortByRelevance(matches).take(MAX_MATCHES)
        return ScanResult.success(candidate.searchRoot, candidate.path, ordered, readTime(candidate.path))
    }

    private fun candidatesIn(directory: Path, depth: Int): List<PackCandidate> {
        if (!directory.isDirectory()) return emptyList()
        return try {
            Files.walk(directory, depth).use { stream ->
                stream.toList()
                    .filter { it != directory && it.isRegularFile() && isLikelyZipFile(it) }
                    .map { PackCandidate(directory, it) }
            }
        } catch (e: IOException) {
            MccNoFlashMod.LOGGER.warn("Could not list server packs in {}", directory, e)
            emptyList()
        }
    }

    private fun isLikelyZipFile(path: Path): Boolean {
        if (path.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip")) return true

        val header = ByteArray(4)
        try {
            Files.newInputStream(path).use { input ->
                if (input.read(header) < 4) return false
            }
        } catch (_: IOException) {
            return false
        }

        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2].toInt() in setOf(0x03, 0x05, 0x07) &&
            header[3].toInt() in setOf(0x04, 0x06, 0x08)
    }

    private fun lastModifiedMillis(path: Path): Long = try {
        Files.getLastModifiedTime(path).toMillis()
    } catch (_: IOException) {
        Long.MIN_VALUE
    }

    private fun readTime(path: Path): String = try {
        TS_FORMAT.format(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()))
    } catch (_: IOException) {
        "unknown"
    }

    private fun containsAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { it.isNotBlank() && text.contains(it) }

    data class ScanResult(
        val ok: Boolean,
        val packDirectory: Path,
        val scannedZip: Path?,
        val lastModified: String?,
        val failure: String?,
        val matches: List<String>
    ) {
        fun summary(): String = if (ok) "scan ok: ${matches.size} candidate files" else "scan failed: $failure"

        companion object {
            fun noPackFound(gameDir: Path) = ScanResult(
                false,
                gameDir,
                null,
                null,
                "no server pack zip found in server-resource-packs or downloads",
                emptyList()
            )

            fun error(packDirectory: Path, scannedZip: Path, failure: String?) =
                ScanResult(false, packDirectory, scannedZip, null, failure, emptyList())

            fun success(packDirectory: Path, scannedZip: Path, matches: List<String>, lastModified: String) =
                ScanResult(true, packDirectory, scannedZip, lastModified, null, matches)
        }
    }

    private data class PackCandidate(val searchRoot: Path, val path: Path)
}
