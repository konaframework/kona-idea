package org.konaframework.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

/**
 * Ctrl+B / Ctrl+Click (Go to Declaration) for view names when PSI references are thin.
 * Documented shortcut: **Ctrl+B** (Windows/Linux) or **⌘B** (macOS); also Ctrl+Click.
 */
class ViewGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null
        val project = sourceElement.project
        val root = project.guessProjectDir()?.toNioPath() ?: return null
        val file = sourceElement.containingFile ?: return null
        val text = file.viewProvider.contents
        val hit = ViewNamePatterns.viewNameAt(text, offset) ?: return null
        val path = ViewIndex.resolve(root, hit.name) ?: return null
        val vf =
            com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByNioFile(path) ?: return null
        val psi = PsiManager.getInstance(project).findFile(vf) ?: return null
        return arrayOf(psi)
    }
}
