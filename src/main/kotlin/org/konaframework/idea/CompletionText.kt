package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionParameters

/**
 * Resolves the buffer and caret offset used by pattern-based completion.
 *
 * Kotlin (and some other) completion inserts [DUMMY] into a *copy* of the file;
 * [CompletionParameters.offset] points into that copy. Reading
 * [CompletionParameters.originalFile] and clamping the offset to its length
 * moves the caret to the wrong place and breaks prefix / context detection.
 *
 * Always prefer [CompletionParameters.position]'s containing file, then rewind
 * the offset so the dummy identifier is not part of the typed prefix.
 */
object CompletionText {
    /** [com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED] */
    const val DUMMY: String = "IntellijIdeaRulezzz"

    data class Snapshot(
        val text: CharSequence,
        val offset: Int,
    )

    fun of(parameters: CompletionParameters): Snapshot =
        fromContents(
            parameters.position.containingFile.viewProvider.contents,
            parameters.offset,
        )

    /** Pure helper for tests and [of]. */
    fun fromContents(text: CharSequence, offset: Int): Snapshot {
        val raw = offset.coerceIn(0, text.length)
        return Snapshot(text, offsetBeforeDummy(text, raw))
    }

    /**
     * If the identifier ending at [offset] contains [DUMMY], return the index of
     * the dummy start so walkers only see the real typed prefix (or empty).
     */
    fun offsetBeforeDummy(text: CharSequence, offset: Int): Int {
        if (offset <= 0) return 0
        var i = offset.coerceIn(0, text.length) - 1
        while (i >= 0 && isIdentPart(text[i])) i--
        val start = i + 1
        val end = offset.coerceIn(0, text.length)
        if (start >= end) return end
        val ident = text.subSequence(start, end).toString()
        val dummyAt = ident.indexOf(DUMMY)
        return if (dummyAt >= 0) start + dummyAt else end
    }

    /** Strip a trailing / embedded completion dummy from a collected prefix. */
    fun stripDummy(prefix: String): String {
        val idx = prefix.indexOf(DUMMY)
        return if (idx >= 0) prefix.substring(0, idx) else prefix
    }

    fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
