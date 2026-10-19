package org.konaframework.idea

/**
 * Logical top-level entries for the Kona Project View — short scaffold names
 * instead of deep `src/main/kotlin/…` package paths.
 */
object KonaProjectStructure {
    enum class Kind {
        SOURCE_PACKAGE,
        RESOURCE,
        ROOT_FILE,
        ROOT_DIR,
    }

    data class Entry(
        /** Path relative to the project root, using `/`. */
        val relativePath: String,
        /** Label shown in the Kona Project View. */
        val displayName: String,
        val kind: Kind,
    )

    /**
     * Ordered entries that exist for [detection]. Pure path checks — callers
     * resolve [Entry.relativePath] against the VFS.
     */
    fun entries(detection: KonaLayout.Detection, exists: (String) -> Boolean): List<Entry> {
        if (!detection.isKonaApp) return emptyList()
        val src =
            when (detection.language) {
                KonaLayout.AppLanguage.JAVA -> "src/main/java"
                else -> "src/main/kotlin"
            }
        val candidates =
            listOf(
                Entry("$src/bootstrap", "bootstrap", Kind.SOURCE_PACKAGE),
                Entry("$src/app", "app", Kind.SOURCE_PACKAGE),
                Entry("$src/routes", "routes", Kind.SOURCE_PACKAGE),
                Entry("$src/config", "config", Kind.SOURCE_PACKAGE),
                Entry("$src/database", "database", Kind.SOURCE_PACKAGE),
                // One node for resources; views / public / lang / css / js appear as children.
                Entry("src/main/resources", "resources", Kind.RESOURCE),
                Entry("storage", "storage", Kind.ROOT_DIR),
                Entry("grind", "grind", Kind.ROOT_FILE),
                Entry("grind.bat", "grind.bat", Kind.ROOT_FILE),
                Entry(".env", ".env", Kind.ROOT_FILE),
                Entry(".env.example", ".env.example", Kind.ROOT_FILE),
            )
        return candidates.filter { exists(it.relativePath) }
    }

    fun entriesForRoot(rootExists: (String) -> Boolean, detection: KonaLayout.Detection): List<Entry> =
        entries(detection, rootExists)
}
