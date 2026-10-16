package dev.kona.idea

/**
 * Detects Cadence / QueryBuilder call sites where a DB column string is expected.
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

    private val CALL =
        Regex("""\b(?:${METHOD_PREFIXES.joinToString("|")})\s*\(\s*$""")

    private val MAP_KEY =
        Regex("""\b(?:create|update|insert|fill|forceFill)\s*\(\s*(?:mapOf|Map\.of)\s*\([^)]*$""")

    fun columnPrefixAt(text: CharSequence, offset: Int): String? {
        var i = offset - 1
        while (i >= 0 && isColPart(text[i])) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        if (!isColumnCallContext(text, i)) return null
        return text.subSequence(i + 1, offset).toString()
    }

    fun isColumnCallContext(text: CharSequence, quoteStart: Int): Boolean {
        if (quoteStart < 0 || quoteStart >= text.length) return false
        val q = text[quoteStart]
        if (q != '\'' && q != '"') return false
        val before = text.subSequence(0, quoteStart).toString()
        val window = before.takeLast(120)
        if (CALL.containsMatchIn(window)) return true
        if (MAP_KEY.containsMatchIn(window)) return true
        // select("a", "b") — subsequent string args after a comma
        if (before.trimEnd().endsWith(',') && looksLikeSelectOrOrderList(before)) return true
        return false
    }

    private fun looksLikeSelectOrOrderList(before: CharSequence): Boolean {
        val window = before.takeLast(120)
        return Regex("""\b(?:select|addSelect|groupBy|orderBy)\s*\(""").containsMatchIn(window)
    }

    fun isColPart(c: Char): Boolean = c == '_' || c == '.' || c.isLetterOrDigit()
}
