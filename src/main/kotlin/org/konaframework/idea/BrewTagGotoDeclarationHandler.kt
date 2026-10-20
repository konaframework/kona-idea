package org.konaframework.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Ctrl+B / Ctrl+Click on `<brew:counter` → [Counter] class;
 * on `:start` / `count` attributes → property or `mount` parameter.
 */
class BrewTagGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        val file = sourceElement.containingFile ?: return null
        if (!CremaPsi.isCrema(file)) return null
        val target = BrewTagNavigation.resolveTarget(sourceElement.project, file, offset) ?: return null
        return arrayOf(target)
    }
}
