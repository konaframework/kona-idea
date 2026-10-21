package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path

/**
 * Canonical Kona application layout from `kona new` / grind scaffolds.
 *
 * Mirrors the tree under `src/main/{kotlin|java}` plus `resources/`, the
 * project-root HTTP document directory `public/`, `storage/`, and app-local
 * `./grind`. Detection is used for run configs and project markers — not for
 * inventing source roots (Gradle owns those).
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

    /**
     * Resource folders under `src/main/resources`. Views, lang, css, and js live
     * here. The HTTP document root is [PUBLIC_DIR] at the project root, not a
     * `public` child of this tree.
     */
    val RESOURCE_DIRS: List<String> =
        listOf(
            "src/main/resources/views",
            "src/main/resources/lang",
            "src/main/resources/css",
            "src/main/resources/js",
        )

    /** Project-root HTTP document directory (Vite writes `public/build`). */
    const val PUBLIC_DIR: String = "public"

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
    const val MAVEN_GROUP: String = "org.konaframework"

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

    /**
     * Walk parents from [start] until [isKonaApp] succeeds. Used by completion so a
     * file in a Kona app still resolves when IDEA's guessed project dir is a parent
     * folder (monorepo / multi-module).
     */
    fun appRootNear(start: Path?): Path? {
        var dir: Path? = start?.toAbsolutePath()?.normalize()
        while (dir != null) {
            if (isKonaApp(dir)) return dir
            dir = dir.parent
        }
        return null
    }

    /**
     * Like [appRootNear], but also accepts a Gradle project that has Cadence
     * models or migrations even when `./grind` is missing (so completion still
     * finds `database/migrations` when IDEA's guessed directory is a parent).
     */
    fun completionRootNear(start: Path?): Path? {
        var dir: Path? = start?.toAbsolutePath()?.normalize()
        while (dir != null) {
            if (isKonaApp(dir) || looksLikeKonaSources(dir)) return dir
            dir = dir.parent
        }
        return null
    }

    fun looksLikeKonaSources(root: Path): Boolean {
        if (ROOT_MARKERS.none { Files.isRegularFile(root.resolve(it)) }) {
            return false
        }
        val markers =
            listOf(
                "src/main/kotlin/database/migrations",
                "src/main/java/database/migrations",
                "src/main/kotlin/app/models",
                "src/main/java/app/models",
                "src/main/kotlin/app/Models",
                "src/main/java/app/Models",
            )
        return markers.any { Files.isDirectory(root.resolve(it)) }
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
                RESOURCE_DIRS.filter { Files.isDirectory(abs.resolve(it)) } +
                listOf(PUBLIC_DIR).filter { Files.isDirectory(abs.resolve(it)) }

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
        return APP_PACKAGES.map { "$src/$it" } + RESOURCE_DIRS + listOf(PUBLIC_DIR, "storage")
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
                runCatching { Files.readString(file) }.getOrDefault("").let { text ->
                    text.contains("org.konaframework") || text.contains("dev.kona")
                }
        }
    }
}
