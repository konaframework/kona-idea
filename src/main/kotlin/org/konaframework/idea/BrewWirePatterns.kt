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
     * Full member name under the caret for Go to Declaration
     * (`wire:submit="add"`, `wire:model="draft"`, `{{ remaining }}`).
     */
    data class MemberReference(
        val name: String,
        /** Attribute name when inside a `wire:*` value; null for echoes. */
        val attributeName: String?,
    ) {
        val expectsAction: Boolean
            get() = attributeName != null && BrewComponentIndex.isActionAttribute(attributeName)

        val expectsProperty: Boolean
            get() =
                attributeName == null ||
                    BrewComponentIndex.isPropertyAttribute(attributeName)
    }

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

    /**
     * Resolves the Brew member name under [offset] for navigation
     * (full identifier, not only the typed prefix).
     */
    fun memberReferenceAt(text: CharSequence, offset: Int): MemberReference? {
        valueMemberAt(text, offset)?.let { return it }
        echoMemberAt(text, offset)?.let { return it }
        return null
    }

    private fun valueMemberAt(text: CharSequence, offset: Int): MemberReference? {
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
        if (!BrewComponentIndex.isActionAttribute(attrName) &&
            !BrewComponentIndex.isPropertyAttribute(attrName)
        ) {
            return null
        }
        var valueEnd = valueStart
        while (valueEnd < text.length && text[valueEnd] != quote) valueEnd++
        if (offset < valueStart || offset > valueEnd) return null
        val value = text.subSequence(valueStart, valueEnd)
        val rel = (offset - valueStart).coerceIn(0, value.length)
        val raw = identifierAt(value, rel) ?: return null
        val name = navigationName(raw) ?: return null
        return MemberReference(name, attrName)
    }

    private fun echoMemberAt(text: CharSequence, offset: Int): MemberReference? {
        val echo = echoContextAt(text, offset) ?: return null
        // Re-scan for the full identifier around the caret inside the echo.
        var openAt = -1
        var openLen = 0
        var i = offset - 1
        while (i >= 1) {
            val two = text.subSequence(i - 1, i + 1).toString()
            if (two == "{{") {
                openAt = i - 1
                openLen = 2
                break
            }
            if (i >= 2 && text.subSequence(i - 2, i + 1).toString() == "{!!") {
                openAt = i - 2
                openLen = 3
                break
            }
            i--
        }
        if (openAt < 0) {
            val name = navigationName(echo.prefix) ?: return null
            return MemberReference(name, null)
        }
        val closer = if (openLen == 2) "}}" else "!!}"
        var closeAt = offset
        while (closeAt + closer.length <= text.length) {
            if (text.subSequence(closeAt, closeAt + closer.length).toString() == closer) break
            closeAt++
        }
        val inner = text.subSequence(openAt + openLen, closeAt.coerceAtMost(text.length))
        val rel = (offset - (openAt + openLen)).coerceIn(0, inner.length)
        val raw = identifierAt(inner, rel) ?: echo.prefix
        val name = navigationName(raw) ?: return null
        return MemberReference(name, null)
    }

    /** Top-level member name: `remove(…)` → `remove`, `items.done` → `items`. */
    fun navigationName(raw: String): String? {
        val stem = raw.substringBefore('(').trim()
        if (stem.isEmpty()) return null
        val simple = stem.substringBefore('.')
        return simple.takeIf { it.isNotEmpty() && it[0].isLetter() }
    }

    fun identifierAt(text: CharSequence, offset: Int): String? {
        if (text.isEmpty()) return null
        var pos = offset.coerceIn(0, text.length)
        if (pos > 0 && (pos == text.length || !isMemberIdentPart(text[pos]))) {
            pos--
        }
        if (pos < 0 || pos >= text.length || !isMemberIdentPart(text[pos])) return null
        var start = pos
        while (start > 0 && isMemberIdentPart(text[start - 1])) start--
        var end = pos + 1
        while (end < text.length && isMemberIdentPart(text[end])) end++
        return text.subSequence(start, end).toString()
    }

    fun isMemberIdentPart(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '.' || c == '$'

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
