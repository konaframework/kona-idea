package org.konaframework.idea

/**
 * Detects `<brew:…` typing contexts in Crema HTML.
 *
 * Brew tags map to [BrewComponentIndex] names (`Counter` → `counter`,
 * `@BrewComponent("posts")` → `posts`), rendered as `<brew:counter />`.
 */
object BrewTagPatterns {
    const val PREFIX: String = "brew:"

    data class TagPrefix(
        /** Component-name prefix after `brew:` (may be empty). */
        val prefix: String,
        /**
         * True when `<brew:` is already in the document — insert only the
         * component name. False when the user is still typing `brew` / `br`.
         */
        val afterColon: Boolean,
    )

    /** Prefix after `<brew:` (or while typing `brew` / `brew:`) at [offset]. */
    fun tagPrefixAt(text: CharSequence, offset: Int): TagPrefix? {
        var i = offset - 1
        while (i >= 0 && isTagPart(text[i])) i--
        val before = text.subSequence(0, i + 1).toString()
        when {
            before.endsWith("<$PREFIX") ->
                return TagPrefix(text.subSequence(i + 1, offset).toString(), afterColon = true)
            before.endsWith("<brew") && offset > i + 1 && text[i + 1] == ':' ->
                return TagPrefix(text.subSequence(i + 2, offset).toString(), afterColon = true)
        }
        if (before.endsWith("<") && offset > i + 1) {
            val typed = text.subSequence(i + 1, offset).toString()
            if (typed.startsWith(PREFIX, ignoreCase = true)) {
                return TagPrefix(typed.substring(PREFIX.length), afterColon = true)
            }
            if (typed.equals("brew", ignoreCase = true) ||
                typed.equals("bre", ignoreCase = true)
            ) {
                // Require ≥3 chars so `<b` / `<br` stay normal HTML completion.
                return TagPrefix("", afterColon = false)
            }
        }
        return null
    }

    fun isBrewTagName(name: String): Boolean =
        name.startsWith(PREFIX, ignoreCase = true)

    /** Component name inside `<brew:todos>` / `brew:todos`, or null if not a brew tag. */
    fun componentNameFromTag(name: String): String? {
        if (!isBrewTagName(name)) return null
        return name.substring(PREFIX.length)
    }

    fun htmlTag(componentName: String): String = PREFIX + componentName

    fun isTagPart(c: Char): Boolean =
        c == '_' || c == '-' || c == '.' || c == '/' || c.isLetterOrDigit()

    /**
     * When the caret is on the component name in `<brew:counter` / `</brew:counter>`,
     * returns that name (e.g. `counter`).
     */
    fun componentNameAt(text: CharSequence, offset: Int): String? {
        if (offset < 0 || offset > text.length) return null
        var pos = offset.coerceIn(0, text.length)
        if (pos > 0 && (pos == text.length || !isTagPart(text[pos]))) pos--
        if (pos < 0 || pos >= text.length || !isTagPart(text[pos])) return null
        var start = pos
        while (start > 0 && isTagPart(text[start - 1])) start--
        var end = pos + 1
        while (end < text.length && isTagPart(text[end])) end++
        val token = text.subSequence(start, end).toString()
        if (token.startsWith(PREFIX, ignoreCase = true)) {
            return token.substring(PREFIX.length).takeIf { it.isNotEmpty() }
        }
        // Bare name after `<brew:` / `</brew:`
        if (start < 5) return null
        val before = text.subSequence(0, start).toString()
        if (before.endsWith("<$PREFIX", ignoreCase = true) ||
            before.endsWith("</$PREFIX", ignoreCase = true)
        ) {
            return token.takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * Caret inside a `<brew:name …>` open tag while typing an attribute name
     * (not inside a quoted value).
     */
    data class AttributeNameContext(
        val componentName: String,
        val prefix: String,
    )

    fun attributeNameContextAt(text: CharSequence, offset: Int): AttributeNameContext? {
        if (offset < 0 || offset > text.length) return null
        // Not inside quotes.
        if (insideQuotes(text, offset)) return null
        val open = openBrewTagBefore(text, offset) ?: return null
        // Attribute name fragment before caret.
        var i = offset - 1
        while (i >= open.nameEnd && BrewWirePatterns.isAttrNamePart(text[i])) i--
        val prefixStart = i + 1
        if (prefixStart < open.nameEnd) return null
        // Must be after whitespace or tag name end (start of attrs).
        if (prefixStart > open.nameEnd) {
            val prev = text[prefixStart - 1]
            if (!prev.isWhitespace() && prev != '"' && prev != '\'') return null
        }
        val prefix = text.subSequence(prefixStart, offset).toString()
        return AttributeNameContext(open.componentName, prefix)
    }

    /**
     * Caret on an attribute name (not value) of a `<brew:…>` tag — for Go to Declaration.
     */
    data class AttributeReference(
        val componentName: String,
        /** Attribute as written (`:start`, `count`, `wire:key`). */
        val attributeName: String,
    )

    fun attributeReferenceAt(text: CharSequence, offset: Int): AttributeReference? {
        if (offset < 0 || offset > text.length) return null
        if (insideQuotes(text, offset)) return null
        val open = openBrewTagBefore(text, offset) ?: return null
        var pos = offset.coerceIn(0, text.length)
        if (pos > 0 && (pos == text.length || !BrewWirePatterns.isAttrNamePart(text[pos]))) pos--
        if (pos < open.nameEnd || pos >= text.length) return null
        if (!BrewWirePatterns.isAttrNamePart(text[pos])) return null
        var start = pos
        while (start > open.nameEnd && BrewWirePatterns.isAttrNamePart(text[start - 1])) start--
        var end = pos + 1
        while (end < text.length && BrewWirePatterns.isAttrNamePart(text[end])) end++
        val attr = text.subSequence(start, end).toString()
        if (attr.isEmpty()) return null
        // Ensure we are not past `=` into the value (already excluded by quotes).
        return AttributeReference(open.componentName, attr)
    }

    private data class OpenBrewTag(
        val componentName: String,
        /** Index after the component name token. */
        val nameEnd: Int,
    )

    private fun openBrewTagBefore(text: CharSequence, offset: Int): OpenBrewTag? {
        var i = offset - 1
        var sawClose = false
        while (i >= 0) {
            when (text[i]) {
                '>' -> {
                    sawClose = true
                    break
                }
                '<' -> break
            }
            i--
        }
        if (sawClose || i < 0 || text[i] != '<') return null
        var j = i + 1
        if (j < text.length && text[j] == '/') return null // closing tag
        if (j + PREFIX.length > text.length) return null
        if (!text.subSequence(j, j + PREFIX.length).toString().equals(PREFIX, ignoreCase = true)) {
            return null
        }
        j += PREFIX.length
        val nameStart = j
        while (j < text.length && isTagPart(text[j])) j++
        if (j == nameStart) return null
        // Tag must still be open at [offset].
        var k = offset
        while (k < text.length) {
            when (text[k]) {
                '>' -> break
                '<' -> return null
            }
            k++
        }
        return OpenBrewTag(text.subSequence(nameStart, j).toString(), j)
    }

    private fun insideQuotes(text: CharSequence, offset: Int): Boolean {
        var i = offset - 1
        var quote: Char? = null
        while (i >= 0) {
            val c = text[i]
            when {
                c == '"' || c == '\'' -> {
                    quote = c
                    break
                }
                c == '<' || c == '>' || c == '\n' -> return false
            }
            i--
        }
        if (quote == null) return false
        var k = i + 1
        while (k < offset) {
            if (text[k] == quote) return false
            k++
        }
        return true
    }
}
