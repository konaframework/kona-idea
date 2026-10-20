package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GrindCallTest {
    @Test
    fun quotedAndUnquotedCallSites() {
        val quoted = """Grind.call("mig"""
        val q = GrindCallPatterns.callContextAt(quoted, quoted.length)!!
        assertEquals("mig", q.prefix)
        assertTrue(q.quoted)

        val silent = """Grind.callSilently("db:"""
        assertEquals("db:", GrindCallPatterns.callContextAt(silent, silent.length)!!.prefix)

        val fqcn = """org.konaframework.grind.Grind.call("serve"""
        assertEquals("serve", GrindCallPatterns.callContextAt(fqcn, fqcn.length)!!.prefix)

        val bare = "Grind.call("
        val u = GrindCallPatterns.callContextAt(bare, bare.length)!!
        assertEquals("", u.prefix)
        assertFalse(u.quoted)

        val partial = "Grind.call(make:"
        assertEquals("make:", GrindCallPatterns.callContextAt(partial, partial.length)!!.prefix)

        assertNull(GrindCallPatterns.callContextAt("""println("mig""", """println("mig""".length))
        assertNull(GrindCallPatterns.quotedContext("x", 0))
        assertTrue(GrindCallPatterns.isCmdPart(':'))
        assertFalse(GrindCallPatterns.isCmdPart(' '))
    }

    @Test
    fun dummyInsideGrindCallString() {
        val src = """Grind.call(""" + "\"" + CompletionText.DUMMY
        val snap = CompletionText.fromContents(src, src.length)
        val ctx = GrindCallPatterns.callContextAt(snap.text, snap.offset)!!
        assertEquals("", ctx.prefix)
        assertTrue(ctx.quoted)
    }

    @Test
    fun commandNameFromSignature() {
        assertEquals("mail:send", GrindCommandIndex.commandNameFromSignature("mail:send {user} {--queue=}"))
        assertEquals("app:foo", GrindCommandIndex.commandNameFromSignature("app:foo"))
        assertEquals("", GrindCommandIndex.commandNameFromSignature("   "))
    }

    @Test
    fun parseKotlinAndJavaCommandSources() {
        val kt =
            """
            class MailSendCommand : ConsoleCommand() {
              override val signature: String = "mail:send {user}"
              override val description: String = "Send mail"
            }
            """.trimIndent()
        val parsed = GrindCommandIndex.parseCommandSource(kt)!!
        assertEquals("mail:send", parsed.name)
        assertEquals("Send mail", parsed.summary)
        assertTrue(parsed.custom)

        val java =
            """
            public class FooCommand extends ConsoleCommand {
              public String getSignature() { return "app:foo"; }
              public String getDescription() { return "Foo it"; }
            }
            """.trimIndent()
        assertEquals("app:foo", GrindCommandIndex.parseCommandSource(java)!!.name)
        assertNull(GrindCommandIndex.parseCommandSource("class NotACommand"))
    }

    @Test
    fun discoversMetaInfAndConsoleSources() {
        val root = Files.createTempDirectory("kona-grind-idx")
        val meta = root.resolve("src/main/resources/META-INF/kona/commands")
        Files.createDirectories(meta.parent)
        Files.writeString(
            meta,
            """
            # comment
            app.console.commands.MailSendCommand

            app.console.commands.MissingCommand
            """.trimIndent(),
        )
        val dir = root.resolve("src/main/kotlin/app/console/commands")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("MailSendCommand.kt"),
            """
            package app.console.commands
            class MailSendCommand : ConsoleCommand() {
              override val signature = "mail:send {user}"
              override val description = "Send mail"
            }
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("ReportCommand.kt"),
            """
            class ReportCommand : ConsoleCommand() {
              override val signature = "app:report"
            }
            """.trimIndent(),
        )
        Files.writeString(dir.resolve("notes.txt"), "ignore")

        assertEquals(
            listOf("app.console.commands.MailSendCommand", "app.console.commands.MissingCommand"),
            GrindCommandIndex.metaInfCommands(root),
        )
        val custom = GrindCommandIndex.customCommands(root)
        assertTrue(custom.any { it.name == "mail:send" })
        assertTrue(custom.any { it.name == "app:report" })

        val all = GrindCommandIndex.commands(root).map { it.name }.toSet()
        assertTrue(
            all.containsAll(
                listOf(
                    "serve",
                    "migrate",
                    "make:model",
                    "install",
                    "down",
                    "up",
                    "ide:lsp",
                    "migrate:fresh",
                    "migrate:refresh",
                    "migrate:install",
                    "db:wipe",
                    "db:show",
                    "db:table",
                    "schema:dump",
                ),
            ),
        )
        assertTrue(all.contains("mail:send"))
        assertTrue(GrindCommandIndex.names(root).contains("app:report"))
        assertTrue(GrindCommandIndex.consoleDirs(root).isNotEmpty())
        assertNotNull(
            GrindCommandIndex.sourceForFqcn(root, "app.console.commands.MailSendCommand"),
        )
        assertNull(GrindCommandIndex.sourceForFqcn(root, "app.console.commands.MissingCommand"))

        val txt = root.resolve("src/main/resources/META-INF/kona/commands.txt")
        Files.writeString(txt, "app.console.commands.ExtraCommand\n")
        assertTrue(GrindCommandIndex.metaInfCommands(root).contains("app.console.commands.ExtraCommand"))
    }

    @Test
    fun javaConsoleCommandFromMetaInf() {
        val root = Files.createTempDirectory("kona-grind-java")
        val meta = root.resolve("src/main/resources/META-INF/kona/commands")
        Files.createDirectories(meta.parent)
        Files.writeString(meta, "app.console.commands.FooCommand\n")
        val dir = root.resolve("src/main/java/app/console/commands")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("FooCommand.java"),
            """
            package app.console.commands;
            public class FooCommand extends ConsoleCommand {
              public String getSignature() { return "app:foo {--quiet}"; }
              public String getDescription() { return ""; }
            }
            """.trimIndent(),
        )
        val cmd = GrindCommandIndex.customCommands(root).single()
        assertEquals("app:foo", cmd.name)
        assertEquals("custom command", cmd.summary)
    }
}
