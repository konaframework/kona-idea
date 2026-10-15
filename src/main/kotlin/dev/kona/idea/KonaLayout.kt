package dev.kona.idea

import java.nio.file.Files
import java.nio.file.Path

/**
 * Canonical Kona application layout from `kona new` / grind scaffolds.
 *
 * Mirrors the tree under `src/main/{kotlin|java}` plus `resources/`, `storage/`,
 * and app-local `./grind`. Detection is used for run configs and project markers —
 * not for inventing source roots (Gradle owns those).
 */
object KonaLayout {
    /** Laravel-shaped packages under the language source root. */
    val APP_PACKAGES: List<String> =
        listOf(
            "bootstrap",
            "app",
            "app/http/controllers",
            "app/http/middleware",
            "app/http/resources",
            "app/models",
            "app/providers",
            "routes",
            "config",
            "database",
            "database/migrations",
            "database/factories",
            "database/seeders",
        )

    val RESOURCE_DIRS: List<String> =
        listOf(
            "src/main/resources/views",
            "src/main/resources/public",
            "src/main/resources/lang",
            "src/main/resources/css",
            "src/main/resources/js",
        )

    val ROOT_MARKERS: List<String> =
        listOf(
            "settings.gradle.kts",
            "settings.gradle",
            "build.gradle.kts",
            "build.gradle",
        )

    val KONA_ROOT_PATHS: List<String> =
        listOf(
            "grind",
            "grind.bat",
            "storage",
            ".env",
            ".env.example",
        )

    /** Maven group for published Kona modules. */
    const val MAVEN_GROUP: String = "dev.kona"

    enum class AppLanguage {
        KOTLIN,
        JAVA,
        UNKNOWN,
    }

    data class Detection(
        val root: Path,
        val language: AppLanguage,
        val score: Int,
        val presentPackages: List<String>,
        val hasGrind: Boolean,
        val hasDevKonaDependency: Boolean,
    ) {
        val isKonaApp: Boolean get() = score >= MIN_SCORE
    }

    const val MIN_SCORE: Int = 3

    fun findProjectRoot(start: Path): Path? {
        var dir: Path? = start.toAbsolutePath().normalize()
        while (dir != null) {
            val current = dir
            if (ROOT_MARKERS.any { Files.isRegularFile(current.resolve(it)) }) {
                return current
            }
            dir = current.parent
        }
        return null
    }

    fun detect(root: Path): Detection {
        val abs = root.toAbsolutePath().normalize()
        val kotlinRoot = abs.resolve("src/main/kotlin")
        val javaRoot = abs.resolve("src/main/java")
        val language =
            when {
                Files.isDirectory(kotlinRoot) && APP_PACKAGES.any { Files.isDirectory(kotlinRoot.resolve(it)) } ->
                    AppLanguage.KOTLIN
                Files.isDirectory(javaRoot) && APP_PACKAGES.any { Files.isDirectory(javaRoot.resolve(it)) } ->
                    AppLanguage.JAVA
                Files.isDirectory(kotlinRoot) -> AppLanguage.KOTLIN
                Files.isDirectory(javaRoot) -> AppLanguage.JAVA
                else -> AppLanguage.UNKNOWN
            }
        val sourceRoot =
            when (language) {
                AppLanguage.KOTLIN -> kotlinRoot
                AppLanguage.JAVA -> javaRoot
                AppLanguage.UNKNOWN -> kotlinRoot
            }
        val present =
            APP_PACKAGES.filter { Files.isDirectory(sourceRoot.resolve(it)) } +
                RESOURCE_DIRS.filter { Files.isDirectory(abs.resolve(it)) }

        var score = 0
        if (ROOT_MARKERS.any { Files.isRegularFile(abs.resolve(it)) }) score += 1
        val hasGrind =
            Files.isRegularFile(abs.resolve("grind")) || Files.isRegularFile(abs.resolve("grind.bat"))
        if (hasGrind) score += 2
        if (Files.isDirectory(abs.resolve("storage"))) score += 1
        if (Files.isDirectory(sourceRoot.resolve("bootstrap"))) score += 2
        if (Files.isDirectory(sourceRoot.resolve("routes"))) score += 2
        if (Files.isDirectory(sourceRoot.resolve("app"))) score += 1
        if (Files.isDirectory(sourceRoot.resolve("config"))) score += 1
        if (Files.isDirectory(abs.resolve("src/main/resources/views"))) score += 1
        val hasDep = hasDevKonaDependency(abs)
        if (hasDep) score += 3

        return Detection(
            root = abs,
            language = language,
            score = score,
            presentPackages = present,
            hasGrind = hasGrind,
            hasDevKonaDependency = hasDep,
        )
    }

    fun isKonaApp(root: Path): Boolean = detect(root).isKonaApp

    fun relativeMarkerDirs(language: AppLanguage): List<String> {
        val src =
            when (language) {
                AppLanguage.JAVA -> "src/main/java"
                else -> "src/main/kotlin"
            }
        return APP_PACKAGES.map { "$src/$it" } + RESOURCE_DIRS + listOf("storage")
    }

    fun summarize(detection: Detection): String {
        val lang =
            when (detection.language) {
                AppLanguage.KOTLIN -> "Kotlin"
                AppLanguage.JAVA -> "Java"
                AppLanguage.UNKNOWN -> "unknown language"
            }
        return buildString {
            append("Kona app ($lang)")
            if (detection.hasGrind) append(", ./grind")
            if (detection.hasDevKonaDependency) append(", $MAVEN_GROUP artifacts")
            append(" — HTTP via Netty + virtual threads on ./grind serve (default port 4000)")
        }
    }

    internal fun hasDevKonaDependency(root: Path): Boolean {
        val candidates =
            listOf(
                "build.gradle.kts",
                "build.gradle",
                "settings.gradle.kts",
                "pom.xml",
            )
        return candidates.any { name ->
            val file = root.resolve(name)
            Files.isRegularFile(file) &&
                runCatching { Files.readString(file) }.getOrDefault("").contains("dev.kona")
        }
    }
}
