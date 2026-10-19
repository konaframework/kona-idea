package org.konaframework.idea

/**
 * Detects `<x-…` / `<x-slot` typing contexts in Crema HTML.
 */
object XComponentPatterns {
    data class TagPrefix(
        val prefix: String,
        val isSlot: Boolean,
    )

    /** Prefix after `<x-` or `<x-slot` / `<x-slot:` at [offset]. */
    fun tagPrefixAt(text: CharSequence, offset: Int): TagPrefix? {
        var i = offset - 1
        while (i >= 0 && isTagPart(text[i])) i--
        // optional colon for <x-slot:name
        if (i >= 0 && text[i] == ':') {
            // <x-slot:foo
            val beforeColon = text.subSequence(0, i).toString()
            if (beforeColon.endsWith("<x-slot")) {
                return TagPrefix(text.subSequence(i + 1, offset).toString(), isSlot = true)
            }
            return null
        }
        val before = text.subSequence(0, i + 1).toString()
        when {
            before.endsWith("<x-slot") ->
                return TagPrefix("", isSlot = true)
            before.endsWith("<x-") ->
                return TagPrefix(text.subSequence(i + 1, offset).toString(), isSlot = false)
            before.endsWith("<x") && offset > i + 1 && text[i + 1] == '-' ->
                return TagPrefix(text.subSequence(i + 2, offset).toString(), isSlot = false)
        }
        // Typing just `<x` — offer `x-` components after completing to `<x-`
        if (before.endsWith("<") && offset > i + 1) {
            val typed = text.subSequence(i + 1, offset).toString()
            if (typed == "x" || typed.startsWith("x-")) {
                val prefix = typed.removePrefix("x").removePrefix("-")
                return TagPrefix(prefix, isSlot = false)
            }
        }
        return null
    }

    /** Inside an open `<x-foo …>` tag, suggest `x-slot` child starters when typing `<`. */
    fun wantsSlotChild(text: CharSequence, offset: Int): Boolean {
        if (offset < 1 || text[offset - 1] != '<') return false
        val before = text.subSequence(0, offset - 1)
        return openXComponentDepth(before) > 0
    }

    fun openXComponentDepth(before: CharSequence): Int {
        // Rough count of unclosed <x-name> (not x-slot, not self-closing).
        val open = Regex("""<x-(?!slot\b)([a-zA-Z0-9_.\-/]+)(?![^>]*/>)[^>]*>""")
        val close = Regex("""</x-([a-zA-Z0-9_.\-/]+)>""")
        var depth = 0
        open.findAll(before).forEach { depth++ }
        close.findAll(before).forEach { depth-- }
        return depth.coerceAtLeast(0)
    }

    fun isTagPart(c: Char): Boolean =
        c == '_' || c == '-' || c == '.' || c == '/' || c.isLetterOrDigit()
}
