package org.konaframework.idea

/**
 * Detects `<brew:…` typing contexts in Crema HTML.
 *
 * Brew tags map to [BrewComponentIndex] names (`Counter` → `counter`,
 * `@BrewComponent("posts")` → `posts`), rendered as `<brew:counter />`.
 */
object BrewTagPatterns {
    const val PREFIX: String = "brew:"

    data class TagPrefix(
        /** Component-name prefix after `brew:` (may be empty). */
        val prefix: String,
        /**
         * True when `<brew:` is already in the document — insert only the
         * component name. False when the user is still typing `brew` / `br`.
         */
        val afterColon: Boolean,
    )

    /** Prefix after `<brew:` (or while typing `brew` / `brew:`) at [offset]. */
    fun tagPrefixAt(text: CharSequence, offset: Int): TagPrefix? {
        var i = offset - 1
        while (i >= 0 && isTagPart(text[i])) i--
        val before = text.subSequence(0, i + 1).toString()
        when {
            before.endsWith("<$PREFIX") ->
                return TagPrefix(text.subSequence(i + 1, offset).toString(), afterColon = true)
            before.endsWith("<brew") && offset > i + 1 && text[i + 1] == ':' ->
                return TagPrefix(text.subSequence(i + 2, offset).toString(), afterColon = true)
        }
        if (before.endsWith("<") && offset > i + 1) {
            val typed = text.subSequence(i + 1, offset).toString()
            if (typed.startsWith(PREFIX, ignoreCase = true)) {
                return TagPrefix(typed.substring(PREFIX.length), afterColon = true)
            }
            if (typed.equals("brew", ignoreCase = true) ||
                typed.equals("bre", ignoreCase = true)
            ) {
                // Require ≥3 chars so `<b` / `<br` stay normal HTML completion.
                return TagPrefix("", afterColon = false)
            }
        }
        return null
    }

    fun isBrewTagName(name: String): Boolean =
        name.startsWith(PREFIX, ignoreCase = true)

    /** Component name inside `<brew:todos>` / `brew:todos`, or null if not a brew tag. */
    fun componentNameFromTag(name: String): String? {
        if (!isBrewTagName(name)) return null
        return name.substring(PREFIX.length)
    }

    fun htmlTag(componentName: String): String = PREFIX + componentName

    fun isTagPart(c: Char): Boolean =
        c == '_' || c == '-' || c == '.' || c == '/' || c.isLetterOrDigit()
}
