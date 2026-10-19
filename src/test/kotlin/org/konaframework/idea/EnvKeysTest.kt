package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EnvKeysTest {
    @Test
    fun usagesFindEnvLookupsOnly() {
        val source =
            """
            Env.get("APP_NAME")
            Env.getOrDefault("DB_URL", "jdbc:h2:mem")
            Env.require(key = "APP_KEY")
            Env.set("IGNORED", "x")
            System.getenv("HOST")
            MyEnv.get("NOT_THIS")
            """.trimIndent()
        assertEquals(
            listOf("APP_NAME", "DB_URL", "APP_KEY"),
            EnvKeys.usages(source).map { it.name },
        )
        val app = EnvKeys.usages(source).first()
        assertEquals("APP_NAME", source.substring(app.start, app.end))
    }

    @Test
    fun usageAtAndLookupPrefix() {
        val source = """val name = Env.get("APP_NAME")"""
        val inside = source.indexOf("APP_NAME") + 2
        val hit = EnvKeys.usageAt(source, inside)
        assertEquals("APP_NAME", hit!!.name)
        assertEquals("APP_", EnvKeys.lookupPrefixAt(source, source.indexOf("APP_NAME") + 4))
        val open = "Env.get(\""
        assertEquals("", EnvKeys.lookupPrefixAt(open, open.length))
        assertEquals("DB", EnvKeys.lookupPrefixAt("""Env.getOrDefault("DB""", """Env.getOrDefault("DB""".length))
        assertEquals("APP_KEY", EnvKeys.lookupPrefixAt("""Env.require(key = "APP_KEY""", """Env.require(key = "APP_KEY""".length))

        assertNull(EnvKeys.usageAt("", 0))
        assertNull(EnvKeys.usageAt(source, -1))
        assertNull(EnvKeys.usageAt(source, source.length + 1))
        assertNull(EnvKeys.usageAt("no quotes", 3))
        assertNull(EnvKeys.usageAt("hello\nAPP", 8))
        assertNull(EnvKeys.usageAt(source, source.indexOf('"')))
        assertNull(EnvKeys.usageAt(source, source.lastIndexOf('"')))
        assertNull(EnvKeys.usageAt("""Env.get("APP-NAME")""", """Env.get("APP-NAME")""".indexOf('-')))
        assertNull(EnvKeys.usageAt("""println("APP_NAME")""", """println("APP_NAME")""".indexOf('A')))
        assertNull(EnvKeys.lookupPrefixAt(source, -1))
        assertNull(EnvKeys.lookupPrefixAt(source, source.length + 4))
        assertNull(EnvKeys.lookupPrefixAt("Env.get(APP", "Env.get(APP".length))
        assertNull(EnvKeys.lookupPrefixAt("""println("APP")""", """println("APP")""".length))

        val unclosed = """Env.get("APP_NAME"""
        assertEquals("APP_NAME", EnvKeys.usageAt(unclosed, unclosed.length)!!.name)
    }

    @Test
    fun dotenvPrefixesAndDeclarations() {
        assertNull(EnvKeys.dotenvKeyPrefixAt("APP", -1))
        assertNull(EnvKeys.dotenvKeyPrefixAt("APP", 4))
        assertNull(EnvKeys.dotenvKeyPrefixAt("# comment\n", 3))
        assertNull(EnvKeys.dotenvKeyPrefixAt("  # hidden", 6))
        assertNull(EnvKeys.dotenvKeyPrefixAt("APP_NAME=value", "APP_NAME=value".length))
        assertNull(EnvKeys.dotenvKeyPrefixAt("APP NAME", "APP NAME".length))

        assertEquals(
            EnvKeys.DotenvEdit("APP_", interpolating = false),
            EnvKeys.dotenvKeyPrefixAt("export APP_", "export APP_".length),
        )
        assertEquals(
            EnvKeys.DotenvEdit("", interpolating = false),
            EnvKeys.dotenvKeyPrefixAt("", 0),
        )
        assertEquals(
            EnvKeys.DotenvEdit("DB", interpolating = true),
            EnvKeys.dotenvKeyPrefixAt("URL=\${DB", "URL=\${DB".length),
        )
        assertEquals(
            EnvKeys.DotenvEdit("", interpolating = true),
            EnvKeys.dotenvKeyPrefixAt("URL=\${", "URL=\${".length),
        )

        val body = "APP_NAME=hello\r\n# skip\nexport DB_URL=jdbc\nAPP_NAME=second\n\nnot a key\n"
        val decls = EnvKeys.declarations(body)
        assertEquals(listOf("APP_NAME", "DB_URL", "APP_NAME"), decls.map { it.name })
        val last = EnvKeys.declaration(body, "APP_NAME")
        assertEquals("second", body.substring(last!!.end).substringBefore('\n').removePrefix("="))
        assertEquals("APP_NAME", body.substring(last.start, last.end))
        assertTrue(last.line > decls.first().line)
        assertNull(EnvKeys.declaration(body, "MISSING"))
        assertTrue(EnvKeys.declarations("").isEmpty())
        assertTrue(EnvKeys.declarations("# only\n").isEmpty())
    }

    @Test
    fun projectIndexMergesEnvFilesAndSource() {
        val root = Files.createTempDirectory("kona-env")
        Files.writeString(root.resolve(".env"), "APP_NAME=Demo\nEXTRA=1\n")
        Files.writeString(root.resolve(".env.example"), "EXAMPLE_ONLY=1\nAPP_NAME=from-example\n")
        Files.writeString(root.resolve(".env.bak"), "BACKUP_ONLY=1\n")
        Files.createDirectories(root.resolve(".env.d"))
        val config = root.resolve("src/main/kotlin/config")
        Files.createDirectories(config)
        Files.writeString(
            config.resolve("App.kt"),
            """
            Env.getOrDefault("APP_NAME", "x")
            Env.get("DB_URL")
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("src/main/java/config"))
        Files.writeString(
            root.resolve("src/main/java/config/Mail.java"),
            """Env.require("MAIL_HOST");""",
        )
        Files.writeString(config.resolve("notes.txt"), """Env.get("FROM_TEXT")""")

        assertEquals(listOf("APP_NAME", "DB_URL", "MAIL_HOST"), EnvKeys.referencedKeys(root))
        assertTrue(EnvKeys.declaredKeys(root).containsAll(listOf("APP_NAME", "EXTRA", "EXAMPLE_ONLY")))
        assertFalse(EnvKeys.declaredKeys(root).contains("BACKUP_ONLY"))
        assertEquals(
            listOf("APP_NAME", "DB_URL", "EXAMPLE_ONLY", "EXTRA", "MAIL_HOST"),
            EnvKeys.completionKeys(root),
        )
        assertEquals(
            listOf("APP_NAME"),
            EnvKeys.matching(EnvKeys.completionKeys(root), "app"),
        )
        assertEquals(
            listOf("APP_NAME", "DB_URL", "EXAMPLE_ONLY", "EXTRA", "MAIL_HOST"),
            EnvKeys.matching(EnvKeys.completionKeys(root), ""),
        )
        assertEquals(root.resolve(".env"), EnvKeys.envFile(root))
        assertTrue(EnvKeys.isDotEnvFileName(".env"))
        assertFalse(EnvKeys.isDotEnvFileName(".env.example"))
        assertEquals(listOf(".env", ".env.example"), EnvKeys.envFiles(root).map { it.fileName.toString() })

        val empty = Files.createTempDirectory("kona-env-empty")
        assertTrue(EnvKeys.referencedKeys(empty).isEmpty())
        assertTrue(EnvKeys.declaredKeys(empty).isEmpty())
        assertTrue(EnvKeys.completionKeys(empty).isEmpty())
        assertTrue(EnvKeys.envFiles(empty.resolve("missing")).isEmpty())
        assertTrue(EnvKeys.envFiles(root.resolve(".env")).isEmpty())
    }
}
