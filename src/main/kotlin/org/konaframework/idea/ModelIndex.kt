package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
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

    private val KOTLIN_CLASS =
        Regex("""(?:open\s+|abstract\s+)?class\s+(\w+)\s*(?:<[^>]*>)?\s*:\s*(?:Cadence|Extract)\b""")
    private val JAVA_CLASS =
        Regex("""(?:public\s+|abstract\s+)?class\s+(\w+)\s+extends\s+(?:Cadence|Extract)\b""")
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

    fun collect(projectRoot: Path): List<ModelInfo> {
        val results = linkedMapOf<String, ModelInfo>()
        for (dir in modelDirs(projectRoot)) {
            if (!Files.isDirectory(dir)) continue
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

    fun tableForClass(projectRoot: Path, className: String): String? {
        val simple = className.substringAfterLast('.')
        return byClassName(projectRoot)[simple]?.table
            ?: byClassName(projectRoot)[className]?.table
            ?: run {
                // Unknown class still pluralizes conventionally when it looks like a model call site.
                if (simple.isNotEmpty() && simple[0].isUpperCase()) CadenceTableNames.tableNameFor(simple) else null
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
