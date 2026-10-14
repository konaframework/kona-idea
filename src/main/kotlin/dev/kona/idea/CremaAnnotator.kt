package dev.kona.idea

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Flags `@unknown` tokens that look like directives but are not in [CremaDirectives].
 * Pure scanning lives in [CremaDirectiveDiagnostics].
 */
class CremaAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile.viewProvider.baseLanguage !== CremaLanguage) return
        // Annotate once at the file root to avoid duplicate markers.
        if (element.parent !is CremaFile && element !is CremaFile) return
        val file = element.containingFile
        if (element !== file) return
        CremaDirectiveDiagnostics.findUnknown(file.text).forEach { hit ->
            holder
                .newAnnotation(HighlightSeverity.WARNING, "Unknown Crema directive @${hit.name}")
                .range(TextRange(hit.start, hit.end))
                .create()
        }
    }
}

data class UnknownDirective(val name: String, val start: Int, val end: Int)

object CremaDirectiveDiagnostics {
    fun findUnknown(text: CharSequence): List<UnknownDirective> {
        val out = mutableListOf<UnknownDirective>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '@' && (i == 0 || text[i - 1] != '@')) {
                val name = CremaDirectives.directiveNameAt(text, i)
                if (name != null && !CremaDirectives.isKnown(name) && looksLikeDirectiveCall(text, i, name)) {
                    out += UnknownDirective(name, i, i + 1 + name.length)
                }
            }
            i++
        }
        return out
    }

    /** True when `@name` is a call site, not `user@host.tld`. */
    internal fun looksLikeDirectiveCall(text: CharSequence, atOffset: Int, name: String): Boolean {
        val after = atOffset + 1 + name.length
        if (after >= text.length) return true
        val next = text[after]
        if (next == '.') return false
        return next == '(' || next.isWhitespace() || next == '"' || next == '\'' || next == '<' || next == '/'
    }
}
