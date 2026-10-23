package org.konaframework.idea

/**
 * Published Maven coordinates for Kona modules (`group` = [KonaLayout.MAVEN_GROUP]).
 * Kept as documentation for facets / tooling UI — apps depend on the subset they need.
 */
object KonaArtifacts {
    val MODULES: List<String> =
        listOf(
            "kona-core",
            "kona-http",
            "kona-grind",
            "kona-cadence",
            "kona-crema",
            "kona-session",
            "kona-auth",
            "kona-redis",
            "kona-cache",
            "kona-filesystem",
            "kona-queue",
            "kona-schedule",
            "kona-mail",
            "kona-notifications",
            "kona-events",
            "kona-support",
            "kona-testing",
            "kona-drip",
            "kona-wire",
            "kona-brew",
            "kona-inertia",
            "kona-broadcasting",
            "kona-search",
            "kona-cli",
            "kona-lsp",
        )

    fun coordinate(module: String): String = "${KonaLayout.MAVEN_GROUP}:$module"

    fun coordinates(): List<String> = MODULES.map { coordinate(it) }

    fun helpText(): String =
        buildString {
            appendLine("Kona Maven group: ${KonaLayout.MAVEN_GROUP}")
            appendLine()
            appendLine("Typical web app (from kona new scaffolds):")
            appendLine("  ${coordinate("kona-http")}")
            appendLine("  ${coordinate("kona-grind")}")
            appendLine("  ${coordinate("kona-cadence")}")
            appendLine("  ${coordinate("kona-session")}")
            appendLine("  ${coordinate("kona-auth")}")
            appendLine("  ${coordinate("kona-crema")}")
            appendLine("  ${coordinate("kona-queue")}")
            appendLine("  ${coordinate("kona-schedule")}")
            appendLine("  …")
            appendLine()
            appendLine("Frontend extras:")
            appendLine("  ${coordinate("kona-wire")}     — Wire (wire:* in Crema)")
            appendLine("  ${coordinate("kona-brew")}     — deprecated alias of kona-wire")
            appendLine("  ${coordinate("kona-inertia")}  — Inertia page responses")
            appendLine()
            appendLine("HTTP: KonaHttpServer is a Netty adapter; requests run on virtual threads.")
            appendLine("Serve: ./grind serve  →  http://127.0.0.1:4000 (HOST/PORT overridable)")
            appendLine()
            appendLine("All modules:")
            MODULES.forEach { appendLine("  ${coordinate(it)}") }
        }
}
