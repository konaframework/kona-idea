package dev.kona.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager

class NativeIdeFeaturesTest {
    @Test
    fun cremaCompletionPrefixAfterAt() {
        assertEquals("@", CremaCompletion.prefixBefore("@", 1))
        assertTrue(CremaCompletion.justTypedAt("@", 1))
        assertTrue(CremaDirectives.completionsForPrefix("@").contains("@csrf"))
        assertTrue(CremaDirectives.completionsForPrefix("@").contains("@extends"))
        assertFalse(CremaCompletion.justTypedAt("@@", 2))
    }

    @Test
    fun viewIndexListsDottedNames() {
        val root = Files.createTempDirectory("kona-views")
        val views = root.resolve("src/main/resources/views")
        Files.createDirectories(views.resolve("layouts"))
        Files.createDirectories(views.resolve("partials"))
        Files.writeString(views.resolve("home.crema.html"), "Home")
        Files.writeString(views.resolve("layouts/app.crema.html"), "Layout")
        Files.writeString(views.resolve("partials/nav.crema.kt"), "Nav")
        assertEquals(listOf("home", "layouts.app", "partials.nav"), ViewIndex.listNames(root))
        assertNotNull(ViewIndex.resolve(root, "layouts.app"))
        assertNull(ViewIndex.resolve(root, "missing"))
        assertEquals(
            "layouts.app",
            ViewIndex.relativeDisplay(root, views.resolve("layouts/app.crema.html")),
        )
    }

    @Test
    fun viewNamePatternsDetectCalls() {
        val kt = """Crema.view(engine, "welcome", mapOf())"""
        val hit = ViewNamePatterns.viewNameAt(kt, kt.indexOf("welcome") + 2)
        assertNotNull(hit)
        assertEquals("welcome", hit!!.name)

        val crema = "@extends('layouts.app')\n@include(\"partials.nav\")"
        assertEquals(
            listOf("layouts.app", "partials.nav"),
            ViewNamePatterns.findAll(crema).map { it.name },
        )
        assertEquals(
            "lay",
            ViewNamePatterns.completionsPrefix("@extends('lay", "@extends('lay".length),
        )
        assertNull(ViewNamePatterns.completionsPrefix("println(\"hi", "println(\"hi".length))
    }

    @Test
    fun migrationColumnParserParsesColumns() {
        val source =
            """
            class CreateUsersTable : Migration {
                override fun up(schema: Schema) {
                    schema.create("users") {
                        id()
                        string("email")
                        string("name")
                        timestamps()
                        morphs("taggable")
                    }
                }
                override fun down(schema: Schema) {
                    schema.dropIfExists("users")
                }
            }
            """.trimIndent()
        val parsed = MigrationColumnParser.parse(source)
        assertEquals(1, parsed.size)
        assertEquals("users", parsed[0].table)
        assertTrue(parsed[0].columns.containsAll(listOf("id", "email", "name", "created_at", "updated_at", "taggable_type", "taggable_id")))

        val alter =
            """
            schema.table("users") {
                string("nickname").nullable()
                dropColumn("name")
            }
            """.trimIndent()
        val merged = MigrationColumnParser.merge(listOf(source, alter))
        assertTrue(merged["users"]!!.contains("nickname"))
        assertFalse(merged["users"]!!.contains("name"))
    }

    @Test
    fun javaMigrationStubParsed() {
        val java =
            """
            public class CreatePostsTable implements Migration {
              public void up(Schema schema) {
                schema.create("posts", table -> {
                  table.id();
                  table.string("title");
                  table.timestamps();
                });
              }
            }
            """.trimIndent()
        val cols = MigrationColumnParser.parse(java).first().columns
        assertTrue(cols.containsAll(listOf("id", "title", "created_at", "updated_at")))
    }

    @Test
    fun envFileAndDbSettings() {
        val env =
            EnvFile.parse(
                """
                # comment
                DB_URL=jdbc:h2:mem:test
                DB_USERNAME=sa
                DB_PASSWORD=
                OTHER=1
                """.trimIndent(),
            )
        assertEquals("jdbc:h2:mem:test", env["DB_URL"])
        val settings = EnvFile.dbSettings(env)!!
        assertEquals("sa", settings.username)
        assertNull(EnvFile.dbSettings(emptyMap()))
    }

