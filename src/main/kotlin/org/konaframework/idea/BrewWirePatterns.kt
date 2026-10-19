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
     * Caret inside a `wire:…="…"` (or `'…'`) value.
     *
     * [prefix] is the member-name fragment before `|` (stops at `(` for
     * `remove(|)`-style action calls).
     */
    data class ValueContext(
        val attributeName: String,
        val prefix: String,
        val quote: Char,
    )

    /** Caret inside `{{ … }}` or `{!! … !!}` echo expressions. */
    data class EchoContext(
        val prefix: String,
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

    /**
     * When the caret is inside a Brew attribute value
     * (`wire:submit="ad|"`, `wire:model='dra|'`), returns the attribute and typed prefix.
     */
    fun valueContextAt(text: CharSequence, offset: Int): ValueContext? {
        if (offset < 0 || offset > text.length) return null
        var i = offset - 1
        var quote: Char? = null
        while (i >= 0) {
            val c = text[i]
            when {
                c == '"' || c == '\'' -> {
                    quote = c
                    break
                }
                c == '>' || c == '<' || c == '\n' -> return null
            }
            i--
        }
        if (quote == null) return null
        val valueStart = i + 1
        // Walk back over optional whitespace then `=` then attribute name.
        var j = i - 1
        while (j >= 0 && text[j].isWhitespace()) j--
        if (j < 0 || text[j] != '=') return null
        j--
        while (j >= 0 && text[j].isWhitespace()) j--
        val nameEnd = j + 1
        while (j >= 0 && isAttrNamePart(text[j])) j--
        val nameStart = j + 1
        if (nameStart >= nameEnd) return null
        val attrName = text.subSequence(nameStart, nameEnd).toString()
        if (!attrName.lowercase().startsWith("wire:")) return null
        if (!insideOpenTag(text, nameStart)) return null

        // Ensure we have not already closed the value before [offset].
        var k = valueStart
        while (k < offset) {
            if (text[k] == quote) return null
            k++
        }

        val raw = text.subSequence(valueStart, offset).toString()
        val prefix = memberPrefix(raw)
        return ValueContext(attrName, prefix, quote)
    }

    /**
     * When the caret is inside `{{ draft| }}` / `{!! count| !!}`, returns the echo identifier prefix.
     */
    fun echoContextAt(text: CharSequence, offset: Int): EchoContext? {
        if (offset < 0 || offset > text.length) return null
        // Find opening `{{` or `{!!` before caret without crossing `}}` / `!!}`.
        var i = offset - 1
        var openKind: String? = null
        var openAt = -1
        while (i >= 1) {
            val two = text.subSequence(i - 1, i + 1).toString()
            when (two) {
                "}}" -> return null
                "{{" -> {
                    openKind = "{{"
                    openAt = i - 1
                    break
                }
            }
            if (i >= 2) {
                val three = text.subSequence(i - 2, i + 1).toString()
                if (three == "!!}") return null
                if (three == "{!!") {
                    openKind = "{!!"
                    openAt = i - 2
                    break
                }
            }
            if (text[i] == '<' || text[i] == '>') return null
            i--
        }
        if (openKind == null || openAt < 0) return null
        // Must still be open (no closer between open and offset).
        val closer = if (openKind == "{{") "}}" else "!!}"
        val between = text.subSequence(openAt + openKind.length, offset).toString()
        if (between.contains(closer)) return null
        // Skip optional comment opener `{{--`
        if (openKind == "{{" && between.trimStart().startsWith("--")) return null
        val prefix = memberPrefix(between)
        return EchoContext(prefix)
    }

    fun isAttrNamePart(c: Char): Boolean =
        c == ':' || c == '.' || c == '-' || c == '_' || c.isLetterOrDigit()

    /** Identifier / dotted path fragment before `(` in a wire value or echo. */
    fun memberPrefix(raw: String): String {
        val beforeParen = raw.substringBefore('(')
        var start = beforeParen.length - 1
        while (start >= 0) {
            val c = beforeParen[start]
            if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '$') {
                start--
            } else {
                break
            }
        }
        return beforeParen.substring(start + 1)
    }

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
