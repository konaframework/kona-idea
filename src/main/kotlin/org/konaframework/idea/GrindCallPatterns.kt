package org.konaframework.idea

/**
 * Detects programmatic grind invocations: `Grind.call("…")`, `Grind.callSilently("…")`,
 * including the caret inside empty parentheses (inserts quotes).
 */
object GrindCallPatterns {
    private val RECEIVER = """(?:(?:org\s*\.\s*konaframework\s*\.\s*grind\s*\.\s*)?Grind)"""

    private val QUOTED =
        Regex("""\b$RECEIVER\s*\.\s*call(?:Silently)?\s*\(\s*$""")

    private val UNQUOTED =
        Regex("""\b$RECEIVER\s*\.\s*call(?:Silently)?\s*\(\s*([\w:.-]*)$""")

    data class CallContext(
        val prefix: String,
        val quoted: Boolean,
    )

    fun callContextAt(text: CharSequence, offset: Int): CallContext? {
        quotedContext(text, offset)?.let { return it }
        return unquotedContext(text, offset)
    }

    fun quotedContext(text: CharSequence, offset: Int): CallContext? {
        var i = offset - 1
        while (i >= 0 && isCmdPart(text[i])) i--
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        val window = text.subSequence(0, i).toString().takeLast(160)
        if (!QUOTED.containsMatchIn(window)) return null
        val prefix = CompletionText.stripDummy(text.subSequence(i + 1, offset).toString())
        return CallContext(prefix, quoted = true)
    }

    fun unquotedContext(text: CharSequence, offset: Int): CallContext? {
        val end = offset.coerceIn(0, text.length)
        val window = text.subSequence(0, end).toString().takeLast(160)
        val m = UNQUOTED.find(window) ?: return null
        return CallContext(CompletionText.stripDummy(m.groupValues[1]), quoted = false)
    }

    fun isCmdPart(c: Char): Boolean =
        c == '_' || c == '-' || c == ':' || c == '.' || c.isLetterOrDigit()
}
