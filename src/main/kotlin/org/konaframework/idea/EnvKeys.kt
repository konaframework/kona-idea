package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Env key index for Kona config and `.env`.
 *
 * Keys are read from `Env.get` / `Env.getOrDefault` / `Env.require` under
 * `src/main/{kotlin,java}` (the scaffold keeps those calls in `config/`) and
 * from `.env` plus `.env.*` templates. Navigation always targets the project
 * `.env` assignment.
 */
object EnvKeys {
    private val KEY = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val USAGE =
        Regex(
            """(?<![\w.])Env\.(?:getOrDefault|get|require)\s*\(\s*(?:key\s*=\s*)?(['"])([A-Za-z_][A-Za-z0-9_]*)\1""",
        )
    private val CALL_BEFORE =
        Regex("""(?<![\w.])Env\.(?:getOrDefault|get|require)\s*\(\s*(?:key\s*=\s*)?$""")
    private val DECL_LINE =
        Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=""")
    private val KEY_LINE =
        Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)?$""")
    private val INTERPOLATION =
        Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)?$""")

    private val SOURCE_ROOTS = listOf("src/main/kotlin", "src/main/java")
    private val SKIP_SUFFIXES = listOf(".encrypted", ".bak", ".backup", ".save")

    data class Use(val name: String, val start: Int, val end: Int)

    data class Decl(val name: String, val line: Int, val start: Int, val end: Int)

    /** Caret is on a `.env` key, or inside `${…}` on the value side. */
    data class DotenvEdit(val prefix: String, val interpolating: Boolean)

    fun usages(text: CharSequence): List<Use> =
        USAGE.findAll(text).map { match ->
            val range = match.groups[2]!!.range
            Use(match.groupValues[2], range.first, range.last + 1)
        }.toList()

    /** Key whose content contains [offset] (not the surrounding quotes). */
    fun usageAt(text: CharSequence, offset: Int): Use? {
        if (text.isEmpty() || offset < 0 || offset > text.length) return null
        val probe = offset.coerceAtMost(text.length - 1)
        var quoteAt = probe
        while (quoteAt >= 0 && text[quoteAt] != '"' && text[quoteAt] != '\'' && text[quoteAt] != '\n') {
            quoteAt--
        }
        if (quoteAt < 0 || text[quoteAt] == '\n') return null
        val quote = text[quoteAt]
        var end = quoteAt + 1
        while (end < text.length && text[end] != quote && text[end] != '\n') end++
        if (offset < quoteAt + 1 || offset > end) return null
        val name = text.subSequence(quoteAt + 1, end).toString()
        if (!KEY.matches(name)) return null
        val before = text.subSequence(0, quoteAt).toString().takeLast(120)
        if (!CALL_BEFORE.containsMatchIn(before)) return null
        return Use(name, quoteAt + 1, end)
    }

    /** Prefix inside `Env.get|getOrDefault|require(`, or null when the caret is elsewhere. */
    fun lookupPrefixAt(text: CharSequence, offset: Int): String? {
        if (offset < 0 || offset > text.length) return null
        var i = offset - 1
        while (i >= 0 && isKeyChar(text[i])) i--
        if (i < 0 || (text[i] != '"' && text[i] != '\'')) return null
        val before = text.subSequence(0, i).toString().takeLast(120)
        if (!CALL_BEFORE.containsMatchIn(before)) return null
        return text.subSequence(i + 1, offset).toString()
    }

    fun dotenvKeyPrefixAt(text: CharSequence, offset: Int): DotenvEdit? {
        if (offset < 0 || offset > text.length) return null
        val lineStart =
            if (offset == 0) {
                0
            } else {
                val nl = text.lastIndexOf('\n', offset - 1)
                if (nl < 0) 0 else nl + 1
            }
        val line = text.subSequence(lineStart, offset).toString().trimEnd('\r')
        if (line.trimStart().startsWith("#")) return null
        val interpolation = INTERPOLATION.find(line)
        if (interpolation != null) {
            return DotenvEdit(interpolation.groupValues[1], interpolating = true)
        }
        if (line.contains('=')) return null
        val match = KEY_LINE.matchEntire(line) ?: return null
        return DotenvEdit(match.groupValues[1], interpolating = false)
    }

    /** Assignments in one dotenv document. Later lines win in [declaration]. */
    fun declarations(text: String): List<Decl> {
        val out = mutableListOf<Decl>()
        var index = 0
        var lineNo = 0
        while (index <= text.length) {
            val nl = text.indexOf('\n', index).let { if (it < 0) text.length else it }
            var line = text.substring(index, nl)
            val cr = line.endsWith('\r')
            if (cr) line = line.dropLast(1)
            if (!line.trimStart().startsWith("#")) {
                val match = DECL_LINE.find(line)
                if (match != null) {
                    val name = match.groupValues[1]
                    val startInLine = match.range.first + match.value.indexOf(name)
                    val start = index + startInLine
                    out += Decl(name, lineNo, start, start + name.length)
                }
            }
            if (nl >= text.length) break
            index = nl + 1
            lineNo++
        }
        return out
    }

    fun declaration(text: String, name: String): Decl? =
        declarations(text).lastOrNull { it.name == name }

    fun referencedKeys(root: Path): List<String> {
        val keys = linkedSetOf<String>()
        for (relative in SOURCE_ROOTS) {
            val base = root.resolve(relative)
            if (!Files.isDirectory(base)) continue
            Files.walk(base).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    val fileName = file.fileName.toString()
                    if (!fileName.endsWith(".kt") && !fileName.endsWith(".java")) return@forEach
                    val text = runCatching { Files.readString(file) }.getOrNull() ?: return@forEach
                    usages(text).forEach { keys += it.name }
                }
            }
        }
        return keys.toList()
    }

    /** `.env` first, then other `.env.*` templates (not backups or encrypted copies). */
    fun envFiles(root: Path): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        val out = mutableListOf<Path>()
        val primary = root.resolve(".env")
        if (Files.isRegularFile(primary)) out.add(primary)
        Files.newDirectoryStream(root).use { stream ->
            val extras = mutableListOf<Path>()
            for (path in stream) {
                val name = path.fileName.toString()
                if (name == ".env" || !name.startsWith(".env.")) continue
                if (!Files.isRegularFile(path)) continue
                if (SKIP_SUFFIXES.any { name.endsWith(it) }) continue
                extras.add(path)
            }
            extras.sortBy { it.fileName.toString() }
            out.addAll(extras)
        }
        return out
    }

    fun declaredKeys(root: Path): List<String> {
        val keys = linkedSetOf<String>()
        for (file in envFiles(root)) {
            val text = runCatching { Files.readString(file) }.getOrNull() ?: continue
            declarations(text).forEach { keys += it.name }
        }
        return keys.toList()
    }

    /** Keys offered inside `Env.get(…)` in config (and any other Env lookup). */
    fun completionKeys(root: Path): List<String> {
        val keys = linkedSetOf<String>()
        keys += declaredKeys(root)
        keys += referencedKeys(root)
        return keys.sorted()
    }

    fun matching(keys: Collection<String>, prefix: String): List<String> =
        keys.filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }.sorted()

    fun isDotEnvFileName(name: String): Boolean = name == ".env"

    fun envFile(root: Path): Path = root.resolve(".env")

    private fun isKeyChar(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
