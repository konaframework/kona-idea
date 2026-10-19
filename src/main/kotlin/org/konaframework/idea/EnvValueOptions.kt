package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Known option-like values for Kona env keys, plus project-derived names
 * from `config/Database` / `config/Filesystems` / logging channel maps.
 */
object EnvValueOptions {
    data class Option(
        val value: String,
        val typeText: String,
        val tailText: String? = null,
    )

    /** Caret is on the value side of a dotenv assignment (not `${…}`). */
    data class DotenvValueEdit(val key: String, val prefix: String)

    /** Caret is inside the default string of `Env.getOrDefault("KEY", "…")`. */
    data class DefaultValueEdit(val key: String, val prefix: String)

    private val SOURCE_ROOTS = listOf("src/main/kotlin", "src/main/java")
    private val SKIP_ENV_SUFFIXES = listOf(".encrypted", ".bak", ".backup", ".save")

    private val KT_MAP_ENTRY =
        Regex(""""([A-Za-z_][A-Za-z0-9_]*)"\s+to\s+mapOf\s*\(""")
    private val JAVA_CONNECTIONS_PUT =
        Regex("""connections\.put\s*\(\s*"([A-Za-z_][A-Za-z0-9_]*)"\s*,""")
    private val JAVA_DISKS_PUT =
        Regex("""(?:disks|Disks)\.put\s*\(\s*"([A-Za-z_][A-Za-z0-9_]*)"\s*,""")
    private val JAVA_CHANNELS_PUT =
        Regex("""(?:channels|Channels)\.put\s*\(\s*"([A-Za-z_][A-Za-z0-9_]*)"\s*,""")
    private val LOGGING_CHANNEL_SET =
        Regex("""logging\.channels\.([A-Za-z_][A-Za-z0-9_]*)\.""")

    private val GET_OR_DEFAULT_KEY =
        Regex(
            """(?<![\w.])Env\.getOrDefault\s*\(\s*(?:key\s*=\s*)?(['"])([A-Za-z_][A-Za-z0-9_]*)\1\s*,\s*(?:default\s*=\s*)?$""",
        )

