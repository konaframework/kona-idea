package org.konaframework.idea

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.LexerBase
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet

/**
 * Crema is a template language over HTML.
 *
 * 1. **PSI** — [CremaFileViewProvider] gives the file an HTML root so HTML
 *    completion / inspections work ([CREMA_TEMPLATE_DATA] strips Crema syntax).
 * 2. **Colors** — [CremaEditorHighlighterProvider] layers the HTML highlighter
 *    over [CremaTokens.TEMPLATE_DATA] spans.
 */
object CremaLanguage : Language("Crema"), TemplateLanguage {
    private fun readResolve(): Any = CremaLanguage
}

object CremaTokens {
    val TEMPLATE_DATA = IElementType("CREMA_TEMPLATE_DATA", CremaLanguage)
    val OUTER = OuterLanguageElementType("CREMA_OUTER", CremaLanguage)
    val COMMENT = IElementType("CREMA_COMMENT", CremaLanguage)
    val ECHO = IElementType("CREMA_ECHO", CremaLanguage)
    val RAW_ECHO = IElementType("CREMA_RAW_ECHO", CremaLanguage)
    val DIRECTIVE = IElementType("CREMA_DIRECTIVE", CremaLanguage)

    val CREMA_SYNTAX = TokenSet.create(COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
    val ALL = TokenSet.create(TEMPLATE_DATA, OUTER, COMMENT, ECHO, RAW_ECHO, DIRECTIVE)
}

val CREMA_TEMPLATE_DATA =
    TemplateDataElementType(
        "CREMA_HTML",
        HTMLLanguage.INSTANCE,
        CremaTokens.TEMPLATE_DATA,
        CremaTokens.OUTER,
    )

/**
 * Splits a template into Crema constructs and HTML host chunks.
 * Every [advance] consumes at least one character.
 */
class CremaLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        if (startOffset < endOffset) advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }

        val text = buffer
        val i = tokenStart

        if (match(text, i, "{{--")) {
            val close = indexOf(text, i + 4, "--}}")
            tokenEnd = if (close >= 0) close + 4 else endOffset
            tokenType = CremaTokens.COMMENT
            return
        }
        if (match(text, i, "{!!")) {
            val close = indexOf(text, i + 3, "!!}")
            tokenEnd = if (close >= 0) close + 3 else endOffset
            tokenType = CremaTokens.RAW_ECHO
            return
        }
        if (match(text, i, "{{")) {
            val close = indexOf(text, i + 2, "}}")
            tokenEnd = if (close >= 0) close + 2 else endOffset
            tokenType = CremaTokens.ECHO
            return
        }
        val directiveEnd = directiveEndAt(text, i)
        if (directiveEnd > 0) {
            tokenEnd = directiveEnd
            tokenType = CremaTokens.DIRECTIVE
            return
        }

        var j = i + 1
        if (text[i] == '@' && j < endOffset && text[j] == '@') j++
        while (j < endOffset && !startsCrema(text, j)) j++
        tokenEnd = j
        tokenType = CremaTokens.TEMPLATE_DATA
    }

    private fun startsCrema(text: CharSequence, offset: Int): Boolean =
        match(text, offset, "{{") || match(text, offset, "{!!") || directiveEndAt(text, offset) > 0

    private fun directiveEndAt(text: CharSequence, offset: Int): Int {
        val name = CremaDirectives.directiveNameAt(text, offset) ?: return -1
        if (!CremaDirectives.isKnown(name)) return -1
        var j = offset + 1 + name.length
        if (j < endOffset && text[j] == '(') {
            val close = matchingParen(text, j)
            j = if (close >= 0) close + 1 else endOffset
        }
        return j
    }

    private fun matchingParen(text: CharSequence, open: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = open
        while (i < endOffset) {
            val c = text[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun match(text: CharSequence, offset: Int, literal: String): Boolean {
        if (offset + literal.length > endOffset) return false
        for (k in literal.indices) {
            if (text[offset + k] != literal[k]) return false
        }
        return true
    }

    private fun indexOf(text: CharSequence, from: Int, literal: String): Int {
        val last = endOffset - literal.length
        var i = from
        while (i <= last) {
            if (match(text, i, literal)) return i
            i++
        }
        return -1
    }
}
