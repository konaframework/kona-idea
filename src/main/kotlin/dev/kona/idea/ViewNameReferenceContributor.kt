package dev.kona.idea

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * Soft references for view name string literals.
 * Crema (flat PSI): scan the file element once.
 * Java/Kotlin string literals: attach a reference to the literal element.
 */
class ViewNameReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val text = element.text ?: return PsiReference.EMPTY_ARRAY
                    if (text.length >= 2 && (text[0] == '"' || text[0] == '\'')) {
                        val fileText = element.containingFile?.viewProvider?.contents
                            ?: return PsiReference.EMPTY_ARRAY
                        val quoteStart = element.textRange.startOffset
                        if (!ViewNamePatterns.isViewCallContext(fileText, quoteStart)) {
                            return PsiReference.EMPTY_ARRAY
                        }
                        val name = text.substring(1, text.length - 1)
                        if (name.isBlank() || name.any { it.isWhitespace() }) {
                            return PsiReference.EMPTY_ARRAY
                        }
                        return arrayOf(
                            ViewNameReference(element, TextRange(1, text.length - 1), name),
                        )
                    }

                    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
                    if (element !== file) return PsiReference.EMPTY_ARRAY
                    if (file.viewProvider.baseLanguage !== CremaLanguage &&
                        !CremaFileType.isCremaFileName(file.name)
                    ) {
                        return PsiReference.EMPTY_ARRAY
                    }
                    val hits = ViewNamePatterns.findAll(text)
                    if (hits.isEmpty()) return PsiReference.EMPTY_ARRAY
                    return hits.map { hit ->
                        ViewNameReference(element, TextRange(hit.start, hit.end), hit.name)
                    }.toTypedArray()
                }
            },
        )
    }
}
