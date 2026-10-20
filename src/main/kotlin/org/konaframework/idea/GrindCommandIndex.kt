package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Built-in grind commands plus custom commands discovered from
 * `META-INF/kona/commands` and `ConsoleCommand` sources under `app/console`.
 */
object GrindCommandIndex {
    data class Command(
        val name: String,
        val summary: String,
        val custom: Boolean = false,
    )

    /** Built-ins that exist on `ListCommand` but are not Grind run-config presets. */
    val CALLABLE_EXTRAS: List<GrindCommands.Command> =
        listOf(
            GrindCommands.Command("install", "Add a package dependency and register providers"),
            GrindCommands.Command("down", "Put the application into maintenance mode"),
            GrindCommands.Command("up", "Bring the application out of maintenance mode"),
            GrindCommands.Command("ide:lsp", "Print kona-lsp setup help"),
        )

    private val KT_SIGNATURE =
        Regex("""(?:override\s+)?val\s+signature(?:\s*:\s*String)?\s*=\s*["']([^"']+)["']""")
    private val JAVA_SIGNATURE =
        Regex("""getSignature\s*\(\s*\)\s*\{[\s\S]{0,500}?return\s*["']([^"']+)["']""")
    private val KT_DESCRIPTION =
        Regex("""(?:override\s+)?val\s+description(?:\s*:\s*String)?\s*=\s*["']([^"']*)["']""")
    private val JAVA_DESCRIPTION =
        Regex("""getDescription\s*\(\s*\)\s*\{[\s\S]{0,500}?return\s*["']([^"']*)["']""")
    private val CONSOLE_BASE =
        Regex("""(?::\s*ConsoleCommand\b|extends\s+ConsoleCommand\b)""")

    fun commands(projectRoot: Path): List<Command> {
        val out = linkedMapOf<String, Command>()
        fun put(name: String, summary: String, custom: Boolean) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            out.putIfAbsent(trimmed, Command(trimmed, summary, custom))
        }
        GrindCommands.BUILTIN.forEach { put(it.name, it.summary, custom = false) }
        CALLABLE_EXTRAS.forEach { put(it.name, it.summary, custom = false) }
        for (cmd in customCommands(projectRoot)) {
            put(cmd.name, cmd.summary, custom = true)
        }
        return out.values.toList()
    }

    fun names(projectRoot: Path): List<String> = commands(projectRoot).map { it.name }

    fun customCommands(projectRoot: Path): List<Command> {
        val out = linkedMapOf<String, Command>()
        val fqcns = metaInfCommands(projectRoot)
        for (fqcn in fqcns) {
            val source = sourceForFqcn(projectRoot, fqcn) ?: continue
            parseCommandSource(source)?.let { out.putIfAbsent(it.name, it) }
        }
        for (dir in consoleDirs(projectRoot)) {
            if (!dir.isDirectory()) continue
            Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    val name = file.fileName.toString()
                    if (!name.endsWith(".kt") && !name.endsWith(".java")) return@forEach
                    val source = Files.readString(file)
                    if (!CONSOLE_BASE.containsMatchIn(source)) return@forEach
                    parseCommandSource(source)?.let { out.putIfAbsent(it.name, it) }
                }
            }
        }
        return out.values.toList()
    }

    fun metaInfCommands(projectRoot: Path): List<String> {
        val files =
            listOf(
                projectRoot.resolve("src/main/resources/META-INF/kona/commands"),
                projectRoot.resolve("src/main/resources/META-INF/kona/commands.txt"),
            )
        val names = linkedSetOf<String>()
        for (file in files) {
            if (!Files.isRegularFile(file)) continue
            Files.readString(file).lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                names += line
            }
        }
        return names.toList()
    }

    fun parseCommandSource(source: String): Command? {
        val signature =
            KT_SIGNATURE.find(source)?.groupValues?.get(1)
                ?: JAVA_SIGNATURE.find(source)?.groupValues?.get(1)
                ?: return null
        val name = commandNameFromSignature(signature)
        if (name.isEmpty()) return null
        val summary =
            KT_DESCRIPTION.find(source)?.groupValues?.get(1)
                ?: JAVA_DESCRIPTION.find(source)?.groupValues?.get(1)
                ?: "custom command"
        return Command(name, summary.ifBlank { "custom command" }, custom = true)
    }

    fun commandNameFromSignature(signature: String): String =
        signature.trim().substringBefore(' ').substringBefore('{').trim()

    fun consoleDirs(projectRoot: Path): List<Path> =
        listOf(
            projectRoot.resolve("src/main/kotlin/app/console"),
            projectRoot.resolve("src/main/kotlin/app/Console"),
            projectRoot.resolve("src/main/java/app/console"),
            projectRoot.resolve("src/main/java/app/Console"),
        )

    fun sourceForFqcn(projectRoot: Path, fqcn: String): String? {
        val rel = fqcn.replace('.', '/')
        val candidates =
            listOf(
                projectRoot.resolve("src/main/kotlin/$rel.kt"),
                projectRoot.resolve("src/main/java/$rel.java"),
            )
        val file = candidates.firstOrNull { Files.isRegularFile(it) } ?: return null
        return Files.readString(file)
    }
}