    private val DECL_BEFORE_EQ =
        Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=""")

    private val BOOLEAN_KEYS =
        setOf(
            "APP_DEBUG",
            "LOG_TO_FILE",
            "LOG_REQUESTS",
            "SESSION_ENCRYPT",
            "SESSION_HTTP_ONLY",
            "DB_QUOTE_IDENTIFIERS",
        )

    private val DB_CONNECTIONS =
        options(
            "db connection",
            "main", "h2", "sqlite", "mysql", "mariadb", "pgsql", "sqlsrv", "oracle",
        )

    private val LOG_DRIVERS =
        options(
            "log driver",
            "stack", "single", "daily", "stderr", "stdout", "console", "syslog", "errorlog", "null",
        )

    private val SESSION_DRIVERS =
        options("session driver", "array", "memory", "cookie", "file", "redis")

    private val CACHE_STORES =
        options("cache store", "array", "memory", "file", "redis")

    private val QUEUE_CONNECTIONS =
        options("queue connection", "sync", "array", "memory", "redis", "database")

    private val MAIL_MAILERS =
        options("mail transport", "log", "array", "smtp")

    private val HASH_DRIVERS =
        options("hash driver", "bcrypt", "argon2", "argon2id")

    private val FILESYSTEM_DISKS =
        options("filesystem disk", "local", "public")

    private val APP_ENVS =
        options("app env", "local", "development", "staging", "production", "testing")

    private val BOOLEANS =
        options("boolean", "true", "false", "yes", "no", "1", "0")

    private val SAME_SITE =
        options("same-site", "Lax", "Strict", "None")

    private val LOG_LEVELS =
        options(
            "log level",
            "emergency", "alert", "critical", "error", "warning", "warn", "notice", "info", "debug",
        )

    /** Static catalog only (no project merge). */
    val STATIC: Map<String, List<Option>> =
        buildMap {
            put("DB_CONNECTION", DB_CONNECTIONS)
            put("LOG_CHANNEL", LOG_DRIVERS)
            put("LOG_STACK", LOG_DRIVERS)
            put("SESSION_DRIVER", SESSION_DRIVERS)
            put("CACHE_STORE", CACHE_STORES)
            put("CACHE_DRIVER", CACHE_STORES)
            put("QUEUE_CONNECTION", QUEUE_CONNECTIONS)
            put("MAIL_MAILER", MAIL_MAILERS)
            put("HASH_DRIVER", HASH_DRIVERS)
            put("FILESYSTEM_DISK", FILESYSTEM_DISKS)
            put("APP_ENV", APP_ENVS)
            put("SESSION_SAME_SITE", SAME_SITE)
            put("LOG_LEVEL", LOG_LEVELS)
            for (key in BOOLEAN_KEYS) {
                put(key, BOOLEANS)
            }
        }

    fun optionsFor(key: String, root: Path?): List<Option> {
        val upper = key.uppercase()
        val static = STATIC[upper].orEmpty()
        if (root == null) return static
        return when (upper) {
            "DB_CONNECTION" -> mergeNamed(static, "db connection", projectConnectionNames(root))
            "FILESYSTEM_DISK" -> mergeNamed(static, "filesystem disk", projectDiskNames(root))
            "LOG_CHANNEL", "LOG_STACK" ->
                mergeNamed(static, "log channel", projectLogChannelNames(root))
            else -> static
        }
    }

    fun matching(options: Collection<Option>, prefix: String): List<Option> =
        options
            .filter { prefix.isEmpty() || it.value.startsWith(prefix, ignoreCase = true) }
            .distinctBy { it.value.lowercase() }
            .sortedBy { it.value.lowercase() }

    fun dotenvValuePrefixAt(text: CharSequence, offset: Int): DotenvValueEdit? {
        if (offset < 0 || offset > text.length) return null
        val lineStart =
            if (offset == 0) {
                0
            } else {
                val nl = text.lastIndexOf('\n', offset - 1)
                if (nl < 0) 0 else nl + 1
            }
        val lineEnd =
            text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        val fullLine = text.subSequence(lineStart, lineEnd).toString().trimEnd('\r')
        if (fullLine.trimStart().startsWith("#")) return null
        val eq = fullLine.indexOf('=')
        if (eq <= 0) return null
        val caretInLine = offset - lineStart
        if (caretInLine <= eq) return null
        val beforeCaret = fullLine.substring(0, caretInLine)
        if (insideInterpolation(beforeCaret)) return null
        val keyMatch = DECL_BEFORE_EQ.find(fullLine.substring(0, eq + 1)) ?: return null
        val key = keyMatch.groupValues[1]
        var valuePrefix = fullLine.substring(eq + 1, caretInLine)
        if (valuePrefix.startsWith('"') || valuePrefix.startsWith('\'')) {
            valuePrefix = valuePrefix.drop(1)
        }
        return DotenvValueEdit(key, valuePrefix)
    }

    fun getOrDefaultValuePrefixAt(text: CharSequence, offset: Int): DefaultValueEdit? {
        if (offset < 0 || offset > text.length) return null
        var i = offset - 1
        while (i >= 0 && text[i] != '"' && text[i] != '\'' && text[i] != '\n') i--
        if (i < 0 || text[i] == '\n') return null
        val before = text.subSequence(0, i).toString().takeLast(160)
        val match = GET_OR_DEFAULT_KEY.find(before) ?: return null
        return DefaultValueEdit(match.groupValues[2], text.subSequence(i + 1, offset).toString())
    }

    /** `.env`, `.env.example`, `.env.local`, … (not backups / encrypted copies). */
    fun isDotEnvFamilyName(name: String): Boolean {
        if (name == ".env") return true
        if (!name.startsWith(".env.")) return false
        return SKIP_ENV_SUFFIXES.none { name.endsWith(it) }
    }

    fun parseDatabaseConnections(text: String): List<String> {
        val names = linkedSetOf<String>()
        KT_MAP_ENTRY.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name !in SKIP_MAP_KEYS) names += name
        }
        JAVA_CONNECTIONS_PUT.findAll(text).forEach { names += it.groupValues[1] }
        return names.toList()
    }

    fun parseFilesystemDisks(text: String): List<String> {
        val names = linkedSetOf<String>()
        KT_MAP_ENTRY.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name !in SKIP_DISK_KEYS) names += name
        }
        JAVA_DISKS_PUT.findAll(text).forEach { names += it.groupValues[1] }
        return names.toList()
    }

    fun parseLoggingChannels(text: String): List<String> {
        val names = linkedSetOf<String>()
        LOGGING_CHANNEL_SET.findAll(text).forEach { names += it.groupValues[1] }
        JAVA_CHANNELS_PUT.findAll(text).forEach { names += it.groupValues[1] }
        // Kotlin map under "channels" — same entry shape as disks
        KT_MAP_ENTRY.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (name !in SKIP_CHANNEL_KEYS) names += name
        }
        return names.toList()
    }

    fun projectConnectionNames(root: Path): List<String> =
        readConfigTexts(root, "Database").flatMap { parseDatabaseConnections(it) }.distinct()

    fun projectDiskNames(root: Path): List<String> =
        readConfigTexts(root, "Filesystems").flatMap { parseFilesystemDisks(it) }.distinct()

    fun projectLogChannelNames(root: Path): List<String> =
        readConfigTexts(root, "Logging", "Log").flatMap { parseLoggingChannels(it) }.distinct()

    private fun readConfigTexts(root: Path, vararg basenames: String): List<String> {
        val out = mutableListOf<String>()
        for (relative in SOURCE_ROOTS) {
            val base = root.resolve(relative)
            if (!Files.isDirectory(base)) continue
            for (name in basenames) {
                for (ext in listOf("kt", "java")) {
                    val candidates =
                        listOf(
                            base.resolve("config").resolve("$name.$ext"),
                            base.resolve("$name.$ext"),
                        )
                    for (file in candidates) {
                        if (file.isRegularFile()) {
                            runCatching { Files.readString(file) }.getOrNull()?.let { out += it }
                        }
                    }
                }
            }
            // Walk config/ for Java package layout (config/Database.java already covered)
            val configDir = base.resolve("config")
            if (Files.isDirectory(configDir)) {
                Files.list(configDir).use { stream ->
                    stream.filter { it.isRegularFile() }.forEach { file ->
                        val fileName = file.fileName.toString()
                        if (basenames.any { fileName.equals("$it.kt", true) || fileName.equals("$it.java", true) }) {
                            runCatching { Files.readString(file) }.getOrNull()?.let { out += it }
                        }
                    }
                }
            }
        }
        return out.distinct()
    }

    private fun mergeNamed(
        static: List<Option>,
        typeText: String,
        names: Collection<String>,
    ): List<Option> {
        val seen = static.map { it.value.lowercase() }.toMutableSet()
        val merged = static.toMutableList()
        for (name in names) {
            if (seen.add(name.lowercase())) {
                merged += Option(name, typeText, "project")
            }
        }
        return merged
    }

    private fun options(typeText: String, vararg values: String): List<Option> =
        values.map { Option(it, typeText) }

    private fun insideInterpolation(beforeCaret: String): Boolean {
        val open = beforeCaret.lastIndexOf("\${")
        if (open < 0) return false
        val close = beforeCaret.indexOf('}', open)
        return close < 0
    }

    private val SKIP_MAP_KEYS = setOf("connections", "default", "disks", "channels", "stores", "mailers")
    private val SKIP_DISK_KEYS = setOf("disks", "default", "connections", "channels")
    private val SKIP_CHANNEL_KEYS = setOf("channels", "default", "disks", "connections", "stores", "mailers")
}
