package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CompletionUxTest {
    @Test
    fun autopopupHelpersDetectAtAndPrefix() {
        assertTrue(CremaCompletion.shouldAutopopup("@", 1))
        assertTrue(CremaCompletion.shouldAutopopup("@com", 4))
        assertEquals("@com", CremaCompletion.prefixBefore("@com", 4))
        assertTrue(CremaDirectives.completionsForPrefix("@com").any { it.startsWith("@com") })
        assertFalse(CremaCompletion.shouldAutopopup("hello", 5))
        assertFalse(CremaCompletion.shouldAutopopup("@@", 2))
    }

    @Test
    fun cadenceTableNamesMatchFramework() {
        assertEquals("blog_posts", CadenceTableNames.tableNameFor("BlogPost"))
        assertEquals("categories", CadenceTableNames.tableNameFor("Category"))
        assertEquals("boxes", CadenceTableNames.tableNameFor("Box"))
        assertEquals("users", CadenceTableNames.tableNameFor("User"))
        assertEquals("projects", CadenceTableNames.tableNameFor("Project"))
        assertEquals("project", CadenceTableNames.instanceNameFor("Project"))
        assertEquals("blogPost", CadenceTableNames.instanceNameFor("BlogPost"))
    }

    @Test
    fun modelIndexParsesTableOverrideAndConvention() {
        val kt =
            """
            class Project : Cadence<Project>() {
              override val table = "projects"
            }
            class Todo : Cadence<Todo>()
            class User : Cadence<User>(), Authenticatable, HasCredentials, HasEmail
            """.trimIndent()
        val parsed = ModelIndex.parseSource(kt)
        assertEquals("projects", parsed.first { it.className == "Project" }.table)
        assertEquals("todos", parsed.first { it.className == "Todo" }.table)
        assertEquals("users", parsed.first { it.className == "User" }.table)

        val java =
            """
            public class User extends Cadence {
              public String getTable() { return "accounts"; }
            }
            """.trimIndent()
        assertEquals("accounts", ModelIndex.parseSource(java).first().table)

        val javaGeneric =
            """
            public class Account extends Cadence<Account> implements Authenticatable {}
            """.trimIndent()
        assertEquals("accounts", ModelIndex.parseSource(javaGeneric).first().table)
    }

    @Test
    fun modelIndexCollectsFromAppModels() {
        val root = Files.createTempDirectory("kona-models")
        val dir = root.resolve("src/main/kotlin/app/Models")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("Project.kt"),
            """
            package app.Models
            class Project : Cadence<Project>() {
              override val table = "projects"
            }
            """.trimIndent(),
        )
        val by = ModelIndex.byClassName(root)
        assertEquals("projects", by["Project"]!!.table)
        assertEquals("projects", ModelIndex.tableForClass(root, "Project"))
        assertEquals("users", ModelIndex.tableForClass(root, "User")) // convention fallback
    }

    @Test
    fun xComponentIndexListsComponentsAndSlots() {
        val root = Files.createTempDirectory("kona-x")
        val comps = root.resolve("src/main/resources/views/components")
        Files.createDirectories(comps.resolve("inputs"))
        Files.writeString(
            comps.resolve("alert.crema.html"),
            """
            <div>{{ title }}</div>
            <div>{{ slot }}</div>
            @slot('footer')@endslot
            """.trimIndent(),
        )
        Files.writeString(comps.resolve("inputs/button.crema.html"), "<button>{{ slot }}</button>")
        val list = XComponentIndex.list(root)
        assertEquals(listOf("alert", "inputs.button"), list.map { it.tagName })
        assertTrue(list.first { it.tagName == "alert" }.slotNames.containsAll(listOf("title", "footer")))
        assertEquals("components.alert", list.first().viewName)
    }

    @Test
    fun xComponentPatternsDetectTagAndSlot() {
        assertEquals("ale", XComponentPatterns.tagPrefixAt("<x-ale", 6)?.prefix)
        assertEquals("", XComponentPatterns.tagPrefixAt("<x-", 3)?.prefix)
        assertFalse(XComponentPatterns.tagPrefixAt("<x-", 3)!!.isSlot)
        assertTrue(XComponentPatterns.tagPrefixAt("<x-slot:", 8)!!.isSlot)
        assertEquals("fo", XComponentPatterns.tagPrefixAt("<x-slot:fo", 10)?.prefix)
        assertNull(XComponentPatterns.tagPrefixAt("hello", 5))
        assertTrue(XComponentPatterns.wantsSlotChild("<x-alert><", "<x-alert><".length))
        assertFalse(XComponentPatterns.wantsSlotChild("plain", 5))
    }

    @Test
    fun dbColumnPatternsScopeReceiver() {
        val src = """User.where("em"""
        val ctx = DbColumnPatterns.columnContextAt(src, src.length)!!
        assertEquals("em", ctx.prefix)
        assertEquals("User", ctx.receiver)

        val project = """Project.where("na"""
        assertEquals("Project", DbColumnPatterns.columnContextAt(project, project.length)!!.receiver)

        val chained = """User.query().where("id"""
        assertEquals("User", DbColumnPatterns.columnContextAt(chained, chained.length)!!.receiver)

        assertNull(DbColumnPatterns.columnContextAt("""println("em""", """println("em""".length))
    }

    @Test
    fun resolveTableUsesModelIndex() {
        val root = Files.createTempDirectory("kona-scope")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateUsersTable.kt"),
            """schema.create("users") { id(); string("email"); string("name") }""",
        )
        Files.writeString(
            mig.resolve("CreateProjectsTable.kt"),
            """schema.create("projects") { id(); string("description"); string("name") }""",
        )
        val models = root.resolve("src/main/kotlin/app/Models")
        Files.createDirectories(models)
        Files.writeString(
            models.resolve("User.kt"),
            "class User : Cadence<User>()",
        )
        Files.writeString(
            models.resolve("Project.kt"),
            "class Project : Cadence<Project>()",
        )

        val snap = DbColumnIndex.collect(root, mode = ColumnSourceMode.MIGRATIONS_ONLY, probeJdbc = false)
        assertTrue(snap.columnsForTable("users").contains("email"))
        assertTrue(snap.columnsForTable("projects").contains("description"))
        assertFalse(snap.columnsForTable("users").contains("description"))

        assertEquals("users", DbColumnCompletionContributor.resolveTable(root, "User"))
        assertEquals("projects", DbColumnCompletionContributor.resolveTable(root, "project"))
    }

    @Test
    fun columnSourceModeFiltersIndex() {
        val root = Files.createTempDirectory("kona-mode")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateWidgetsTable.kt"),
            """schema.create("widgets") { id(); string("color") }""",
        )
        val migOnly =
            DbColumnIndex.collect(root, mode = ColumnSourceMode.MIGRATIONS_ONLY, probeJdbc = false)
        assertTrue(migOnly.fromMigrations)
        assertFalse(migOnly.fromJdbc)
        assertTrue(migOnly.allColumns.contains("color"))

        val connOnly =
            DbColumnIndex.collect(root, mode = ColumnSourceMode.CONNECTION_ONLY, probeJdbc = false)
        assertFalse(connOnly.fromMigrations)
        assertTrue(connOnly.allColumns.isEmpty())

        assertEquals(ColumnSourceMode.BOTH, ColumnSourceMode.fromStored(null))
        assertEquals(ColumnSourceMode.MIGRATIONS_ONLY, ColumnSourceMode.fromStored("MIGRATIONS_ONLY"))
        assertEquals(ColumnSourceMode.BOTH, ColumnSourceMode.fromStored("nope"))
    }

    @Test
    fun modelPropertyPatternsResolveAssignment() {
        val src =
            """
            val project = Project.find(1)
            project.des
            """.trimIndent()
        val offset = src.indexOf("des") + 3
        val prop = ModelPropertyPatterns.propertyAt(src, offset)!!
        assertEquals("project", prop.receiver)
        assertEquals("des", prop.prefix)
        assertEquals("Project", prop.modelClass)

        val conventional = """project.nam"""
        assertEquals(
            "Project",
            ModelPropertyPatterns.propertyAt(conventional, conventional.length)!!.modelClass,
        )
        assertNull(ModelPropertyPatterns.propertyAt("Project.where", "Project.where".length))
    }

    @Test
    fun settingsStateRoundTrip() {
        val settings = KonaIdeaSettings()
        assertEquals(ColumnSourceMode.BOTH, settings.columnSourceMode)
        settings.columnSourceMode = ColumnSourceMode.CONNECTION_ONLY
        assertEquals(ColumnSourceMode.CONNECTION_ONLY, settings.columnSourceMode)
        val state = settings.getState()
        assertEquals("CONNECTION_ONLY", state.columnSourceMode)
        settings.loadState(KonaIdeaState(columnSourceMode = "MIGRATIONS_ONLY"))
        assertEquals(ColumnSourceMode.MIGRATIONS_ONLY, settings.columnSourceMode)
    }

    @Test
    fun xSlotHintsFromExtractSlotHints() {
        val body = "{{ title }} {{ footer }} {{ slot }}"
        val hints = XComponentIndex.extractSlotHints(body)
        assertTrue(hints.contains("title"))
        assertTrue(hints.contains("footer"))
        assertFalse(hints.contains("slot"))
    }

    @Test
    fun konaProjectStructureListsScaffoldEntries() {
        val present =
            setOf(
                "src/main/kotlin/bootstrap",
                "src/main/kotlin/app",
                "src/main/kotlin/routes",
                "src/main/resources",
                "src/main/resources/public",
                "storage",
                "grind",
                ".env",
            )
        val detection =
            KonaLayout.Detection(
                root = Files.createTempDirectory("kona-struct"),
                language = KonaLayout.AppLanguage.KOTLIN,
                score = 10,
                presentPackages = listOf("bootstrap", "app", "routes", "src/main/resources"),
                hasGrind = true,
                hasDevKonaDependency = true,
            )
        val entries = KonaProjectStructure.entries(detection) { it in present }
        assertEquals(
            listOf("bootstrap", "app", "routes", "resources", "public", "storage", "grind", ".env"),
            entries.map { it.displayName },
        )
        val firstFile = entries.indexOfFirst { it.kind == KonaProjectStructure.Kind.ROOT_FILE }
        assertTrue(firstFile > 0)
        assertTrue(
            "directories must precede files in Kona Project View",
            entries.take(firstFile).all {
                it.kind != KonaProjectStructure.Kind.ROOT_FILE
            },
        )
        assertTrue(entries.drop(firstFile).all { it.kind == KonaProjectStructure.Kind.ROOT_FILE })
        assertTrue(entries.any { it.relativePath == "src/main/resources" })
        assertTrue(entries.any { it.relativePath == "src/main/resources/public" })
        assertFalse(entries.any { it.displayName == "views" || it.displayName == "css" })
        assertTrue(
            KonaProjectStructure.entries(
                detection.copy(score = 0),
            ) { true }.isEmpty(),
        )

        val javaDetection = detection.copy(language = KonaLayout.AppLanguage.JAVA)
        val javaEntries =
            KonaProjectStructure.entriesForRoot(
                { it == "src/main/java/app" || it == "storage" },
                javaDetection,
            )
        assertEquals(listOf("app", "storage"), javaEntries.map { it.displayName })
        assertTrue(javaEntries.first().relativePath.startsWith("src/main/java/"))
    }
}
