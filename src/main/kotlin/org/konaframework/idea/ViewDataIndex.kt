package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Maps Crema view names to data keys passed from controllers
 * (`Crema.view(…, "welcome", mapOf("name" to …))` / `view("welcome", mapOf(…))`).
 */
object ViewDataIndex {
    data class KeyHit(
        val key: String,
        val sourcePath: Path,
        val offset: Int,
    )

    private val VIEW_CALL =
        Regex(
            """(?:Crema\.view|Brew\.view|\.make|\.render|view)\s*\(\s*[^,)]*,\s*["']([^"']+)["']\s*,\s*mapOf\s*\(([^)]*)\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val VIEW_CALL_TWO_ARG =
        Regex(
            """(?:^|[^\w.])view\s*\(\s*["']([^"']+)["']\s*,\s*mapOf\s*\(([^)]*)\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val MAP_KEY =
        Regex("""["']([^"']+)["']\s*to\b""")

    fun keysForView(projectRoot: Path, viewName: String): List<KeyHit> {
        val hits = mutableListOf<KeyHit>()
        for (dir in controllerDirs(projectRoot)) {
            if (!Files.isDirectory(dir)) continue
            Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    val name = file.fileName.toString()
                    if (!name.endsWith(".kt") && !name.endsWith(".java")) return@forEach
                    val source = runCatching { Files.readString(file) }.getOrNull() ?: return@forEach
                    hits += parseKeys(source, file, viewName)
                }
            }
        }
        return hits
    }

    fun firstKey(projectRoot: Path, viewName: String, key: String): KeyHit? =
        keysForView(projectRoot, viewName).firstOrNull { it.key == key }

    fun parseKeys(source: String, sourcePath: Path, viewName: String): List<KeyHit> {
        val out = mutableListOf<KeyHit>()
        for (pattern in listOf(VIEW_CALL, VIEW_CALL_TWO_ARG)) {
            pattern.findAll(source).forEach { m ->
                if (m.groupValues[1] != viewName) return@forEach
                val mapBody = m.groupValues[2]
                val mapStart = m.groups[2]?.range?.first ?: return@forEach
                MAP_KEY.findAll(mapBody).forEach { km ->
                    val key = km.groupValues[1]
                    val keyOffset = mapStart + (km.groups[1]?.range?.first ?: return@forEach)
                    out += KeyHit(key, sourcePath, keyOffset)
                }
            }
        }
        return out
    }

    private fun controllerDirs(projectRoot: Path): List<Path> =
        listOf(
            projectRoot.resolve("src/main/kotlin/app/http/controllers"),
            projectRoot.resolve("src/main/java/app/http/controllers"),
            projectRoot.resolve("src/main/kotlin/app/controllers"),
            projectRoot.resolve("src/main/java/app/controllers"),
        )
}
