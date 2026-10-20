package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class KonaModernAlignmentTest {
    @Test
    fun grindArgsComposeServeHostPort() {
        assertEquals(
            listOf("serve", "--host=127.0.0.1", "--port=4000"),
            GrindArgs.compose("serve", "127.0.0.1", "4000"),
        )
        assertEquals(
            listOf("serve", "--host=0.0.0.0", "--port=9000"),
            GrindArgs.compose("serve --host=0.0.0.0", "127.0.0.1", "9000"),
        )
        assertEquals(
            listOf("migrate"),
            GrindArgs.compose("migrate", "127.0.0.1", "4000"),
        )
        assertEquals(listOf("list"), GrindArgs.compose("  ", "x", "y"))
        assertTrue(GrindArgs.serveDescription().contains("Netty"))
        assertTrue(GrindArgs.serveDescription().contains("4000"))
    }

    @Test
    fun layoutDetectsScaffoldTree() {
        val root = Files.createTempDirectory("kona-app")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            "dependencies { implementation(\"org.konaframework:kona-http:0.1.0\") }\n",
        )
        Files.writeString(root.resolve("grind"), "#!/bin/sh\n")
        Files.createDirectories(root.resolve("storage/app"))
        Files.createDirectories(root.resolve("src/main/kotlin/bootstrap"))
        Files.createDirectories(root.resolve("src/main/kotlin/routes"))
        Files.createDirectories(root.resolve("src/main/kotlin/app/http/controllers"))
        Files.createDirectories(root.resolve("src/main/kotlin/config"))
        Files.createDirectories(root.resolve("src/main/resources/views"))

        val detection = KonaLayout.detect(root)
        assertTrue(detection.isKonaApp)
        assertEquals(KonaLayout.AppLanguage.KOTLIN, detection.language)
        assertTrue(detection.hasGrind)
        assertTrue(detection.hasDevKonaDependency)
        assertEquals(root.toAbsolutePath().normalize(), KonaLayout.findProjectRoot(root.resolve("src/main/kotlin/routes")))
        assertTrue(KonaLayout.summarize(detection).contains("Netty"))
        assertTrue(KonaLayout.relativeMarkerDirs(KonaLayout.AppLanguage.KOTLIN).any { it.contains("bootstrap") })
    }

    @Test
    fun layoutDetectsJavaTree() {
        val root = Files.createTempDirectory("kona-java")
        Files.writeString(root.resolve("build.gradle.kts"), "implementation(\"org.konaframework:kona-http:1\")\n")
        Files.writeString(root.resolve("grind.bat"), "@echo off\n")
        Files.createDirectories(root.resolve("src/main/java/bootstrap"))
        Files.createDirectories(root.resolve("src/main/java/routes"))
        val d = KonaLayout.detect(root)
        assertEquals(KonaLayout.AppLanguage.JAVA, d.language)
        assertTrue(d.hasGrind)
        assertTrue(d.score >= KonaLayout.MIN_SCORE)
    }

    @Test
    fun layoutRejectsPlainGradle() {
        val root = Files.createTempDirectory("plain")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"x\"\n")
        assertFalse(KonaLayout.isKonaApp(root))
        assertEquals(
            root.toAbsolutePath().normalize(),
            KonaLayout.findProjectRoot(root.resolve("src/main")),
        )
        val empty = Files.createTempDirectory("no-markers")
        assertFalse(KonaLayout.detect(empty).isKonaApp)
    }

    @Test
    fun artifactsAndCommandsCoverSurface() {
        assertTrue(KonaArtifacts.MODULES.contains("kona-http"))
        assertTrue(KonaArtifacts.MODULES.contains("kona-grind"))
        assertTrue(KonaArtifacts.MODULES.contains("kona-brew"))
        assertTrue(KonaArtifacts.MODULES.contains("kona-inertia"))
        assertTrue(KonaArtifacts.coordinate("kona-http").startsWith("org.konaframework:"))
        assertTrue(KonaArtifacts.helpText().contains("Netty"))
        assertTrue(KonaArtifacts.helpText().contains("kona-brew"))
        assertTrue(KonaArtifacts.coordinates().all { it.startsWith("org.konaframework:") })
        assertEquals("serve", GrindCommands.find("serve")!!.name)
        assertTrue(GrindCommands.names().contains("queue:work"))
        assertTrue(GrindCommands.PRESET_CONFIGS.any { it.name == "serve" })
        assertTrue(GrindCommands.BUILTIN.first { it.name == "serve" }.summary.contains("Netty"))
        assertTrue(
            GrindCommands.names().containsAll(
                listOf(
                    "migrate",
                    "migrate:rollback",
                    "migrate:reset",
                    "migrate:status",
                    "db:seed",
                    "make:migration",
                    "make:seeder",
                    "make:factory",
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
        assertEquals(
            "Drop all tables and re-run migrations (--seed)",
            GrindCommands.find("migrate:fresh")!!.summary,
        )
    }

    @Test
    fun templatesPreferTypedHandlers() {
        assertTrue(KonaTemplateBodies.usesTypedHandlers(KonaTemplateBodies.WEB_ROUTES_KT))
        assertTrue(KonaTemplateBodies.usesTypedHandlers(KonaTemplateBodies.WEB_ROUTES_JAVA))
        assertFalse(KonaTemplateBodies.mentionsClassAtMethod(KonaTemplateBodies.WEB_ROUTES_KT))
        assertFalse(KonaTemplateBodies.mentionsClassAtMethod(KonaTemplateBodies.WEB_ROUTES_JAVA))
        assertTrue(KonaTemplateBodies.CONTROLLER_KT.contains("Request"))
        assertTrue(KonaTemplateBodies.CREMA_VIEW.contains("DOCTYPE"))
        assertTrue(KonaFileTemplates.TEMPLATES.contains("Kona Crema View.crema.html"))
        assertTrue(KonaFileTemplates.TEMPLATES.contains("Kona Inertia Page.vue"))
        assertTrue(KonaRoutingGuidance.helpText().contains("typed"))
        assertFalse(KonaRoutingGuidance.PREFERRED_KOTLIN.contains("@"))
        assertTrue(KonaLiveTemplates.bodiesPreferTypedRoutes())
        assertTrue(KonaLiveTemplates.abbreviations().contains("kroute"))
        assertTrue(KonaLiveTemplates.abbreviations().contains("kdisk"))
        assertTrue(KonaLiveTemplates.abbreviations().contains("kinertia"))
        assertTrue(layoutDoc(KonaLayout.AppLanguage.JAVA).contains("src/main/java"))
        assertTrue(ViewNavHelp.text().contains("Ctrl+B"))
        assertTrue(BrewInertiaHelp.text().contains("kona-brew"))
    }

    @Test
    fun classAtMethodDetector() {
        assertTrue(KonaTemplateBodies.mentionsClassAtMethod("""router.get("/", "WelcomeController@index")"""))
        assertTrue(KonaTemplateBodies.mentionsClassAtMethod("Controllers.action(app, type, \"index\")"))
        assertFalse(KonaTemplateBodies.mentionsClassAtMethod("router.get(\"/\") { welcome.index(it) }"))
    }

    @Test
    fun layoutLanguageFallbacksAndSummaries() {
        val kotlinOnly = Files.createTempDirectory("kona-kt-only")
        Files.createDirectories(kotlinOnly.resolve("src/main/kotlin"))
        Files.writeString(kotlinOnly.resolve("grind"), "#!/bin/sh\n")
        Files.writeString(kotlinOnly.resolve("pom.xml"), "<project>org.konaframework</project>\n")
        val kt = KonaLayout.detect(kotlinOnly)
        assertEquals(KonaLayout.AppLanguage.KOTLIN, kt.language)
        assertTrue(kt.hasDevKonaDependency)

        val javaOnly = Files.createTempDirectory("kona-java-only")
        Files.createDirectories(javaOnly.resolve("src/main/java"))
        assertEquals(KonaLayout.AppLanguage.JAVA, KonaLayout.detect(javaOnly).language)

        val unknown = Files.createTempDirectory("kona-unknown")
        val u = KonaLayout.detect(unknown)
        assertEquals(KonaLayout.AppLanguage.UNKNOWN, u.language)
        val summary = KonaLayout.summarize(u)
        assertTrue(summary.contains("unknown language"))
        assertFalse(summary.contains(", ./grind,"))
        assertTrue(summary.contains("./grind serve"))

        assertTrue(
            KonaLayout.relativeMarkerDirs(KonaLayout.AppLanguage.JAVA)
                .any { it.startsWith("src/main/java/") },
        )
        assertTrue(KonaLayout.KONA_ROOT_PATHS.contains(".env"))
        assertTrue(KonaLayout.ROOT_MARKERS.contains("build.gradle"))
        assertTrue(KonaLayout.RESOURCE_DIRS.any { it.contains("views") })
        assertTrue(KonaLayout.APP_PACKAGES.contains("bootstrap"))
    }

    @Test
    fun templateBodiesAndLiveTemplateBranches() {
        assertTrue(KonaTemplateBodies.CONTROLLER_JAVA.contains("public class"))
        assertTrue(KonaTemplateBodies.usesTypedHandlers("router.get(\"/\", welcome::index)"))
        assertTrue(KonaTemplateBodies.usesTypedHandlers("handler::index"))
        assertFalse(KonaTemplateBodies.usesTypedHandlers("router.get(\"/\") { }"))
        assertTrue(KonaRoutingGuidance.PREFERRED_JAVA.contains("welcome::index"))
        assertTrue(KonaRoutingGuidance.helpText().contains(KonaRoutingGuidance.PREFERRED_JAVA))
        assertTrue(KonaLiveTemplates.ALL.any { it.abbreviation == "jroute" })
        assertTrue(KonaLiveTemplates.bodiesPreferTypedRoutes())
        // Force the Class@method guard in bodiesPreferTypedRoutes to evaluate both sides.
        assertFalse(KonaTemplateBodies.mentionsClassAtMethod(KonaLiveTemplates.ALL.first { it.abbreviation == "jroute" }.body))
    }
}
