package org.konaframework.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Files

/** Pure + PSI helpers for Brew / view-data Go to Declaration. */
object BrewMemberNavigation {
    fun resolveTarget(project: Project, viewFile: PsiFile, offset: Int): PsiElement? {
        val root = project.guessProjectDir()?.toNioPath() ?: return null
        if (!KonaLayout.isKonaApp(root)) return null
        val text = viewFile.viewProvider.contents
        val ref = BrewWirePatterns.memberReferenceAt(text, offset) ?: return null
        val host = CremaPsi.hostFile(viewFile)
        val nio = CremaPsi.hostPath(viewFile)
            ?: viewFile.virtualFile?.let { vf ->
                runCatching { VfsUtilCore.virtualToIoFile(vf).toPath() }.getOrNull()
            }
            ?: return null

        // Prefer Brew component that owns this view, or the host Component class
        // when the buffer is an injected `template()` string.
        BrewInjectionOwners.ownerFor(root, nio, host.text)?.let { owner ->
            elementForOwnerMember(project, owner, ref)?.let { return it }
        }

        // Controller / route data keys for page views (`{{ name }}` ← mapOf("name" to …)).
        val viewName = ViewIndex.relativeDisplay(root, nio) ?: return null
        val hit = ViewDataIndex.firstKey(root, viewName, ref.name) ?: return null
        if (!Files.isRegularFile(hit.sourcePath)) return null
        val sourceVf = LocalFileSystem.getInstance().findFileByNioFile(hit.sourcePath) ?: return null
        val psi = PsiManager.getInstance(project).findFile(sourceVf) ?: return null
        return psi.findElementAt(hit.offset) ?: psi
    }

    private fun elementForOwnerMember(
        project: Project,
        owner: BrewComponentIndex.ComponentInfo,
        ref: BrewWirePatterns.MemberReference,
    ): PsiElement? {
        val sourcePath = owner.sourcePath ?: return null
        if (!Files.isRegularFile(sourcePath)) return null
        val source = runCatching { Files.readString(sourcePath) }.getOrNull() ?: return null
        val prefer =
            when {
                ref.expectsAction -> BrewComponentIndex.MemberKind.ACTION
                ref.expectsProperty -> BrewComponentIndex.MemberKind.PROPERTY
                else -> null
            }
        val known = owner.members.firstOrNull { it.name == ref.name }
        val kind = known?.kind ?: prefer
        val memberOffset = BrewComponentIndex.offsetOfMember(source, ref.name, kind) ?: return null
        val sourceVf = LocalFileSystem.getInstance().findFileByNioFile(sourcePath) ?: return null
        val psi = PsiManager.getInstance(project).findFile(sourceVf) ?: return null
        return psi.findElementAt(memberOffset) ?: psi
    }
}
