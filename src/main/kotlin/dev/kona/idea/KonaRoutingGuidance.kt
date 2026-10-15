package dev.kona.idea

/**
 * Current routing guidance for IDE help / templates.
 *
 * Prefer typed controller instances or lambdas. Class / `"Controller@method"`
 * string registration is being removed from the framework; do not teach it.
 */
object KonaRoutingGuidance {
    val PREFERRED_KOTLIN: String =
        """
        val welcome = WelcomeController()
        router.get("/") { welcome.index(it) }.name("welcome")
        """.trimIndent()

    val PREFERRED_JAVA: String =
        """
        WelcomeController welcome = new WelcomeController();
        router.get("/", welcome::index).name("welcome");
        """.trimIndent()

    fun helpText(): String =
        """
        Kona routing (typed handlers)

        Prefer a controller instance + method reference / lambda:

        Kotlin:
        $PREFERRED_KOTLIN

        Java:
        $PREFERRED_JAVA

        Avoid Class + method name or "Controller@method" string registration —
        scaffolds and samples use typed handlers only.

        HTTP server: KonaHttpServer (Netty + virtual threads).
        Start: ./grind serve   →  http://127.0.0.1:4000
        """.trimIndent()
}
