package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Discovers Cadence model classes under `app/Models` (and common variants) and
 * resolves each model's table name from an explicit override or convention.
 */
object ModelIndex {
    data class ModelInfo(
        val className: String,
        val table: String,
        val sourcePath: Path?,
    )

    /**
     * Matches `class User : Cadence<User>()`, multi-interface scaffolds
     * (`Cadence<User>(), Authenticatable, …`), and legacy `Extract`.
     */
    private val KOTLIN_CLASS =
        Regex(
            """(?:open\s+|abstract\s+)?class\s+(\w+)\s*(?:<[^>]*>)?\s*:\s*(?:Cadence|Extract)\s*(?:<[^>]*>)?""",
        )
    /** Matches `extends Cadence` / `extends Cadence<User>` (optional implements list). */
    private val JAVA_CLASS =
        Regex(
            """(?:public\s+|abstract\s+)?class\s+(\w+)\s+extends\s+(?:Cadence|Extract)\s*(?:<[^>]*>)?""",
        )
    private val TABLE_KT =
        Regex("""override\s+val\s+table\s*=\s*["']([^"']+)["']""")
    private val TABLE_JAVA =
        Regex(
            """(?:public\s+)?String\s+getTable\s*\(\s*\)\s*\{[\s\S]*?return\s*["']([^"']+)["']""",
        )

    fun modelDirs(projectRoot: Path): List<Path> =
        listOf(
            projectRoot.resolve("src/main/kotlin/app/Models"),
            projectRoot.resolve("src/main/kotlin/app/models"),
            projectRoot.resolve("src/main/java/app/Models"),
            projectRoot.resolve("src/main/java/app/models"),
            projectRoot.resolve("src/main/kotlin/Models"),
            projectRoot.resolve("src/main/java/Models"),
        )

    /** Broader scan so `app/domain/User.kt` still counts as a Cadence model. */
    fun modelSearchRoots(projectRoot: Path): List<Path> =
        (
            modelDirs(projectRoot) +
                listOf(
                    projectRoot.resolve("src/main/kotlin/app"),
                    projectRoot.resolve("src/main/java/app"),
                )
            ).distinct()

    fun collect(projectRoot: Path): List<ModelInfo> {
        val results = linkedMapOf<String, ModelInfo>()
        for (dir in modelSearchRoots(projectRoot)) {
            if (!dir.isDirectory()) continue
            Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    val name = file.fileName.toString()
                    if (!name.endsWith(".kt") && !name.endsWith(".java")) return@forEach
                    parseSource(Files.readString(file), file).forEach { info ->
                        results[info.className] = info
                    }
                }
            }
        }
        return results.values.sortedBy { it.className }
    }

    fun byClassName(projectRoot: Path): Map<String, ModelInfo> =
        collect(projectRoot).associateBy { it.className }

    /** Discovered Cadence model only — no convention fallback (avoids spamming unrelated locals). */
    fun find(projectRoot: Path, className: String): ModelInfo? {
        val simple = className.substringAfterLast('.')
        val by = byClassName(projectRoot)
        return by[simple] ?: by[className]
    }

    fun tableForClass(projectRoot: Path, className: String): String? {
        find(projectRoot, className)?.table?.let { return it }
        val simple = className.substringAfterLast('.')
        // Unknown class still pluralizes conventionally when it looks like a model call site
        // (e.g. `User.where("…")` before the model file is indexed).
        return if (simple.isNotEmpty() && simple[0].isUpperCase()) {
            CadenceTableNames.tableNameFor(simple)
        } else {
            null
        }
    }

    fun parseSource(source: String, sourcePath: Path? = null): List<ModelInfo> {
        val classes = mutableListOf<Pair<String, IntRange>>()
        KOTLIN_CLASS.findAll(source).forEach { classes += it.groupValues[1] to it.range }
        JAVA_CLASS.findAll(source).forEach { classes += it.groupValues[1] to it.range }
        if (classes.isEmpty()) return emptyList()

        return classes.mapIndexed { idx, (name, range) ->
            val end = classes.getOrNull(idx + 1)?.second?.first ?: source.length
            val body = source.substring(range.first, end)
            val table =
                TABLE_KT.find(body)?.groupValues?.get(1)
                    ?: TABLE_JAVA.find(body)?.groupValues?.get(1)
                    ?: CadenceTableNames.tableNameFor(name)
            ModelInfo(name, table, sourcePath)
        }
    }
}
