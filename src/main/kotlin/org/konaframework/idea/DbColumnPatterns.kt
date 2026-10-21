package org.konaframework.idea

/**
 * Detects Cadence / QueryBuilder call sites where a DB column string is expected,
 * and resolves the receiver model/table when present (`User.where("…")`).
 */
object DbColumnPatterns {
    private val METHOD_PREFIXES =
        listOf(
            "where",
            "orWhere",
            "whereNull",
            "whereNotNull",
            "whereIn",
            "whereNotIn",
            "whereBetween",
            "orWhereBetween",
            "orderBy",
            "groupBy",
            "having",
            "orHaving",
            "select",
            "addSelect",
            "pluck",
            "value",
            "latest",
            "oldest",
            "increment",
            "decrement",
            "firstWhere",
        )

    private val METHOD_ALT = METHOD_PREFIXES.joinToString("|")

    /** `.` / Kotlin `?.` / `!!.` — e.g. `user?.where("…")`. */
    private const val DOT = """(?:\?\.|!!\.|\.)"""

    private val CALL =
        Regex("""\b(?:$METHOD_ALT)\s*\(\s*$""")

    private val RECEIVER_CALL =
        Regex("""\b(\w+)\s*$DOT\s*(?:$METHOD_ALT)\s*\(\s*$""")

    private val MAP_KEY =
        Regex("""\b(?:create|update|insert|fill|forceFill)\s*\(\s*(?:mapOf|Map\.of)\s*\([^)]*$""")

    private val RECEIVER_MAP =
        Regex("""\b(\w+)\s*$DOT\s*(?:create|update|insert|fill|forceFill)\s*\(\s*(?:mapOf|Map\.of)\s*\([^)]*$""")

    data class ColumnContext(
        val prefix: String,
        /** PascalCase model / table hint from the call receiver, when known. */
        val receiver: String?,
    )

    fun columnPrefixAt(text: CharSequence, offset: Int): String? =
        columnContextAt(text, offset)?.prefix

    fun columnContextAt(text: CharSequence, offset: Int): ColumnContext? {
        var i = offset - 1
        while (i >= 0 && isColPart(text[i])) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        if (!isColumnCallContext(text, i)) return null
        val prefix = CompletionText.stripDummy(text.subSequence(i + 1, offset).toString())
        val receiver = receiverBeforeQuote(text, i)
        return ColumnContext(prefix, receiver)
    }

    fun isColumnCallContext(text: CharSequence, quoteStart: Int): Boolean {
        if (quoteStart < 0 || quoteStart >= text.length) return false
        val q = text[quoteStart]
        if (q != '\'' && q != '"') return false
        val before = text.subSequence(0, quoteStart).toString()
        val window = before.takeLast(160)
        if (CALL.containsMatchIn(window)) return true
        if (MAP_KEY.containsMatchIn(window)) return true
        // select("a", "b") — subsequent string args after a comma
        if (before.trimEnd().endsWith(',') && looksLikeSelectOrOrderList(before)) return true
        return false
    }

    /** Model/class or variable immediately before `.where("` / `.create(mapOf(`. */
    fun receiverBeforeQuote(text: CharSequence, quoteStart: Int): String? {
        val before = text.subSequence(0, quoteStart).toString()
        val window = before.takeLast(160)
        // Java: User.QUERY.where("  Kotlin: User.Companion.where("
        val queryField =
            Regex("""\b(\w+)\s*\.\s*(?:QUERY|Companion)\s*\.\s*(?:$METHOD_ALT)\s*\(\s*$""")
        queryField.find(window)?.groupValues?.get(1)?.let { return it }
        RECEIVER_CALL.find(window)?.groupValues?.get(1)?.let { return it }
        RECEIVER_MAP.find(window)?.groupValues?.get(1)?.let { return it }
        // Chained: User.query().where(" — still User
        val chained =
            Regex("""\b(\w+)\s*$DOT\s*(?:query|newQuery|withTrashed|onlyTrashed)\s*\([^)]*\)\s*$DOT\s*(?:$METHOD_ALT)\s*\(\s*$""")
        chained.find(window)?.groupValues?.get(1)?.let { return it }
        return null
    }

    private fun looksLikeSelectOrOrderList(before: CharSequence): Boolean {
        val window = before.takeLast(160)
        return Regex("""\b(?:select|addSelect|groupBy|orderBy)\s*\(""").containsMatchIn(window)
    }

    fun isColPart(c: Char): Boolean = c == '_' || c == '.' || c.isLetterOrDigit()
}
