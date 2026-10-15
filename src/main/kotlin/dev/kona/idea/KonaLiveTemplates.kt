package dev.kona.idea

/**
 * Logical live-template catalog (XML under `liveTemplates/Kona.xml`).
 * Unit-tested so snippets stay on typed handlers + current public APIs.
 */
object KonaLiveTemplates {
    data class Snippet(
        val abbreviation: String,
        val description: String,
        val body: String,
        val context: String,
    )

    val ALL: List<Snippet> =
        listOf(
            Snippet(
                "kroute",
                "Typed GET route (Kotlin)",
                "router.get(\"\$PATH\$\") { \$CTRL\$.\$METHOD\$(it) }.name(\"\$NAME\$\")",
                "KOTLIN",
            ),
            Snippet(
                "jroute",
                "Typed GET route (Java method reference)",
                "router.get(\"\$PATH\$\", \$CTRL\$::\$METHOD\$).name(\"\$NAME\$\");",
                "JAVA",
            ),
            Snippet(
                "kextract",
                "Extract query (Kotlin)",
                "\$MODEL\$.query().where(\"\$COL\$\", \$VALUE\$).first()",
                "KOTLIN",
            ),
            Snippet(
                "kauth",
                "Auth check (Kotlin)",
                "if (Auth.check()) {\n    \$END\$\n}",
                "KOTLIN",
            ),
            Snippet(
                "kdisk",
                "Filesystem disk put (Kotlin)",
                "files.disk(\"\$DISK\$\").put(\"\$PATH\$\", \$CONTENTS\$)",
                "KOTLIN",
            ),
            Snippet(
                "kqueue",
                "Dispatch queued job via Bus (Kotlin)",
                "bus.dispatch(\$JOB\$)",
                "KOTLIN",
            ),
            Snippet(
                "kschedule",
                "Schedule daily task (Kotlin)",
                "schedule.daily(\"\$NAME\$\") { \$END\$ }",
                "KOTLIN",
            ),
            Snippet(
                "kcrema",
                "Crema view response (Kotlin)",
                "Crema.view(crema, \"\$VIEW\$\", mapOf(\"\$KEY\$\" to \$VALUE\$))",
                "KOTLIN",
            ),
            Snippet(
                "kserve",
                "Comment: grind serve / Netty",
                "// ./grind serve  →  Netty + virtual threads on http://127.0.0.1:4000",
                "KOTLIN",
            ),
        )

    fun abbreviations(): List<String> = ALL.map { it.abbreviation }

    fun bodiesPreferTypedRoutes(): Boolean =
        ALL.filter { it.abbreviation.endsWith("route") }
            .all { !KonaTemplateBodies.mentionsClassAtMethod(it.body) && it.body.contains("router.get") }
}
