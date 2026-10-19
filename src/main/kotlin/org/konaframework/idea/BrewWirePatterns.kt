package org.konaframework.idea

/**
 * Detects Brew `wire:…` attribute typing / attribute spans in Crema HTML.
 */
object BrewWirePatterns {
    data class AttrHit(
        val name: String,
        val start: Int,
        val end: Int,
    )

    /**
     * Prefix of a `wire:` attribute being typed at [offset]
     * (e.g. `wire:mo` inside `<input wire:mo|`).
     */
    fun attributePrefixAt(text: CharSequence, offset: Int): String? {
        if (offset < 0 || offset > text.length) return null
        var i = offset - 1
        while (i >= 0 && isAttrNamePart(text[i])) i--
        val start = i + 1
        if (start >= offset) return null
        val prefix = text.subSequence(start, offset).toString()
        if (!looksLikeAttrStart(text, start)) return null
        if (!insideOpenTag(text, start)) return null
        val lower = prefix.lowercase()
        if (lower.startsWith("wire")) return prefix
        // Typing `w` / `wi` / `wir` inside a tag
        if (lower.isNotEmpty() && "wire".startsWith(lower)) return prefix
        return null
    }

    fun findAll(text: CharSequence): List<AttrHit> {
        val out = mutableListOf<AttrHit>()
        var i = 0
        while (i < text.length) {
            if (regionMatches(text, i, "wire:")) {
                val start = i
                var j = i + "wire:".length
                while (j < text.length && isAttrNamePart(text[j])) j++
                if (j > i + "wire:".length) {
                    out += AttrHit(text.subSequence(start, j).toString(), start, j)
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }

    fun findUnknown(text: CharSequence): List<AttrHit> =
        findAll(text).filter { !BrewWireAttributes.isKnownAttribute(it.name) }

    fun isAttrNamePart(c: Char): Boolean =
        c == ':' || c == '.' || c == '-' || c == '_' || c.isLetterOrDigit()

    private fun looksLikeAttrStart(text: CharSequence, start: Int): Boolean {
        if (start == 0) return false
        val prev = text[start - 1]
        return prev.isWhitespace() || prev == '<' || prev == '"' || prev == '\''
    }

    private fun insideOpenTag(text: CharSequence, offset: Int): Boolean {
        var i = offset - 1
        var sawOpen = false
        while (i >= 0) {
            val c = text[i]
            when {
                c == '>' -> return false
                c == '<' -> {
                    sawOpen = true
                    break
                }
            }
            i--
        }
        if (!sawOpen) return false
        var j = offset
        while (j < text.length) {
            val c = text[j]
            if (c == '>') return true
            if (c == '<') return false
            j++
        }
        return true
    }

    private fun regionMatches(text: CharSequence, offset: Int, needle: String): Boolean {
        if (offset + needle.length > text.length) return false
        for (k in needle.indices) {
            if (text[offset + k].lowercaseChar() != needle[k].lowercaseChar()) return false
        }
        return true
    }
}
