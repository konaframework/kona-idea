package dev.kona.idea

/**
 * Detects Crema / Kotlin / Java call sites that reference a view by dotted name.
 */
object ViewNamePatterns {
    /** Patterns that end immediately before the opening quote of the view name. */
    private val CALL_PREFIXES =
        listOf(
            Regex("""(?i)\b(?:Crema\.)?view\s*\(\s*(?:[^,"']+,\s*)?$"""),
            Regex("""(?i)\b(?:Brew\.)?view\s*\(\s*(?:[^,"']+,\s*)?$"""),
            Regex("""(?i)\b(?:engine|crema)\.make\s*\(\s*$"""),
            Regex("""(?i)\b(?:engine|crema)\.render\s*\(\s*$"""),
            Regex("""@extends\s*\(\s*$"""),
            Regex("""@include(?:If)?\s*\(\s*$"""),
            Regex("""@include(?:When|Unless)\s*\(\s*[^,]+,\s*$"""),
            Regex("""@component\s*\(\s*$"""),
            Regex("""@each\s*\(\s*[^,]+,\s*$"""),
            Regex("""@includeFirst\s*\(\s*\[[^\]]*[\s,]$"""),
        )

    data class Hit(val name: String, val start: Int, val end: Int, val quote: Char)

    /** View name string containing [offset], if the surrounding call matches a view API. */
    fun viewNameAt(text: CharSequence, offset: Int): Hit? {
        if (offset < 0 || offset > text.length) return null
        val quoteHit = quotedStringAt(text, offset) ?: return null
        if (!isViewCallContext(text, quoteHit.start)) return null
        val name = text.subSequence(quoteHit.contentStart, quoteHit.contentEnd).toString()
        if (name.isBlank() || name.any { it.isWhitespace() }) return null
        return Hit(name, quoteHit.contentStart, quoteHit.contentEnd, quoteHit.quote)
    }

    fun isViewCallContext(text: CharSequence, quoteStart: Int): Boolean {
        if (quoteStart < 0 || quoteStart >= text.length) return false
        val q = text[quoteStart]
        if (q != '\'' && q != '"') return false
        val before = text.subSequence(0, quoteStart).toString()
        val window = before.takeLast(160)
        return CALL_PREFIXES.any { it.containsMatchIn(window) }
    }

    fun completionsPrefix(text: CharSequence, offset: Int): String? {
        var i = offset - 1
        while (i >= 0 && isNamePart(text[i])) i--
        if (i < 0) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null
        if (!isViewCallContext(text, i)) return null
        return text.subSequence(i + 1, offset).toString()
    }

    fun findAll(text: CharSequence): List<Hit> {
        val hits = mutableListOf<Hit>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\'' || c == '"') {
                val end = closeQuote(text, i) ?: break
                if (isViewCallContext(text, i)) {
                    val name = text.subSequence(i + 1, end).toString()
                    if (name.isNotBlank() && name.none { it.isWhitespace() }) {
                        hits += Hit(name, i + 1, end, c)
                    }
                }
                i = end + 1
            } else {
                i++
            }
        }
        return hits
    }

    private data class QuoteSpan(val start: Int, val contentStart: Int, val contentEnd: Int, val quote: Char)

    private fun quotedStringAt(text: CharSequence, offset: Int): QuoteSpan? {
        var i = offset - 1
        while (i >= 0) {
            val c = text[i]
            if (c == '\'' || c == '"') {
                val end = closeQuote(text, i) ?: return null
                if (offset in (i + 1)..end) {
                    return QuoteSpan(i, i + 1, end, c)
                }
                return null
            }
            if (c == '\n') return null
            if (!isNamePart(c) && c != '\\') return null
            i--
        }
        return null
    }

    private fun closeQuote(text: CharSequence, open: Int): Int? {
        val quote = text[open]
        var j = open + 1
        while (j < text.length) {
            val c = text[j]
            if (c == '\\') {
                j += 2
                continue
            }
            if (c == quote) return j
            if (c == '\n') return null
            j++
        }
        return null
    }

    fun isNamePart(c: Char): Boolean =
        c == '.' || c == '_' || c == '-' || c == '/' || c.isLetterOrDigit()
}