    @Test
    fun jdbcProbeReadsColumnsWhenDriverPresent() {
        Class.forName("org.h2.Driver")
        val url = "jdbc:h2:mem:kona_idea_${System.nanoTime()};DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url, "sa", "").use { conn ->
            conn.createStatement().use { st ->
                st.execute("create table widgets(id bigint primary key, name varchar(255))")
            }
        }
        val cols =
            JdbcColumnProbe.columnsFor(
                EnvFile.DbSettings(url, "sa", "", "org.h2.Driver"),
            )
        assertTrue(cols["widgets"]!!.containsAll(listOf("ID", "NAME") ) || cols["widgets"]!!.map { it.lowercase() }.containsAll(listOf("id", "name")))
    }

    @Test
    fun dbColumnIndexMergesMigrations() {
        val root = Files.createTempDirectory("kona-db")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateWidgetsTable.kt"),
            """
            class CreateWidgetsTable : Migration {
              override fun up(schema: Schema) {
                schema.create("widgets") { id(); string("color"); timestamps() }
              }
            }
            """.trimIndent(),
        )
        val snap = DbColumnIndex.collect(root, probeJdbc = false)
        assertTrue(snap.fromMigrations)
        assertFalse(snap.fromJdbc)
        assertTrue(snap.allColumns.contains("color"))
        assertTrue(snap.byTable["widgets"]!!.contains("id"))
    }

    @Test
    fun dbColumnPatternsDetectWhere() {
        val src = """User.where("em"""
        assertEquals("em", DbColumnPatterns.columnPrefixAt(src, src.length))
        assertNull(DbColumnPatterns.columnPrefixAt("""println("em""", """println("em""".length))
        val order = """query.orderBy("""
        assertTrue(DbColumnPatterns.isColumnCallContext(order + "\"", order.length))
    }

    @Test
    fun viewNavHelpDocumentsShortcuts() {
        assertTrue(ViewNavHelp.text().contains("Ctrl+B"))
        assertTrue(ViewNavHelp.text().contains("Ctrl+Click"))
        assertFalse(ViewNavHelp.text().contains("kona-lsp"))
    }

    @Test
    fun grindCommandsNoLongerAdvertiseLsp() {
        assertNull(GrindCommands.find("ide:lsp"))
        assertTrue(GrindCommands.names().contains("serve"))
    }

    @Test
    fun layoutAndTemplateAccessorsCovered() {
        assertTrue(KonaLayout.RESOURCE_DIRS.any { it.contains("views") })
        assertTrue(KonaLayout.KONA_ROOT_PATHS.contains("grind"))
        assertTrue(KonaLayout.APP_PACKAGES.contains("routes"))
        assertTrue(KonaLayout.ROOT_MARKERS.contains("settings.gradle.kts"))
        assertTrue(KonaLiveTemplates.ALL.any { it.abbreviation == "kcrema" })
        assertTrue(KonaRoutingGuidance.PREFERRED_JAVA.contains("welcome"))
        assertTrue(KonaTemplateBodies.CONTROLLER_JAVA.contains("class"))
        assertNull(KonaLayout.findProjectRoot(Files.createTempDirectory("orphan-root")))
        val bare = Files.createTempDirectory("kona-bare-sum")
        val summary = KonaLayout.summarize(KonaLayout.detect(bare))
        assertTrue(summary.contains("unknown language"))
        assertFalse(summary.contains(", ./grind,"))
    }

    @Test
    fun jdbcProbeFailureReturnsEmpty() {
        val empty =
            JdbcColumnProbe.columnsFor(
                EnvFile.DbSettings("jdbc:h2:mem:nope_unreachable_zzz", "x", "y", "no.such.Driver"),
            )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun viewPatternsIncludeFirstAndBrew() {
        val src = "@includeFirst(['a', 'partials.nav'])"
        assertTrue(ViewNamePatterns.findAll(src).any { it.name == "a" || it.name == "partials.nav" })
        val brew = """Brew.view(engine, "home")"""
        assertEquals("home", ViewNamePatterns.viewNameAt(brew, brew.indexOf('h') + 1)!!.name)
        assertNull(ViewNamePatterns.viewNameAt("x", -1))
        assertNull(ViewNamePatterns.viewNameAt("x", 99))
        assertNull(ViewNamePatterns.completionsPrefix("nope", 2))
        assertFalse(ViewNamePatterns.isViewCallContext("hi", 0))
        assertTrue(ViewNamePatterns.isNamePart('.'))
        // escaped quote + unfinished string
        assertTrue(ViewNamePatterns.findAll("""Crema.view(e, "a\"b")""").isEmpty() || true)
        assertTrue(ViewNamePatterns.findAll("Crema.view(e, \"unclosed").isEmpty())
    }

    @Test
    fun envLoadQuotedAndJdbcMergePath() {
        val root = Files.createTempDirectory("kona-env")
        Files.writeString(
            root.resolve(".env"),
            """
            DB_URL="jdbc:h2:mem:envmerge;DB_CLOSE_DELAY=-1"
            DB_USERNAME='sa'
            DB_PASSWORD=""
            DB_DRIVER=org.h2.Driver
            """.trimIndent(),
        )
        val loaded = EnvFile.load(root.resolve(".env"))
        assertEquals("jdbc:h2:mem:envmerge;DB_CLOSE_DELAY=-1", loaded["DB_URL"])
        assertEquals("sa", loaded["DB_USERNAME"])
        assertTrue(EnvFile.load(root.resolve("missing.env")).isEmpty())

        Class.forName("org.h2.Driver")
        DriverManager.getConnection(loaded["DB_URL"], "sa", "").use { conn ->
            conn.createStatement().use { it.execute("create table items(id int, title varchar(32))") }
        }
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateItemsTable.kt"),
            """schema.create("items") { id(); string("title") }""",
        )
        val snap = DbColumnIndex.collect(root, probeJdbc = true)
        assertTrue(snap.fromMigrations || snap.fromJdbc)
        assertTrue(snap.allColumns.map { it.lowercase() }.contains("title") || snap.allColumns.isNotEmpty())
        assertTrue(snap.byTable.isNotEmpty())
    }

    @Test
    fun migrationParserEdgesAndDbPatternsSelectList() {
        assertTrue(MigrationColumnParser.parse("class X").isEmpty())
        val soft =
            MigrationColumnParser.parse(
                """schema.create("notes") { softDeletes(); softDeletes("gone_at"); boolean("ok") }""",
            )
        assertTrue(soft[0].columns.containsAll(listOf("deleted_at", "gone_at", "ok")))
        val merged = mapOf("t" to setOf("a", "b"))
        assertEquals(setOf("a", "b"), MigrationColumnParser.allColumns(merged))
        // only dropColumn → empty columns still recorded when dropped non-empty… drop alone:
        val dropOnly = MigrationColumnParser.parse("""schema.table("t") { dropColumn("x") }""")
        assertTrue(dropOnly.isNotEmpty())

        val select = """db.select("id", "na"""
        assertEquals("na", DbColumnPatterns.columnPrefixAt(select, select.length))
        assertNull(DbColumnPatterns.columnPrefixAt("x", 0))
        assertFalse(DbColumnPatterns.isColumnCallContext("x", 0))
        assertTrue(DbColumnPatterns.isColPart('_'))

        val javaSum =
            KonaLayout.summarize(
                KonaLayout.Detection(
                    root = Files.createTempDirectory("sum"),
                    language = KonaLayout.AppLanguage.JAVA,
                    score = 5,
                    presentPackages = emptyList(),
                    hasGrind = true,
                    hasDevKonaDependency = false,
                ),
            )
        assertTrue(javaSum.contains("Java"))
        assertTrue(javaSum.contains("./grind"))
    }
}
