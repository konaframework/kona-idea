package org.konaframework.idea

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/** Attaches [EnvKeyReference] to `Env.get` / `getOrDefault` / `require` string literals. */
class EnvKeyReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val text = element.text ?: return PsiReference.EMPTY_ARRAY
                    if (text.length < 3) return PsiReference.EMPTY_ARRAY
                    val quote = text[0]
                    if ((quote != '"' && quote != '\'') || text.last() != quote) {
                        return PsiReference.EMPTY_ARRAY
                    }
                    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
                    val fileText = file.viewProvider.contents
                    val start = element.textRange.startOffset
                    val use = EnvKeys.usageAt(fileText, start + 1) ?: return PsiReference.EMPTY_ARRAY
                    val end = element.textRange.endOffset
                    if (use.start < start || use.end > end) return PsiReference.EMPTY_ARRAY
                    return arrayOf(
                        EnvKeyReference(element, TextRange(use.start - start, use.end - start), use.name),
                    )
                }
            },
        )
    }
}
