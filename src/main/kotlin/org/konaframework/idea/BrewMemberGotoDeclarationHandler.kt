package org.konaframework.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Ctrl+B / Ctrl+Click from `wire:submit` / `wire:model` / `{{ }}` to the owning
 * Brew component property or action.
 */
class BrewMemberGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        val file = sourceElement.containingFile ?: return null
        if (!isCremaFile(file)) return null
        val target = BrewMemberNavigation.resolveTarget(sourceElement.project, file, offset) ?: return null
        return arrayOf(target)
    }

    private fun isCremaFile(file: com.intellij.psi.PsiFile): Boolean =
        file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
}
