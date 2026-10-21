package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Completions against the files `kona new` actually writes — lowercase
 * `app/models`, the users migration stub, Java `QUERY.find`, `this.` and
 * bare `getAttribute("` inside the model, and relative JDBC URLs.
 */
class CadenceScaffoldCompletionTest {
    @Test
    fun kotlinScaffoldUserCompletesMigrationColumns() {
        val root = kotlinApp()
        val controller =
            """
            package app.http.controllers
            class WelcomeController {
                fun show() {
                    val user = Auth.user() as? User
                    user.
            """.trimIndent()
        assertSuggests(root, controller, "email", "name", "password", "remember_token", "id")

        val where = """User.where("em"""
        assertSuggests(root, where, "email")

        val query = """val user = User.query().first()${'\n'}user.getAttribute("na"""
        assertSuggests(root, query, "name")
    }

    @Test
    fun columnsCompleteInsideTheModelSource() {
        val root = kotlinApp()
        val userKt = Files.readString(root.resolve("src/main/kotlin/app/models/User.kt"))
        val marker = "getAttribute(\"id\")"
        val at = userKt.indexOf(marker) + "getAttribute(\"".length
        val names = CadenceCompletion.collect(root, userKt, at).map { it.name }.toSet()
        assertTrue("bare getAttribute inside User.kt: $names", names.containsAll(setOf("email", "name", "id")))

        val withThis =
            userKt.replace(
                "override fun getEmail(): String? = getAttribute(\"email\")?.toString()",
                "override fun getEmail(): String? {\n        this.\n        return getAttribute(\"email\")?.toString()\n    }",
            )
        val thisAt = withThis.indexOf("this.") + "this.".length
        val thisNames = CadenceCompletion.collect(root, withThis, thisAt).map { it.name }.toSet()
        assertTrue("this. inside User.kt: $thisNames", thisNames.contains("email"))
    }

    @Test
    fun javaQueryCompanionFindResolvesUser() {
        val root = javaApp()
        val src =
            """
            import app.models.User;
            class C {
              void run() {
                User user = User.QUERY.find(1);
                user.get("em
            """.trimIndent()
        assertEquals("User", ModelPropertyPatterns.resolveModelClass(src, "user"))
        assertSuggests(root, src, "email")
        val prop =
            """
            User user = User.QUERY.first();
            user.na
            """.trimIndent()
        assertSuggests(root, prop, "name")
        assertSuggests(root, """User.QUERY.where("em""", "email")
    }

    @Test
    fun javaMigrationAndModelParse() {
        val root = javaApp()
        val snap = DbColumnIndex.collect(root, mode = ColumnSourceMode.MIGRATIONS_ONLY, probeJdbc = false)
        assertTrue(snap.columnsForTable("users").containsAll(setOf("id", "email", "name", "password")))
        assertEquals("users", ModelIndex.find(root, "User")!!.table)
        assertEquals("User", ModelIndex.enclosingCadenceClass(Files.readString(root.resolve("src/main/java/app/models/User.java")), 400))
    }

    @Test
    fun relativeH2FileUrlResolvesAndReadsColumns() {
        val root = Files.createTempDirectory("kona-rel-h2")
        Files.writeString(root.resolve("build.gradle.kts"), "dependencies { implementation(\"org.konaframework:kona-http:1\") }\n")
        Files.createDirectories(root.resolve("storage/database"))
        Files.createDirectories(root.resolve("src/main/kotlin/app/models"))
        val url = "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE"
        Files.writeString(root.resolve(".env"), "DB_URL=$url\nDB_USERNAME=sa\nDB_PASSWORD=\n")
        val resolved = EnvFile.resolveJdbcUrl(url, root)
        assertTrue(resolved.contains(root.toAbsolutePath().normalize().toString()))
        Class.forName("org.h2.Driver")
        java.sql.DriverManager.getConnection(resolved, "sa", "").use { conn ->
            conn.createStatement().use { it.execute("create table users(id int, email varchar(64), name varchar(64))") }
        }
        val snap = DbColumnIndex.collect(root, mode = ColumnSourceMode.CONNECTION_ONLY, probeJdbc = true)
        assertTrue(snap.fromJdbc)
        val cols = snap.columnsForTable("users").map { it.lowercase() }.toSet()
        assertTrue(cols.containsAll(setOf("id", "email", "name")))
    }

