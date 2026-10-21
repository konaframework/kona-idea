package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CadenceCompletionTest {
    @Test
    fun instancePropertyAndKeyApisSeeMigrationColumns() {
        val root = fixture()
        val binding =
            """
            val user = Auth.user() as? User
            """.trimIndent()

        assertSuggests(root, "$binding\nuser.", "email", "name", "id")
        assertSuggests(root, "$binding\nuser.em", "email")
        assertFalse(names(root, "$binding\nuser.em").contains("name"))

        assertSuggests(root, """$binding${'\n'}user.getAttribute(""")
        assertSuggests(root, """$binding${'\n'}user.getAttribute("em""", "email")
        assertSuggests(root, """$binding${'\n'}user.getAttribute("email""")
        assertSuggests(root, """$binding${'\n'}user.get(""")
        assertSuggests(root, """$binding${'\n'}user.get("em""", "email")
        assertSuggests(root, """$binding${'\n'}user["em""", "email")
        assertSuggests(root, """$binding${'\n'}user.setAttribute("na""", "name")

        val key = CadenceCompletion.collect(root, """$binding${'\n'}user.getAttribute("em""", """$binding${'\n'}user.getAttribute("em""".length)
        assertTrue(key.all { it.kind == CadenceCompletion.Kind.KEY })
        assertTrue(key.all { it.quoted })
        assertEquals(setOf("User"), key.map { it.modelClass }.toSet())

        val prop = CadenceCompletion.collect(root, "$binding\nuser.", "$binding\nuser.".length)
        assertTrue(prop.all { it.kind == CadenceCompletion.Kind.PROPERTY })
        assertTrue(prop.any { it.name == "email" && it.typeText.contains("users") })
    }

    @Test
    fun javaGetAndAssignmentSeeColumns() {
        val root = fixture()
        val src =
            """
            User user = User.find(1);
            user.get("em
            """.trimIndent()
        assertSuggests(root, src, "email")
        val prop =
            """
            User user = (User) Auth.user();
            user.na
            """.trimIndent()
        assertSuggests(root, prop, "name")
    }

    @Test
    fun whereCallSeesTableColumns() {
        val root = fixture()
        assertSuggests(root, """User.where("em""", "email")
        assertSuggests(root, """User.query().where("id""", "id")
        val items = CadenceCompletion.collect(root, """User.where("em""", """User.where("em""".length)
        assertTrue(items.all { it.kind == CadenceCompletion.Kind.COLUMN })
        assertEquals(setOf("users"), items.map { it.table }.toSet())
        assertTrue(items.all { it.typeText.startsWith("migration") })

        val camel =
            """
            val user = User.query().first()
            user.where("na
            """.trimIndent()
        assertEquals("users", CadenceCompletion.resolveTable(root, "user", camel))
        assertSuggests(root, camel, "name")
    }

    @Test
    fun dummyIdentifierDoesNotWipeColumns() {
        val root = fixture()
        val binding = "val user = User.find(1)\n"
        val cases =
            listOf(
                binding + "user.",
                binding + "user.em",
                binding + "user.getAttribute(",
                binding + "user.getAttribute(\"",
                binding + "user.getAttribute(\"em",
                binding + "user.get(",
                binding + "user.get(\"em",
                binding + "user[\"em",
                "User.where(\"",
                "User.where(\"em",
            )
        for (src in cases) {
            for (offset in dummyOffsets(src)) {
                val names =
                    CadenceCompletion.collect(root, offset.text, offset.caret)
                        .map { it.name }
                        .toSet()
                assertTrue(
                    "expected columns for `${src}` at dummy offset ${offset.caret}: $names",
                    names.contains("email") || names.contains("id") || names.contains("name"),
                )
            }
            val matcherDummy =
                CadenceCompletion.collect(
                    root,
                    src,
                    src.length,
                    matcherPrefix = CompletionText.DUMMY,
                )
            assertTrue(
                "matcher dummy wiped `$src`: ${matcherDummy.map { it.name }}",
                matcherDummy.any { it.name == "email" || it.name == "id" || it.name == "name" },
            )
            val shortDummy =
                CadenceCompletion.collect(root, src, src.length, matcherPrefix = "Int")
            assertTrue(
                "short dummy prefix wiped `$src`",
                shortDummy.any { it.name == "email" || it.name == "id" || it.name == "name" },
            )
        }
    }

    @Test
    fun dummyRewindInsideIdentifierAndOriginalOffset() {
        val typed = "user.em"
        val withDummy = typed + CompletionText.DUMMY
        val mid = typed.length + 5
        assertEquals(typed.length, CompletionText.offsetBeforeDummy(withDummy, mid))
        assertEquals(typed.length, CompletionText.fromContents(withDummy, typed.length).offset)
        assertEquals(typed.length, CompletionText.fromContents(withDummy, withDummy.length).offset)

        val atStart = "user." + CompletionText.DUMMY
        assertEquals("user.".length, CompletionText.offsetBeforeDummy(atStart, "user.".length))
        assertEquals(
            "user.".length,
            CompletionText.offsetBeforeDummy(atStart + " later()", "user.".length + CompletionText.DUMMY.length),
        )

        assertEquals("", CompletionText.effectivePrefix("", CompletionText.DUMMY))
        assertEquals("", CompletionText.effectivePrefix("", "Int"))
        assertEquals("em", CompletionText.effectivePrefix("em", CompletionText.DUMMY))
        assertEquals("email", CompletionText.effectivePrefix("email", "email"))
        assertTrue(CompletionText.isDummyResidue("Int"))
        assertTrue(CompletionText.isDummyResidue("I"))
        assertFalse(CompletionText.isDummyResidue("email"))
        assertFalse(CompletionText.isDummyResidue("em"))
        assertEquals("emInt", CompletionText.stripDummy("emInt"))
        assertEquals("", CompletionText.stripDummy("Int"))

        assertEquals(10, CompletionText.offsetInOriginal(10, 20))
        assertEquals(1, CompletionText.offsetInOriginal(20, 1))
        assertEquals(0, CompletionText.offsetInOriginal(5, 0))
        val origLen = 8
        assertEquals(
            (20 - CompletionText.DUMMY.length).coerceIn(0, origLen),
            CompletionText.offsetInOriginal(20, origLen),
        )

        val dummyAfter = "user." + CompletionText.DUMMY
        val beforeDummy = "user.".length - 1
        assertEquals(beforeDummy, CompletionText.offsetBeforeDummy(dummyAfter, beforeDummy))
        assertTrue(CompletionText.isDummyResidue(CompletionText.DUMMY.take(5)))
        assertEquals("em", CompletionText.effectivePrefix("em"))
        assertEquals(0, CompletionText.indexOfDummy(CompletionText.DUMMY))
        assertEquals(1, CompletionText.indexOfDummy("x" + CompletionText.DUMMY))
        assertEquals("foo".length, CompletionText.offsetBeforeDummy("fooIntell", "fooIntell".length))
        assertEquals(0, CompletionText.offsetInOriginal(-5, 10))
        val dummy = CompletionText.DUMMY
        val two = dummy + "xx" + dummy
        assertEquals(0, CompletionText.offsetBeforeDummy(two, dummy.length + 1))
    }

    @Test
    fun resolveTableNeverAbortsOnInstanceReceiver() {
        val root = fixture()
        assertNull(CadenceCompletion.resolveTable(root, null))
        assertNull(CadenceCompletion.resolveTable(root, ""))
        assertNull(CadenceCompletion.resolveTable(root, "   "))
        assertEquals("users", CadenceCompletion.resolveTable(root, "User"))
        assertEquals("users", CadenceCompletion.resolveTable(root, "user"))
        assertEquals("users", CadenceCompletion.resolveTable(root, "user", "val x = 1"))
        assertEquals("projects", CadenceCompletion.resolveTable(root, "project"))
        assertEquals("users", DbColumnCompletionContributor.resolveTable(root, "user", "val user = User.find(1)"))
    }

    @Test
    fun collectFallsThroughWhenNotAModelSite() {
        val root = fixture()
        assertTrue(CadenceCompletion.collect(root, "println(", "println(".length).isEmpty())
        assertTrue(
            CadenceCompletion.collect(root, """getAttribute("em""", """getAttribute("em""".length)
                .any { it.name == "email" },
        )
        assertFalse(CompletionText.isDummyResidue(""))
        assertTrue(CompletionText.isDummyResidue(CompletionText.DUMMY_WITH_SPACE))
        assertTrue(
            CadenceCompletion.modelSuggestions(root, "println(", "println(".length).isEmpty(),
        )
        assertTrue(
            CadenceCompletion.columnSuggestions(root, "user.", "user.".length).isEmpty(),
        )
        val connOnly =
            CadenceCompletion.columnSuggestions(
                root,
                """User.where("em""",
                """User.where("em""".length,
                mode = ColumnSourceMode.CONNECTION_ONLY,
                probeJdbc = false,
            )
        assertTrue(connOnly.isEmpty())

        val unscoped = CadenceCompletion.columnSuggestions(root, """where("em""", """where("em""".length)
        assertTrue(unscoped.any { it.name == "email" && it.table == null })
        assertTrue(
            CadenceCompletion.columnSuggestions(root, """User.where("zzz""", """User.where("zzz""".length)
                .isEmpty(),
        )

        assertEquals("migration", CadenceCompletion.sourceLabel(fromJdbc = false, fromMigrations = true))
        assertEquals("JDBC", CadenceCompletion.sourceLabel(fromJdbc = true, fromMigrations = false))
        assertEquals("migration+JDBC", CadenceCompletion.sourceLabel(fromJdbc = true, fromMigrations = true))
        assertEquals("migration", CadenceCompletion.sourceLabel(fromJdbc = false, fromMigrations = false))
    }

    @Test
    fun columnsCompleteAnywhereNotOnlyControllers() {
        val root = fixture()
        val service =
            """
            package app.services
            import org.konaframework.auth.Authenticatable
            class UserMailer {
                fun greet(user: Authenticatable) {
                    user.getAttribute("em
            """.trimIndent()
        assertSuggests(root, service, "email")
        assertEquals(
            "User",
            CadenceCompletion.collect(root, service, service.length).first { it.name == "email" }.modelClass,
        )

        val seeder =
            """
            package database.seeders
            class DatabaseSeeder {
                fun run() {
                    val user = User()
                    user.
            """.trimIndent()
        assertSuggests(root, seeder, "email", "name")

        val job =
            """
            package app.jobs
            class SendWelcome {
                fun handle(user: Authenticatable) {
                    user.
            """.trimIndent()
        assertSuggests(root, job, "email")

        val javaService =
            """
            package app.services;
            import org.konaframework.auth.Authenticatable;
            class UserMailer {
              void greet(Authenticatable user) {
                user.get("em
            """.trimIndent()
        assertSuggests(root, javaService, "email")

        val list =
            """
            val list = listOf(1)
            list.
            """.trimIndent()
        assertTrue(CadenceCompletion.collect(root, list, list.length).isEmpty())
    }

    @Test
    fun attributesPreferringCadenceFallsBackFromNonModelTypes() {
        val root = fixture()
        val known =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "User",
                "user",
                unscopedKeys = false,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertEquals("User", known.modelClass)
        assertTrue(known.attrs.any { it.name == "email" })

        val auth =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "Authenticatable",
                "user",
                unscopedKeys = false,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertEquals("User", auth.modelClass)
        assertTrue(auth.attrs.any { it.name == "email" })

        val session =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "Session",
                "session",
                unscopedKeys = false,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertTrue(session.attrs.isEmpty())

        val unscoped =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "",
                "",
                unscopedKeys = true,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertTrue(unscoped.attrs.any { it.name == "email" })

        val empty =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "Ghost",
                "ghost",
                unscopedKeys = false,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertTrue(empty.attrs.isEmpty())

        val nowhere = Files.createTempDirectory("kona-cadence-none")
        val none =
            CadenceCompletion.attributesPreferringCadence(
                nowhere,
                "",
                "",
                unscopedKeys = true,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertTrue(none.attrs.isEmpty())
    }

    @Test
    fun attributesPreferringCadenceKeepsColumnsWithoutModelFile() {
        val root = Files.createTempDirectory("kona-cadence-nofil")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateUsersTable.kt"),
            """schema.create("users") { id(); string("email") }""",
        )
        val preferred =
            CadenceCompletion.attributesPreferringCadence(
                root,
                "User",
                "user",
                unscopedKeys = false,
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        assertEquals("User", preferred.modelClass)
        assertTrue(preferred.attrs.any { it.name == "email" })
        assertNull(ModelIndex.find(root, "User"))
    }

    @Test
    fun prefixFilterAndUnquotedKey() {
        val root = fixture()
        val unquoted = "val user = User.find(1)\nuser.getAttribute("
        val items = CadenceCompletion.collect(root, unquoted, unquoted.length)
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { !it.quoted && it.kind == CadenceCompletion.Kind.KEY })
        val filtered =
            CadenceCompletion.modelSuggestions(
                root,
                "val user = User.find(1)\nuser.",
                "val user = User.find(1)\nuser.".length,
                matcherPrefix = "em",
            )
        assertEquals(setOf("email"), filtered.map { it.name }.toSet())
        assertTrue(
            CadenceCompletion.collect(
                root,
                "val user = User.find(1)\nuser.",
                "val user = User.find(1)\nuser.".length,
                mode = ColumnSourceMode.BOTH,
                probeJdbc = true,
            ).any { it.name == "email" },
        )
    }

    private fun fixture(): Path {
        val root = Files.createTempDirectory("kona-cadence-comp")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateUsersTable.kt"),
            """schema.create("users") { id(); string("email"); string("name"); timestamps() }""",
        )
        val models = root.resolve("src/main/kotlin/app/Models")
        Files.createDirectories(models)
        Files.writeString(models.resolve("User.kt"), "class User : Cadence<User>()")
        return root
    }

    private fun names(root: Path, src: String, matcherPrefix: String = ""): Set<String> =
        CadenceCompletion.collect(root, src, src.length, matcherPrefix = matcherPrefix)
            .map { it.name }
            .toSet()

    private fun assertSuggests(root: Path, src: String, vararg expected: String) {
        val found = names(root, src)
        val need = if (expected.isEmpty()) setOf("email") else expected.toSet()
        assertTrue("expected $need in $found for `$src`", found.containsAll(need))
    }

    private data class DummyCopy(val text: String, val caret: Int)

    private fun dummyOffsets(src: String): List<DummyCopy> {
        val dummy = CompletionText.DUMMY
        val copy = src + dummy
        val start = src.length
        return listOf(
            DummyCopy(copy, start),
            DummyCopy(copy, start + 4),
            DummyCopy(copy, copy.length),
            DummyCopy(src + CompletionText.DUMMY_WITH_SPACE, start),
        )
    }
}
