package org.konaframework.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Files

/** Resolve `<brew:…>` tags / attributes to Brew component PSI. */
object BrewTagNavigation {
    fun resolveTarget(project: Project, viewFile: PsiFile, offset: Int): PsiElement? {
        val root = project.guessProjectDir()?.toNioPath() ?: return null
        if (!KonaLayout.isKonaApp(root)) return null
        val text = viewFile.viewProvider.contents

        BrewTagPatterns.componentNameAt(text, offset)?.let { name ->
            return elementForComponent(project, root, name, member = null)
        }

        BrewTagPatterns.attributeReferenceAt(text, offset)?.let { ref ->
            val info = BrewComponentIndex.findComponent(root, ref.componentName) ?: return null
            val member = BrewComponentIndex.resolveAttributeMember(info, ref.attributeName)
            return elementForComponent(project, root, ref.componentName, member)
        }

        return null
    }

    fun elementForComponent(
        project: Project,
        root: java.nio.file.Path,
        componentName: String,
        member: String?,
    ): PsiElement? {
        val info = BrewComponentIndex.findComponent(root, componentName) ?: return null
        val sourcePath = info.sourcePath ?: return null
        if (!Files.isRegularFile(sourcePath)) return null
        val source = runCatching { Files.readString(sourcePath) }.getOrNull() ?: return null
        val memberOffset =
            if (member != null) {
                BrewComponentIndex.offsetOfMember(source, member, BrewComponentIndex.MemberKind.PROPERTY)
                    ?: BrewComponentIndex.offsetOfMember(source, member, BrewComponentIndex.MemberKind.ACTION)
                    ?: BrewComponentIndex.offsetOfClass(source, info.className)
            } else {
                BrewComponentIndex.offsetOfClass(source, info.className)
            } ?: return null
        val sourceVf = LocalFileSystem.getInstance().findFileByNioFile(sourcePath) ?: return null
        val psi = PsiManager.getInstance(project).findFile(sourceVf) ?: return null
        return psi.findElementAt(memberOffset) ?: psi
    }
}
