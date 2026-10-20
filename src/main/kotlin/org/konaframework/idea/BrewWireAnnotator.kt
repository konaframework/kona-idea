package org.konaframework.idea

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Flags unknown `wire:*` attributes in Crema HTML (Brew).
 * Pure scanning lives in [BrewWirePatterns.findUnknown].
 */
class BrewWireAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!CremaPsi.isCrema(element.containingFile)) return
        if (element.parent !is CremaFile && element !is CremaFile) return
        val file = element.containingFile
        if (element !== file) return
        BrewWirePatterns.findUnknown(file.text).forEach { hit ->
            holder
                .newAnnotation(
                    HighlightSeverity.WEAK_WARNING,
                    "Unknown Brew attribute ${hit.name}",
                )
                .range(TextRange(hit.start, hit.end))
                .create()
        }
    }
}
