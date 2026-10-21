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
        // All directory kinds first (scaffold order), then root files — never interleave.
        val directories =
            listOf(
                Entry("$src/bootstrap", "bootstrap", Kind.SOURCE_PACKAGE),
                Entry("$src/app", "app", Kind.SOURCE_PACKAGE),
                Entry("$src/routes", "routes", Kind.SOURCE_PACKAGE),
                Entry("$src/config", "config", Kind.SOURCE_PACKAGE),
                Entry("$src/database", "database", Kind.SOURCE_PACKAGE),
                // views / lang / css / js stay under resources.
                Entry("src/main/resources", "resources", Kind.RESOURCE),
                // HTTP document root is <project>/public, never resources/public.
                Entry(KonaLayout.PUBLIC_DIR, "public", Kind.ROOT_DIR),
                Entry("storage", "storage", Kind.ROOT_DIR),
            )
        val files =
            listOf(
                Entry("grind", "grind", Kind.ROOT_FILE),
                Entry("grind.bat", "grind.bat", Kind.ROOT_FILE),
                Entry(".env", ".env", Kind.ROOT_FILE),
                Entry(".env.example", ".env.example", Kind.ROOT_FILE),
                Entry("README.md", "README.md", Kind.ROOT_FILE),
                Entry("package.json", "package.json", Kind.ROOT_FILE),
                Entry("package-lock.json", "package-lock.json", Kind.ROOT_FILE),
                Entry("pnpm-lock.yaml", "pnpm-lock.yaml", Kind.ROOT_FILE),
                Entry("yarn.lock", "yarn.lock", Kind.ROOT_FILE),
                Entry("bun.lock", "bun.lock", Kind.ROOT_FILE),
                Entry("bun.lockb", "bun.lockb", Kind.ROOT_FILE),
                Entry("tsconfig.json", "tsconfig.json", Kind.ROOT_FILE),
                Entry("jsconfig.json", "jsconfig.json", Kind.ROOT_FILE),
                Entry("vite.config.js", "vite.config.js", Kind.ROOT_FILE),
                Entry("vite.config.ts", "vite.config.ts", Kind.ROOT_FILE),
                Entry("vite.config.mjs", "vite.config.mjs", Kind.ROOT_FILE),
                Entry("svelte.config.js", "svelte.config.js", Kind.ROOT_FILE),
                Entry("env.d.ts", "env.d.ts", Kind.ROOT_FILE),
                Entry("vite-env.d.ts", "vite-env.d.ts", Kind.ROOT_FILE),
            )
        return (directories + files)
            .filter { exists(it.relativePath) }
            .filterNot { isNestedResourcesPublic(it.relativePath) }
    }

    /**
     * True when [relativePath] is the project-root document directory, not a
     * `public` folder nested under `resources`.
     */
    fun isProjectWebPublic(relativePath: String): Boolean =
        normalize(relativePath) == KonaLayout.PUBLIC_DIR

    /**
     * A `public` directory under `resources` or `src/main/resources` is not the
     * HTTP document root. The Kona Project View must not re-parent it as `public`.
     */
    fun isNestedResourcesPublic(relativePath: String): Boolean {
        val norm = normalize(relativePath)
        return norm == "resources/public" ||
            norm == "src/main/resources/public" ||
            norm.endsWith("/resources/public")
    }

    /**
     * Hide a nested `public` child of `resources` when the project-root
     * document directory is the one shown in the tree.
     */
    fun hideNestedPublicDirectory(
        childName: String,
        isDirectory: Boolean,
        projectWebPublicExists: Boolean,
    ): Boolean =
        projectWebPublicExists && isDirectory && childName.equals(KonaLayout.PUBLIC_DIR, ignoreCase = true)

    private fun normalize(relativePath: String): String =
        relativePath.replace('\\', '/').trim().trim('/')

    fun entriesForRoot(rootExists: (String) -> Boolean, detection: KonaLayout.Detection): List<Entry> =
        entries(detection, rootExists)
}
