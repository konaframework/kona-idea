package org.konaframework.idea

import com.intellij.psi.tree.IElementType

/**
 * Crema views reformat with the HTML formatter (template data language) and
 * HTML indent options. Echoes, directives, and comments stay frozen so
 * `@if` / `{{ url() }}` are not rewritten as tags.
 */
object CremaFormatting {
    fun appliesToFileName(name: String): Boolean = CremaFileType.isCremaFileName(name)

    fun isFrozenToken(type: IElementType?): Boolean =
        type != null && type != CremaTokens.TEMPLATE_DATA

    /**
     * Character ranges (end exclusive) that the HTML formatter must leave
     * untouched: `{{ }}`, `{!! !!}`, `{{-- --}}`, and known `@directives`.
     */
    fun frozenRanges(text: CharSequence): List<IntRange> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val lexer = CremaLexer()
        lexer.start(text, 0, text.length, 0)
        val out = ArrayList<IntRange>()
        while (true) {
            val type = lexer.tokenType ?: break
            if (isFrozenToken(type)) {
                out.add(lexer.tokenStart until lexer.tokenEnd)
            }
            lexer.advance()
        }
        return out
    }
}
