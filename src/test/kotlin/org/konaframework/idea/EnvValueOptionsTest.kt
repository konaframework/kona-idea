package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EnvValueOptionsTest {
    @Test
    fun catalogCoversKnownEnumKeys() {
        val db = EnvValueOptions.optionsFor("DB_CONNECTION", null).map { it.value }
        assertTrue(db.containsAll(listOf("main", "h2", "sqlite", "mysql", "mariadb", "pgsql", "sqlsrv", "oracle")))

        val log = EnvValueOptions.optionsFor("LOG_CHANNEL", null).map { it.value }
        assertTrue(
            log.containsAll(
                listOf("stack", "single", "daily", "stderr", "stdout", "console", "syslog", "errorlog", "null"),
            ),
        )
        assertEquals(log, EnvValueOptions.optionsFor("LOG_STACK", null).map { it.value })

        assertEquals(
            listOf("array", "cookie", "file", "memory", "redis"),
            EnvValueOptions.matching(EnvValueOptions.optionsFor("SESSION_DRIVER", null), "").map { it.value },
        )
        assertTrue(EnvValueOptions.optionsFor("CACHE_STORE", null).map { it.value }.containsAll(listOf("array", "file", "redis")))
        assertTrue(EnvValueOptions.optionsFor("QUEUE_CONNECTION", null).map { it.value }.contains("sync"))
        assertTrue(EnvValueOptions.optionsFor("MAIL_MAILER", null).map { it.value }.containsAll(listOf("log", "smtp")))
        assertTrue(EnvValueOptions.optionsFor("HASH_DRIVER", null).map { it.value }.contains("argon2id"))
        assertTrue(EnvValueOptions.optionsFor("FILESYSTEM_DISK", null).map { it.value }.containsAll(listOf("local", "public")))
        assertTrue(EnvValueOptions.optionsFor("APP_ENV", null).map { it.value }.contains("production"))

        val bools =
            EnvValueOptions.matching(EnvValueOptions.optionsFor("APP_DEBUG", null), "").map { it.value }
        assertEquals(listOf("0", "1", "false", "no", "true", "yes"), bools)
        assertEquals(bools, EnvValueOptions.matching(EnvValueOptions.optionsFor("LOG_TO_FILE", null), "").map { it.value })
        assertEquals(bools, EnvValueOptions.matching(EnvValueOptions.optionsFor("SESSION_ENCRYPT", null), "").map { it.value })
        assertEquals(
            bools,
            EnvValueOptions.matching(EnvValueOptions.optionsFor("DB_QUOTE_IDENTIFIERS", null), "").map { it.value },
        )

        assertEquals(
            listOf("Lax", "None", "Strict"),
            EnvValueOptions.matching(EnvValueOptions.optionsFor("SESSION_SAME_SITE", null), "").map { it.value },
        )
        val levels = EnvValueOptions.optionsFor("LOG_LEVEL", null).map { it.value }
        assertTrue(levels.containsAll(listOf("emergency", "error", "warning", "warn", "info", "debug")))

        assertTrue(EnvValueOptions.optionsFor("UNKNOWN_KEY", null).isEmpty())
    }

    @Test
    fun prefixFilterIsCaseInsensitive() {
        assertEquals(
            listOf("main", "mariadb", "mysql"),
            EnvValueOptions.matching(EnvValueOptions.optionsFor("DB_CONNECTION", null), "m").map { it.value },
        )
        assertEquals(
            listOf("pgsql"),
            EnvValueOptions.matching(EnvValueOptions.optionsFor("db_connection", null), "PG").map { it.value },
        )
        assertEquals(
            listOf("Lax"),
            EnvValueOptions.matching(EnvValueOptions.optionsFor("SESSION_SAME_SITE", null), "la").map { it.value },
        )
    }

    @Test
    fun dotenvValuePositionDetection() {
        assertNull(EnvValueOptions.dotenvValuePrefixAt("DB_CONNECTION", "DB_CONNECTION".length))
        assertNull(EnvValueOptions.dotenvValuePrefixAt("DB_CONNECTION=", -1))
        assertNull(EnvValueOptions.dotenvValuePrefixAt("# DB_CONNECTION=h2\n", 3))
        assertEquals(
            EnvValueOptions.DotenvValueEdit("DB_CONNECTION", ""),
            EnvValueOptions.dotenvValuePrefixAt("DB_CONNECTION=", "DB_CONNECTION=".length),
        )
        assertEquals(
            EnvValueOptions.DotenvValueEdit("DB_CONNECTION", "my"),
            EnvValueOptions.dotenvValuePrefixAt("DB_CONNECTION=my", "DB_CONNECTION=my".length),
        )
        assertEquals(
            EnvValueOptions.DotenvValueEdit("APP_DEBUG", "tr"),
            EnvValueOptions.dotenvValuePrefixAt("export APP_DEBUG=tr", "export APP_DEBUG=tr".length),
        )
        assertEquals(
            EnvValueOptions.DotenvValueEdit("DB_CONNECTION", "h"),
            EnvValueOptions.dotenvValuePrefixAt("DB_CONNECTION=\"h", "DB_CONNECTION=\"h".length),
        )
        // Key side — null
        assertNull(EnvValueOptions.dotenvValuePrefixAt("DB_CONN", "DB_CONN".length))
        // Inside ${…} — null (key interpolation, not value enum)
        assertNull(EnvValueOptions.dotenvValuePrefixAt("URL=\${DB", "URL=\${DB".length))
        assertNull(EnvValueOptions.dotenvValuePrefixAt("URL=\${", "URL=\${".length))
        // After a closed ${…}, caret is still on the value side (full value prefix)
        assertEquals(
            EnvValueOptions.DotenvValueEdit("URL", "\${HOST}jdbc"),
            EnvValueOptions.dotenvValuePrefixAt("URL=\${HOST}jdbc", "URL=\${HOST}jdbc".length),
        )
    }

    @Test
    fun getOrDefaultSecondArgDetection() {
        val source = """Env.getOrDefault("DB_CONNECTION", "h2")"""
        val atH = source.indexOf("h2") + 1
        assertEquals(
            EnvValueOptions.DefaultValueEdit("DB_CONNECTION", "h"),
            EnvValueOptions.getOrDefaultValuePrefixAt(source, atH),
        )
        val open = """Env.getOrDefault("SESSION_DRIVER", """"
        assertEquals(
            EnvValueOptions.DefaultValueEdit("SESSION_DRIVER", ""),
            EnvValueOptions.getOrDefaultValuePrefixAt(open, open.length),
        )
        val named = """Env.getOrDefault(key = "APP_ENV", default = "loc"""
        assertEquals(
            EnvValueOptions.DefaultValueEdit("APP_ENV", "loc"),
            EnvValueOptions.getOrDefaultValuePrefixAt(named, named.length),
        )
        // Key / first-arg side
        assertNull(EnvValueOptions.getOrDefaultValuePrefixAt("""Env.getOrDefault("DB""", """Env.getOrDefault("DB""".length))
        assertNull(EnvValueOptions.getOrDefaultValuePrefixAt("""Env.get("DB_CONNECTION")""", """Env.get("DB_CONNECTION")""".length - 2))
        assertNull(EnvValueOptions.getOrDefaultValuePrefixAt("nope", 2))
        assertNull(EnvValueOptions.getOrDefaultValuePrefixAt(source, -1))
    }

    @Test
    fun projectDatabaseConnectionsMerge() {
        val root = Files.createTempDirectory("kona-env-values")
        val config = root.resolve("src/main/kotlin/config")
        Files.createDirectories(config)
        Files.writeString(
            config.resolve("Database.kt"),
            """
            "connections" to mapOf(
                "main" to mapOf("url" to "jdbc:h2:mem"),
                "analytics" to mapOf("url" to "jdbc:postgresql://db/analytics"),
            )
            """.trimIndent(),
        )
        val values = EnvValueOptions.optionsFor("DB_CONNECTION", root).map { it.value }
        assertTrue(values.contains("analytics"))
        assertTrue(values.contains("main"))
        assertTrue(values.contains("pgsql"))

        val javaRoot = Files.createTempDirectory("kona-env-values-java")
        val javaConfig = javaRoot.resolve("src/main/java/config")
        Files.createDirectories(javaConfig)
        Files.writeString(
            javaConfig.resolve("Database.java"),
            """
            connections.put("warehouse", warehouse);
            connections.put("main", main);
            """.trimIndent(),
        )
        assertTrue(EnvValueOptions.projectConnectionNames(javaRoot).containsAll(listOf("warehouse", "main")))
    }

    @Test
    fun projectFilesystemDisksAndLogChannels() {
        val root = Files.createTempDirectory("kona-env-disks")
        val config = root.resolve("src/main/kotlin/config")
        Files.createDirectories(config)
        Files.writeString(
            config.resolve("Filesystems.kt"),
            """
            "disks" to mapOf(
                "local" to mapOf("driver" to "local"),
                "uploads" to mapOf("driver" to "local"),
            )
            """.trimIndent(),
        )
        Files.writeString(
            config.resolve("Logging.kt"),
            """
            config.set("logging.channels.payments.driver", "single")
            "channels" to mapOf(
                "stack" to mapOf("driver" to "stack"),
                "audit" to mapOf("driver" to "daily"),
            )
            """.trimIndent(),
        )
        assertTrue(EnvValueOptions.optionsFor("FILESYSTEM_DISK", root).map { it.value }.contains("uploads"))
        val channels = EnvValueOptions.optionsFor("LOG_CHANNEL", root).map { it.value }
        assertTrue(channels.contains("payments"))
        assertTrue(channels.contains("audit"))
        assertTrue(channels.contains("stack"))
    }

    @Test
    fun parseHelpersSkipStructuralKeys() {
        val text =
            """
            "connections" to mapOf(
                "h2" to mapOf(),
                "custom" to mapOf(),
            )
            connections.put("javaOnly", x);
            """.trimIndent()
        assertEquals(listOf("h2", "custom", "javaOnly"), EnvValueOptions.parseDatabaseConnections(text))
        assertFalse(EnvValueOptions.parseDatabaseConnections(text).contains("connections"))

        assertEquals(
            listOf("local", "cdn"),
            EnvValueOptions.parseFilesystemDisks(
                """
                "disks" to mapOf("local" to mapOf(), "cdn" to mapOf())
                disks.put("cdn", x);
                """.trimIndent(),
            ).distinct(),
        )
    }

    @Test
    fun dotenvFamilyNames() {
        assertTrue(EnvValueOptions.isDotEnvFamilyName(".env"))
        assertTrue(EnvValueOptions.isDotEnvFamilyName(".env.example"))
        assertTrue(EnvValueOptions.isDotEnvFamilyName(".env.local"))
        assertFalse(EnvValueOptions.isDotEnvFamilyName(".env.bak"))
        assertFalse(EnvValueOptions.isDotEnvFamilyName(".env.encrypted"))
        assertFalse(EnvValueOptions.isDotEnvFamilyName("env"))
        assertFalse(EnvValueOptions.isDotEnvFamilyName("application.properties"))
    }
}
