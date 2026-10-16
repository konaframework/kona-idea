package dev.kona.idea

/**
 * Mirrors [dev.kona.cadence.Cadence.tableNameFor] so the IDE can resolve a model's
 * conventional table without loading Cadence at runtime.
 *
 * `BlogPost` → `blog_posts`, `Category` → `categories`, `Box` → `boxes`.
 */
object CadenceTableNames {
    private val CAMEL = Regex("([a-z0-9])([A-Z])")
    private val Y_PLURAL = Regex("""[^aeiou]y$""")
    private val SIBILANT = Regex("""(?:s|ss|sh|ch|x|z)$""")

    fun tableNameFor(className: String): String {
        val simple = className.substringAfterLast('.').substringAfterLast('$')
        val snake = CAMEL.replace(simple, "$1_$2").lowercase()
        return when {
            Y_PLURAL.containsMatchIn(snake) -> snake.dropLast(1) + "ies"
            SIBILANT.containsMatchIn(snake) -> snake + "es"
            else -> snake + "s"
        }
    }

    /** `Project` → `project`, `BlogPost` → `blogPost`. */
    fun instanceNameFor(className: String): String {
        val simple = className.substringAfterLast('.').substringAfterLast('$')
        if (simple.isEmpty()) return simple
        return simple.replaceFirstChar { it.lowercaseChar() }
    }
}
