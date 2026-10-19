package org.konaframework.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Ctrl+B / Ctrl+Click when the caret is inside an env key and the leaf PSI
 * element does not own the reference (Kotlin string template entries).
 */
class EnvGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        val file = sourceElement.containingFile ?: return null
        val text = file.viewProvider.contents
        val use = EnvKeys.usageAt(text, offset) ?: return null
        val target = resolveEnvKey(sourceElement, use.name) ?: return null
        return arrayOf(target)
    }
}
