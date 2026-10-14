package dev.kona.idea

/**
 * Known Crema directive names (without `@`).
 *
 * Matching a known name — rather than "`@` followed by a word" — keeps
 * `hi@example.com` out of the highlighter. Keep in sync with
 * `modules/lsp` `CremaDirectives` in the konaframework/kona repository.
 */
object CremaDirectives {
    val NAMES: Set<String> =
        setOf(
            "extends",
            "section",
            "endsection",
            "show",
            "yield",
            "parent",
            "hasSection",
            "sectionMissing",
            "stack",
            "push",
            "endpush",
            "prepend",
            "endprepend",
            "include",
            "includeIf",
            "includeWhen",
            "includeUnless",
            "includeFirst",
            "if",
            "elseif",
            "else",
            "endif",
            "unless",
            "endunless",
            "isset",
            "endisset",
            "empty",
            "endempty",
            "while",
            "endwhile",
            "foreach",
            "endforeach",
            "forelse",
            "endforelse",
            "each",
            "endeach",
            "for",
            "endfor",
            "switch",
            "case",
            "break",
            "default",
            "endswitch",
            "auth",
            "endauth",
            "guest",
            "endguest",
            "can",
            "endcan",
            "cannot",
            "endcannot",
            "error",
            "enderror",
            "once",
            "endonce",
            "verbatim",
            "endverbatim",
            "component",
            "endcomponent",
            "slot",
            "endslot",
            "json",
            "csrf",
            "method",
            "asset",
            "lang",
            "vite",
        )

    val COMPLETION_LABELS: List<String> = NAMES.sorted().map { "@$it" }

    fun isKnown(name: String): Boolean = name in NAMES

    /**
     * If [text] at [offset] starts a `@name` token, returns the bare name;
     * otherwise null.
     */
    fun directiveNameAt(text: CharSequence, offset: Int): String? {
        if (offset < 0 || offset >= text.length || text[offset] != '@') return null
        if (offset > 0 && text[offset - 1] == '@') return null
        var j = offset + 1
        while (j < text.length && isIdentPart(text[j])) j++
        if (j == offset + 1) return null
        return text.subSequence(offset + 1, j).toString()
    }

    fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    /** Completions whose label starts with [prefix] (may include leading `@`). */
    fun completionsForPrefix(prefix: String): List<String> {
        val needle = prefix.removePrefix("@").lowercase()
        return COMPLETION_LABELS.filter { it.removePrefix("@").lowercase().startsWith(needle) }
    }
}
