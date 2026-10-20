package org.konaframework.idea

/**
 * Resolves the buffer and caret offset used by pattern-based completion.
 *
 * Kotlin (and some other) completion inserts [DUMMY] into a *copy* of the file;
 * [CompletionParameters.offset] points into that copy. The dummy is sometimes
 * only a prefix (`Intell`) or has a trailing space (`DUMMY_IDENTIFIER`).
 *
 * Contributors should read `parameters.position.containingFile` (the copy) and
 * rewind with [offsetBeforeDummy] so the dummy is not part of the typed prefix.
 */
object CompletionText {
    /** [com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED] */
    const val DUMMY: String = "IntellijIdeaRulezzz"

    /** [com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER] */
    const val DUMMY_WITH_SPACE: String = "$DUMMY "

    data class Snapshot(
        val text: CharSequence,
        val offset: Int,
    )

    /** Pure helper for tests and completion contributors. */
    fun fromContents(text: CharSequence, offset: Int): Snapshot {
        val raw = offset.coerceIn(0, text.length)
        return Snapshot(text, offsetBeforeDummy(text, raw))
    }

    /**
     * If a dummy identifier sits just before [offset], return the index of the dummy start
     * so walkers only see the real typed prefix (or empty).
     */
    fun offsetBeforeDummy(text: CharSequence, offset: Int): Int {
        val end = offset.coerceIn(0, text.length)
        if (end <= 0) return 0
        val searchStart = (end - DUMMY.length - 8).coerceAtLeast(0)
        val window = text.subSequence(searchStart, end).toString()
        val full = window.lastIndexOf(DUMMY)
        if (full >= 0) return searchStart + full
        var i = end - 1
        while (i >= 0 && (isIdentPart(text[i]) || text[i] == ' ')) {
            if (text[i] == ' ') {
                i--
                break
            }
            i--
        }
        val start = i + 1
        if (start >= end) return end
        val ident = text.subSequence(start, end).toString().trimEnd()
        val dummyAt = indexOfDummy(ident)
        return if (dummyAt >= 0) start + dummyAt else end
    }

    /** Strip a trailing / embedded completion dummy from a collected prefix. */
    fun stripDummy(prefix: String): String {
        val exact = prefix.indexOf(DUMMY)
        if (exact >= 0) return prefix.substring(0, exact)
        val dummyAt = indexOfDummy(prefix)
        return if (dummyAt >= 0) prefix.substring(0, dummyAt) else prefix
    }

    /**
     * Index of a full dummy, or of a suffix that is a prefix of [DUMMY]
     * (caret in the middle of the dummy identifier).
     */
    fun indexOfDummy(ident: String): Int {
        val exact = ident.indexOf(DUMMY)
        if (exact >= 0) return exact
        val max = minOf(ident.length, DUMMY.length)
        var n = max
        while (n >= 4) {
            if (DUMMY.startsWith(ident.takeLast(n))) return ident.length - n
            n--
        }
        return -1
    }

    fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
