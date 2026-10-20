package org.konaframework.idea

/**
 * Detects Cadence attribute-key call sites:
 * `user.getAttribute("…")`, `setAttribute`, `attribute`, `hasAttribute`,
 * `getRawAttribute`, `setRawAttribute`, `getOriginal`, `isDirty` / `isClean` /
 * `wasChanged`, Java `user.get("…")` / `user.set("…", …)`, Kotlin `user["…"]`,
 * and the same methods with the caret still inside empty parentheses.
 */
object AttributeKeyPatterns {
    private val KEY_METHODS =
        listOf(
            "getAttribute",
            "setAttribute",
            "attribute",
            "hasAttribute",
            "getRawAttribute",
            "setRawAttribute",
            "getOriginal",
            "isDirty",
            "isClean",
            "wasChanged",
        )

    private val GET_SET = listOf("get", "set")

    private val METHOD_ALT = (KEY_METHODS + GET_SET).joinToString("|")

    private val CALL =
        Regex("""\b(?:$METHOD_ALT)\s*\(\s*(?:(?:key|name)\s*=\s*)?$""")

    private val RECEIVER_CALL =
        Regex("""\b(\w+)\s*\.\s*(?:$METHOD_ALT)\s*\(\s*(?:(?:key|name)\s*=\s*)?$""")

    private val UNQUOTED_CALL =
        Regex(
            """\b(\w+)\s*\.\s*($METHOD_ALT)\s*\(\s*(?:(?:key|name)\s*=\s*)?([\w.-]*)$""",
        )

    private val INDEX =
        Regex("""\b(\w+)\s*(?:\?\.)?\[\s*(["'])([^"']*)$""")

    data class KeyContext(
        val prefix: String,
        /** Instance / class receiver before `.getAttribute(`, when known. */
        val receiver: String?,
        /** When false, lookup should insert a quoted string. */
        val quoted: Boolean = true,
    )

    fun keyContextAt(text: CharSequence, offset: Int): KeyContext? {
        quotedContext(text, offset)?.let { return it }
        indexContext(text, offset)?.let { return it }
        return unquotedCallContext(text, offset)
    }

    fun quotedContext(text: CharSequence, offset: Int): KeyContext? {
        var i = offset - 1
        while (i >= 0 && DbColumnPatterns.isColPart(text[i])) i--
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        if (!isAttributeKeyContext(text, i)) return null
        val prefix = CompletionText.stripDummy(text.subSequence(i + 1, offset).toString())
        val receiver = receiverBeforeQuote(text, i)
        return KeyContext(prefix, receiver, quoted = true)
    }

    fun indexContext(text: CharSequence, offset: Int): KeyContext? {
        val end = offset.coerceIn(0, text.length)
        val window = text.subSequence(0, end).toString().takeLast(120)
        val m = INDEX.find(window) ?: return null
        return KeyContext(
            prefix = CompletionText.stripDummy(m.groupValues[3]),
            receiver = m.groupValues[1],
            quoted = true,
        )
    }

    fun unquotedCallContext(text: CharSequence, offset: Int): KeyContext? {
        val end = offset.coerceIn(0, text.length)
        val window = text.subSequence(0, end).toString().takeLast(160)
        val m = UNQUOTED_CALL.find(window) ?: return null
        val receiver = m.groupValues[1]
        val method = m.groupValues[2]
        val prefix = CompletionText.stripDummy(m.groupValues[3])
        if (method in GET_SET && receiver.isNotEmpty() && receiver[0].isUpperCase()) {
            return null
        }
        return KeyContext(prefix, receiver, quoted = false)
    }

    fun isAttributeKeyContext(text: CharSequence, quoteStart: Int): Boolean {
        if (quoteStart < 0 || quoteStart >= text.length) return false
        val q = text[quoteStart]
        if (q != '\'' && q != '"') return false
        val window = text.subSequence(0, quoteStart).toString().takeLast(120)
        if (CALL.containsMatchIn(window)) return true
        val before = window.trimEnd()
        return before.endsWith('[') || before.endsWith("?. [") || before.endsWith("?.[")
    }

    fun receiverBeforeQuote(text: CharSequence, quoteStart: Int): String? {
        val window = text.subSequence(0, quoteStart).toString().takeLast(120)
        RECEIVER_CALL.find(window)?.groupValues?.get(1)?.let { return it }
        return Regex("""\b(\w+)\s*(?:\?\.)?\[\s*$""").find(window)?.groupValues?.get(1)
    }
}
