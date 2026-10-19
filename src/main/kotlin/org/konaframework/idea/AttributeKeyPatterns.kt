package org.konaframework.idea

/**
 * Detects Cadence attribute-key string call sites:
 * `user.getAttribute("…")`, `setAttribute`, `attribute`, `hasAttribute`,
 * `getRawAttribute`, `setRawAttribute`.
 */
object AttributeKeyPatterns {
    private val METHODS =
        listOf(
            "getAttribute",
            "setAttribute",
            "attribute",
            "hasAttribute",
            "getRawAttribute",
            "setRawAttribute",
        )

    private val METHOD_ALT = METHODS.joinToString("|")

    private val CALL = Regex("""\b(?:$METHOD_ALT)\s*\(\s*$""")

    private val RECEIVER_CALL =
        Regex("""\b(\w+)\s*\.\s*(?:$METHOD_ALT)\s*\(\s*$""")

    data class KeyContext(
        val prefix: String,
        /** Instance / class receiver before `.getAttribute(`, when known. */
        val receiver: String?,
    )

    fun keyContextAt(text: CharSequence, offset: Int): KeyContext? {
        var i = offset - 1
        while (i >= 0 && DbColumnPatterns.isColPart(text[i])) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        if (!isAttributeKeyContext(text, i)) return null
        val prefix = CompletionText.stripDummy(text.subSequence(i + 1, offset).toString())
        val receiver = receiverBeforeQuote(text, i)
        return KeyContext(prefix, receiver)
    }

    fun isAttributeKeyContext(text: CharSequence, quoteStart: Int): Boolean {
        if (quoteStart < 0 || quoteStart >= text.length) return false
        val q = text[quoteStart]
        if (q != '\'' && q != '"') return false
        val window = text.subSequence(0, quoteStart).toString().takeLast(120)
        return CALL.containsMatchIn(window)
    }

    fun receiverBeforeQuote(text: CharSequence, quoteStart: Int): String? {
        val window = text.subSequence(0, quoteStart).toString().takeLast(120)
        return RECEIVER_CALL.find(window)?.groupValues?.get(1)
    }
}
