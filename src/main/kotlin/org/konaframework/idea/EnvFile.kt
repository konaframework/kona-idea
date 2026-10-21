package org.konaframework.idea

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

    fun dbSettings(env: Map<String, String>, projectRoot: Path? = null): DbSettings? {
        val rawUrl =
            env["DB_URL"]?.takeIf { it.isNotBlank() }
                ?: defaultUrlFor(env["DB_CONNECTION"])
                ?: return null
        val url = resolveJdbcUrl(rawUrl, projectRoot)
        val driver =
            env["DB_DRIVER"]?.takeIf { it.isNotBlank() && it.contains('.') }
                ?: driverClassFor(url)
        return DbSettings(
            url = url,
            username = env["DB_USERNAME"],
            password = env["DB_PASSWORD"],
            driver = driver,
        )
    }

    fun defaultUrlFor(connection: String?): String? =
        when (connection?.lowercase()) {
            "sqlite", "sqlite3" -> "jdbc:sqlite:storage/database/database.sqlite"
            "h2" -> "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
            else -> null
        }

    fun driverClassFor(url: String): String? =
        when {
            url.startsWith("jdbc:sqlite:") -> "org.sqlite.JDBC"
            url.startsWith("jdbc:h2:") -> "org.h2.Driver"
            url.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
            url.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
            url.startsWith("jdbc:mariadb:") -> "org.mariadb.jdbc.Driver"
            url.startsWith("jdbc:sqlserver:") -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            url.startsWith("jdbc:oracle:") -> "oracle.jdbc.OracleDriver"
            else -> null
        }

    /** Make relative SQLite / H2 file URLs absolute against the app root (IDE cwd ≠ project). */
    fun resolveJdbcUrl(url: String, projectRoot: Path?): String {
        if (projectRoot == null) return url
        val abs = projectRoot.toAbsolutePath().normalize()
        when {
            url.startsWith("jdbc:sqlite:") -> {
                val rest = url.removePrefix("jdbc:sqlite:")
                if (rest.startsWith(":") || rest.startsWith("file:") || Path.of(rest).isAbsolute) {
                    return url
                }
                return "jdbc:sqlite:" + abs.resolve(rest).normalize()
            }
            url.startsWith("jdbc:h2:file:") -> {
                val rest = url.removePrefix("jdbc:h2:file:")
                val pathPart = rest.substringBefore(';').removePrefix("./")
                val params = rest.substringAfter(';', missingDelimiterValue = "")
                if (Path.of(pathPart).isAbsolute) return url
                val resolved = abs.resolve(pathPart).normalize()
                return "jdbc:h2:file:$resolved" + if (params.isEmpty()) "" else ";$params"
            }
            else -> return url
        }
    }

    data class DbSettings(
        val url: String,
        val username: String?,
        val password: String?,
        val driver: String?,
    )
}