    @Test
    fun sqliteDefaultUrlWhenOnlyConnectionIsSet() {
        val settings = EnvFile.dbSettings(mapOf("DB_CONNECTION" to "sqlite"))!!
        assertTrue(settings.url.contains("jdbc:sqlite:"))
        assertEquals("org.sqlite.JDBC", settings.driver)
        assertNull(EnvFile.dbSettings(emptyMap()))
        assertEquals("org.postgresql.Driver", EnvFile.driverClassFor("jdbc:postgresql://h/db"))
        assertEquals("jdbc:sqlite::memory:", EnvFile.resolveJdbcUrl("jdbc:sqlite::memory:", Path.of("/tmp")))
        assertEquals("jdbc:sqlite:file:memdb", EnvFile.resolveJdbcUrl("jdbc:sqlite:file:memdb", Path.of("/tmp")))
        val absSqlite = "jdbc:sqlite:" + Path.of("/tmp/abs.sqlite")
        assertEquals(absSqlite, EnvFile.resolveJdbcUrl(absSqlite, Path.of("/home/app")))
        val absH2 = "jdbc:h2:file:/tmp/kona;MODE=MySQL"
        assertEquals(absH2, EnvFile.resolveJdbcUrl(absH2, Path.of("/home/app")))
        assertEquals("jdbc:postgresql://h/db", EnvFile.resolveJdbcUrl("jdbc:postgresql://h/db", Path.of("/tmp")))
        assertEquals("jdbc:h2:file:./storage/x", EnvFile.resolveJdbcUrl("jdbc:h2:file:./storage/x", null))
        assertEquals("org.h2.Driver", EnvFile.driverClassFor("jdbc:h2:mem:x"))
        assertEquals("com.mysql.cj.jdbc.Driver", EnvFile.driverClassFor("jdbc:mysql://h/db"))
        assertEquals("org.mariadb.jdbc.Driver", EnvFile.driverClassFor("jdbc:mariadb://h/db"))
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", EnvFile.driverClassFor("jdbc:sqlserver://h"))
        assertEquals("oracle.jdbc.OracleDriver", EnvFile.driverClassFor("jdbc:oracle:thin:@h"))
        assertNull(EnvFile.driverClassFor("jdbc:unknown:x"))
        val h2Default = EnvFile.dbSettings(mapOf("DB_CONNECTION" to "h2"))!!
        assertTrue(h2Default.url.startsWith("jdbc:h2:file:"))
        assertEquals("org.h2.Driver", h2Default.driver)
        val sqlite3 = EnvFile.dbSettings(mapOf("DB_CONNECTION" to "sqlite3"), Path.of("/tmp/app"))!!
        assertTrue(sqlite3.url.contains("/tmp/app"))
        val withDriver =
            EnvFile.dbSettings(mapOf("DB_URL" to "jdbc:h2:mem:x", "DB_DRIVER" to "org.h2.Driver"))!!
        assertEquals("org.h2.Driver", withDriver.driver)
        val dottedIgnored =
            EnvFile.dbSettings(mapOf("DB_URL" to "jdbc:h2:mem:x", "DB_DRIVER" to "h2"))!!
        assertEquals("org.h2.Driver", dottedIgnored.driver)
    }

    @Test
    fun completionRootFindsAppWithoutGrindScript() {
        val root = Files.createTempDirectory("kona-nogrind")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"app\"\n")
        Files.createDirectories(root.resolve("src/main/kotlin/database/migrations"))
        Files.createDirectories(root.resolve("src/main/kotlin/app/http/controllers"))
        val nested = root.resolve("src/main/kotlin/app/http/controllers")
        assertTrue(KonaLayout.looksLikeKonaSources(root))
        assertEquals(
            root.toAbsolutePath().normalize(),
            KonaLayout.completionRootNear(nested),
        )
        assertFalse(KonaLayout.isKonaApp(root))
        assertNull(KonaLayout.completionRootNear(null))
        val noGradle = Files.createTempDirectory("kona-nogrindle")
        Files.createDirectories(noGradle.resolve("src/main/kotlin/database/migrations"))
        assertFalse(KonaLayout.looksLikeKonaSources(noGradle))

