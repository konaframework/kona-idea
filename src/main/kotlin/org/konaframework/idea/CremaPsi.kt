package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * PSI helpers for Crema files and injected Crema fragments.
 * Platform wiring — excluded from Kover; logic lives in [CremaContexts] / [CremaInjectionSites].
 */
object CremaPsi {
    fun isCrema(file: PsiFile): Boolean {
        val injected =
            runCatching {
                InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)
            }.getOrDefault(false)
        val injectedLanguage =
            if (injected) file.language.id else null
        return CremaContexts.looksLikeCrema(
            fileName = file.name,
            languageId = file.language.id,
            baseLanguageId = file.viewProvider.baseLanguage.id,
            injected = injected,
            injectedLanguageId = injectedLanguage,
        )
    }

    fun hostFile(file: PsiFile): PsiFile {
        val top =
            runCatching {
                InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
            }.getOrNull()
        return top ?: file
    }

    fun completionFile(parameters: CompletionParameters): PsiFile? {
        val positionFile = parameters.position.containingFile
        if (isCrema(positionFile)) return positionFile
        if (isCrema(parameters.originalFile)) return parameters.originalFile
        return null
    }

    fun snapshot(parameters: CompletionParameters): CompletionText.Snapshot? {
        val file = completionFile(parameters) ?: return null
        val text = file.viewProvider.contents
        return CompletionText.fromContents(text, parameters.offset.coerceIn(0, text.length))
    }

    fun hostPath(file: PsiFile): Path? {
        val host = hostFile(file)
        val vf = host.virtualFile ?: file.virtualFile ?: return null
        return runCatching { VfsUtilCore.virtualToIoFile(vf).toPath() }.getOrNull()
    }
}
