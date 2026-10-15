package dev.kona.idea

import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

/** Resolves a dotted view name to the Crema template file (Ctrl+B / Ctrl+Click). */
class ViewNameReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val viewName: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val root = project.guessProjectDir()?.toNioPath() ?: return null
        val path = ViewIndex.resolve(root, viewName) ?: return null
        val vf =
            com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByNioFile(path) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }

    override fun getVariants(): Array<Any> {
        val root = element.project.guessProjectDir()?.toNioPath() ?: return emptyArray()
        return ViewIndex.listNames(root).toTypedArray()
    }
}