        val app = kotlinApp()
        val appNested = app.resolve("src/main/kotlin/app/http/controllers")
        Files.createDirectories(appNested)
        assertEquals(
            app.toAbsolutePath().normalize(),
            KonaLayout.completionRootNear(appNested.resolve("WelcomeController.kt")),
        )
        val found = KonaLayout.completionRootNear(appNested)!!
        val src = "val user = Auth.user() as? User\nuser."
        val names = CadenceCompletion.collect(found, src, src.length).map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("email", "name", "password")))
    }

    @Test
    fun jsonAndLongTextColumnsAreParsed() {
        val parsed =
            MigrationColumnParser.parse(
                """schema.create("posts") { id(); json("payload"); longText("body") }""",
            )
        assertTrue(parsed[0].columns.containsAll(setOf("id", "payload", "body")))
    }

    @Test
    fun queryReceiverAndClasspathFile() {
        assertEquals("User", ModelPropertyPatterns.simpleModelName("User.QUERY"))
        assertEquals("User", ModelPropertyPatterns.simpleModelName("app.models.User.Companion"))
        assertEquals("User", ModelPropertyPatterns.simpleModelName("User"))
        val root = Files.createTempDirectory("kona-cp")
        val jar = root.resolve("dummy.jar")
        Files.writeString(jar, "not-a-jar")
        Files.createDirectories(root.resolve("build"))
        Files.writeString(
            root.resolve("build/kona-runtime.classpath"),
            jar.toAbsolutePath().toString() + java.io.File.pathSeparator + root.resolve("missing.jar"),
        )
        assertEquals(listOf(jar.toAbsolutePath()), ProjectJdbcDrivers.classpathJars(root).map { it.toAbsolutePath() })
        assertTrue(ProjectJdbcDrivers.classpathJars(Files.createTempDirectory("empty-cp")).isEmpty())
        Class.forName("org.h2.Driver")
        ProjectJdbcDrivers.ensure(root, "org.h2.Driver")
        val shim = ProjectJdbcDrivers.Shim(java.sql.DriverManager.getDriver("jdbc:h2:mem:shim"))
        assertTrue(shim.acceptsURL("jdbc:h2:mem:x"))
        assertFalse(shim.acceptsURL("jdbc:nope:x"))
        assertEquals(shim.majorVersion, shim.majorVersion)
        assertEquals(shim.minorVersion, shim.minorVersion)
        assertEquals(shim.jdbcCompliant(), shim.jdbcCompliant())
        shim.getPropertyInfo("jdbc:h2:mem:x", java.util.Properties())
        runCatching { shim.parentLogger }
        assertNull(shim.connect("jdbc:nope:x", null))
        shim.connect("jdbc:h2:mem:shimok", java.util.Properties())?.close()
        ProjectJdbcDrivers.ensure(null, "org.h2.Driver")
        ProjectJdbcDrivers.ensure(root, "org.h2.Driver")
        ProjectJdbcDrivers.ensure(root, "com.example.MissingJdbcDriver")
        assertTrue(ProjectJdbcDrivers.alreadyAvailable("org.h2.Driver"))
    }

    @Test
    fun sqliteColumnsLoadThroughClasspathShim() {
        val sqliteJar = System.getProperty("kona.sqlite.jdbc.jar")
        assertNotNull(sqliteJar)
        assertTrue(Files.isRegularFile(Path.of(sqliteJar)))
        val root = Files.createTempDirectory("kona-sqlite-shim")
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n")
        Files.createDirectories(root.resolve("build"))
        Files.writeString(root.resolve("build/kona-runtime.classpath"), sqliteJar)
        Files.createDirectories(root.resolve("storage/database"))
        Files.createDirectories(root.resolve("src/main/kotlin/app/models"))
        Files.writeString(root.resolve("src/main/kotlin/app/models/User.kt"), "class User : Cadence<User>()")
        Files.writeString(root.resolve(".env"), "DB_CONNECTION=sqlite\n")
        val settings = EnvFile.dbSettings(mapOf("DB_CONNECTION" to "sqlite"), root)!!
        assertTrue(settings.url.contains(root.toAbsolutePath().normalize().toString()))
        ProjectJdbcDrivers.ensure(root, settings.driver)
        assertTrue(ProjectJdbcDrivers.alreadyAvailable("org.sqlite.JDBC"))
        java.sql.DriverManager.getConnection(settings.url).use { conn ->
            conn.createStatement().use {
                it.execute("create table users(id integer, email text, name text)")
            }
        }
        val snap = DbColumnIndex.collect(root, mode = ColumnSourceMode.CONNECTION_ONLY, probeJdbc = true)
        assertTrue("expected JDBC columns, got $snap", snap.fromJdbc)
        val cols = snap.columnsForTable("users").map { it.lowercase() }.toSet()
        assertTrue(cols.containsAll(setOf("id", "email", "name")))
        val items =
            CadenceCompletion.collect(
                root,
                """User.where("em""",
                """User.where("em""".length,
                mode = ColumnSourceMode.CONNECTION_ONLY,
                probeJdbc = true,
            )
        assertTrue(items.any { it.name.equals("email", ignoreCase = true) })
        val props =
            CadenceCompletion.collect(
                root,
                "val user = User.find(1)\nuser.",
                "val user = User.find(1)\nuser.".length,
                mode = ColumnSourceMode.CONNECTION_ONLY,
                probeJdbc = true,
            )
        assertTrue(props.any { it.name.equals("email", ignoreCase = true) })
    }

    @Test
    fun dummyIdentifierOnScaffoldStillSeesColumns() {
        val root = kotlinApp()
        val src = "val user = Auth.user() as? User\nuser."
        val dummy = src + CompletionText.DUMMY
        val names =
            CadenceCompletion.collect(root, dummy, dummy.length).map { it.name }.toSet()
        assertTrue(names.contains("email"))
        assertSuggests(root, """User.Companion.where("na""", "name")
    }

    @Test
    fun javaBareGetAttributeInsideUserSource() {
        val root = javaApp()
        val userJava = Files.readString(root.resolve("src/main/java/app/models/User.java"))
        val marker = "getAttribute(\"id\")"
        val at = userJava.indexOf(marker) + "getAttribute(\"".length
        val names = CadenceCompletion.collect(root, userJava, at).map { it.name }.toSet()
        assertTrue("bare getAttribute inside User.java: $names", names.contains("email"))
        val withThis =
            userJava.replace(
                "return getAttribute(\"id\");",
                "this.getAttribute(\"em",
            )
        val thisAt = withThis.indexOf("this.getAttribute(\"em") + "this.getAttribute(\"em".length
        val thisNames = CadenceCompletion.collect(root, withThis, thisAt).map { it.name }.toSet()
        assertTrue("this.getAttribute inside User.java: $thisNames", thisNames.contains("email"))
    }

    @Test
    fun enclosingClassAndThisResolution() {
        val src =
            """
            class User : Cadence<User>() {
                fun nick() {
                    this.
            """.trimIndent()
        assertEquals("User", ModelIndex.enclosingCadenceClass(src, src.length))
        assertEquals("User", ModelPropertyPatterns.resolveModelClass(src, "this", src.length))
        assertEquals("User", ModelPropertyCompletionContributor.resolveModelClass(src, null, src.length))
        assertEquals("User", ModelPropertyCompletionContributor.resolveModelClass(src, "this", src.length))
        assertNull(ModelIndex.enclosingCadenceClass("", 0))
        assertNull(ModelIndex.enclosingCadenceClass("class Foo", 0))
    }

    private fun kotlinApp(): Path {
        val root = Files.createTempDirectory("kona-kt-app")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """plugins { java }\ndependencies { implementation("org.konaframework:kona-http:1") }\n""",
        )
        Files.writeString(root.resolve("grind"), "#!/bin/sh\n")
        val models = root.resolve("src/main/kotlin/app/models")
        Files.createDirectories(models)
        Files.writeString(models.resolve("User.kt"), KOTLIN_USER)
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(mig.resolve("2000_01_01_000000_create_users_table.kt"), KOTLIN_USERS)
        return root
    }

    private fun javaApp(): Path {
        val root = Files.createTempDirectory("kona-java-app")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """plugins { java }\ndependencies { implementation("org.konaframework:kona-http:1") }\n""",
        )
        Files.writeString(root.resolve("grind"), "#!/bin/sh\n")
        val models = root.resolve("src/main/java/app/models")
        Files.createDirectories(models)
        Files.writeString(models.resolve("User.java"), JAVA_USER)
        val mig = root.resolve("src/main/java/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(mig.resolve("2000_01_01_000000_create_users_table.java"), JAVA_USERS)
        return root
    }

    private fun assertSuggests(root: Path, src: String, vararg expected: String) {
        val found = CadenceCompletion.collect(root, src, src.length).map { it.name }.toSet()
        assertTrue("expected ${expected.toSet()} in $found for `$src`", found.containsAll(expected.toSet()))
    }

    companion object {
        private val KOTLIN_USER =
            """
            package app.models

            import org.konaframework.auth.Authenticatable
            import org.konaframework.auth.HasCredentials
            import org.konaframework.auth.HasEmail
            import org.konaframework.cadence.Cadence
            import org.konaframework.cadence.CadenceCompanion

            class User : Cadence<User>(), Authenticatable, HasCredentials, HasEmail {
                override val fillable = setOf("name", "email", "password", "remember_token")
                override val hidden = setOf("password", "remember_token")

                companion object : CadenceCompanion<User>(User::class.java)

                override fun getAuthIdentifier(): Any = getAttribute("id")!!

                override fun getAuthPassword(): String = getAttribute("password")?.toString() ?: ""

                override fun getRememberToken(): String? = getAttribute("remember_token")?.toString()

                override fun setRememberToken(token: String?) {
                    setAttribute("remember_token", token)
                }

                override fun getCredential(key: String): Any? = getAttribute(key)

                override fun getEmail(): String? = getAttribute("email")?.toString()
            }
            """.trimIndent()

        private val KOTLIN_USERS =
            """
            package database.migrations

            import org.konaframework.cadence.migrations.Migration
            import org.konaframework.cadence.migrations.MigrationName
            import org.konaframework.cadence.schema.Schema

            @MigrationName("2000_01_01_000000_create_users_table")
            class CreateUsersTable : Migration {
                override fun up(schema: Schema) {
                    schema.create("users") {
                        id()
                        string("name")
                        string("email").unique()
                        string("password")
                        string("remember_token").nullable()
                        timestamps()
                    }
                }

                override fun down(schema: Schema) {
                    schema.dropIfExists("users")
                }
            }
            """.trimIndent()

        private val JAVA_USER =
            """
            package app.models;

            import org.konaframework.auth.Authenticatable;
            import org.konaframework.auth.HasCredentials;
            import org.konaframework.auth.HasEmail;
            import org.konaframework.cadence.Cadence;
            import org.konaframework.cadence.CadenceCompanion;

            import java.util.Set;

            public class User extends Cadence<User> implements Authenticatable, HasCredentials, HasEmail {
                public static final CadenceCompanion<User> QUERY = Cadence.of(User.class);

                @Override
                public Set<String> getFillable() {
                    return Set.of("name", "email", "password", "remember_token");
                }

                @Override
                public Set<String> getHidden() {
                    return Set.of("password", "remember_token");
                }

                @Override
                public Object getAuthIdentifier() {
                    return getAttribute("id");
                }

                @Override
                public String getAuthPassword() {
                    Object password = getAttribute("password");
                    return password == null ? "" : password.toString();
                }

                @Override
                public String getRememberToken() {
                    Object token = getAttribute("remember_token");
                    return token == null ? null : token.toString();
                }

                @Override
                public void setRememberToken(String token) {
                    setAttribute("remember_token", token);
                }

                @Override
                public Object getCredential(String key) {
                    return getAttribute(key);
                }

                @Override
                public String getEmail() {
                    Object email = getAttribute("email");
                    return email == null ? null : email.toString();
                }
            }
            """.trimIndent()

        private val JAVA_USERS =
            """
            package database.migrations;
            import org.konaframework.cadence.migrations.Migration;
            import org.konaframework.cadence.migrations.MigrationName;
            import org.konaframework.cadence.schema.Schema;

            @MigrationName("2000_01_01_000000_create_users_table")
            class CreateUsersTable implements Migration {
                @Override
                public void up(Schema schema) {
                    schema.create("users", table -> {
                        table.id();
                        table.string("name");
                        table.string("email").unique();
                        table.string("password");
                        table.string("remember_token").nullable();
                        table.timestamps();
                    });
                }
            }
            """.trimIndent()
    }
}
