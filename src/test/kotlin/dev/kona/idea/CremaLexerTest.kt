package dev.kona.idea

import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Test

class CremaLexerTest {
    @Test
    fun `splits html host text from crema constructs`() {
        assertEquals(
            listOf(
                CremaTokens.TEMPLATE_DATA to "<div class=\"card\">",
                CremaTokens.ECHO to "{{ name }}",
                CremaTokens.TEMPLATE_DATA to "</div>",
            ),
            tokens("<div class=\"card\">{{ name }}</div>"),
        )
    }

    @Test
    fun `directive swallows its argument list`() {
        assertEquals(
            listOf(
                CremaTokens.DIRECTIVE to "@if(count > 1 and label == \")\")",
                CremaTokens.TEMPLATE_DATA to "x",
                CremaTokens.DIRECTIVE to "@endif",
            ),
            tokens("@if(count > 1 and label == \")\")x@endif"),
        )
    }

    @Test
    fun `comments and raw echoes are distinct`() {
        assertEquals(
            listOf(
                CremaTokens.COMMENT to "{{-- hidden --}}",
                CremaTokens.RAW_ECHO to "{!! body !!}",
            ),
            tokens("{{-- hidden --}}{!! body !!}"),
        )
    }

    @Test
    fun `email addresses and escaped at signs stay html`() {
        assertEquals(
            listOf(CremaTokens.TEMPLATE_DATA to "hi@example.com @@notADirective"),
            tokens("hi@example.com @@notADirective"),
        )
    }

    @Test
    fun `unterminated constructs consume the rest without stalling`() {
        assertEquals(listOf(CremaTokens.ECHO to "{{ oops"), tokens("{{ oops"))
        assertEquals(listOf(CremaTokens.COMMENT to "{{-- oops"), tokens("{{-- oops"))
    }

    @Test
    fun `unknown at sequences stay host text`() {
        assertEquals(
            listOf(CremaTokens.TEMPLATE_DATA to "@notARealDirective"),
            tokens("@notARealDirective"),
        )
    }

    @Test
    fun `unterminated raw echo and paren`() {
        assertEquals(listOf(CremaTokens.RAW_ECHO to "{!! oops"), tokens("{!! oops"))
        assertEquals(
            listOf(CremaTokens.DIRECTIVE to "@if(unterminated"),
            tokens("@if(unterminated"),
        )
    }

    @Test
    fun `asset and vite directives tokenize`() {
        assertEquals(
            listOf(
                CremaTokens.DIRECTIVE to "@asset('css/app.css')",
                CremaTokens.TEMPLATE_DATA to " ",
                CremaTokens.DIRECTIVE to "@vite('resources/js/app.js')",
            ),
            tokens("@asset('css/app.css') @vite('resources/js/app.js')"),
        )
    }

    private fun tokens(text: String): List<Pair<IElementType, String>> {
        val lexer = CremaLexer()
        lexer.start(text, 0, text.length, 0)
        assertEquals(text.length, lexer.bufferEnd)
        val out = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            check(lexer.tokenEnd > lexer.tokenStart) { "lexer did not advance at ${lexer.tokenStart}" }
            lexer.advance()
        }
        return out
    }
}
