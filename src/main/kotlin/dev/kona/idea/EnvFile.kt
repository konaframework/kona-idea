package dev.kona.idea

import java.nio.file.Files
import java.nio.file.Path

/** Minimal `.env` reader for `DB_*` keys (no interpolation). */
object EnvFile {
    fun load(path: Path): Map<String, String> {
        if (!Files.isRegularFile(path)) return emptyMap()
        return parse(Files.readString(path))
    }

    fun parse(content: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            var value = line.substring(eq + 1).trim()
            if ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\''))
            ) {
                value = value.substring(1, value.length - 1)
            }
            out[key] = value
        }
        return out
    }

    fun dbSettings(env: Map<String, String>): DbSettings? {
        val url = env["DB_URL"]?.takeIf { it.isNotBlank() } ?: return null
        return DbSettings(
            url = url,
            username = env["DB_USERNAME"],
            password = env["DB_PASSWORD"],
            driver = env["DB_DRIVER"],
        )
    }

    data class DbSettings(
        val url: String,
        val username: String?,
        val password: String?,
        val driver: String?,
    )
}
