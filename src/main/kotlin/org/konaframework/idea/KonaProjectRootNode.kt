package org.konaframework.idea

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager

/**
 * Root of the Kona Project View — lists canonical scaffold entries with short names.
 * Directory entries always precede files (structure order, then IDE type-sort).
 */
class KonaProjectRootNode(
    project: Project,
    settings: ViewSettings,
) : ProjectViewNode<Project>(project, project, settings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val projectDir = myProject.guessProjectDir() ?: return emptyList()
        val rootPath = projectDir.toNioPath()
        val detection = KonaLayout.detect(rootPath)
        val entries =
            KonaProjectStructure.entries(detection) { rel ->
                projectDir.findFileByRelativePath(rel) != null
            }
        if (entries.isEmpty()) {
            return fallbackChildren(projectDir)
        }
        val psi = PsiManager.getInstance(myProject)
        val dirs = mutableListOf<AbstractTreeNode<*>>()
        val files = mutableListOf<AbstractTreeNode<*>>()
        for (entry in entries) {
            val vf = projectDir.findFileByRelativePath(entry.relativePath) ?: continue
            if (vf.isDirectory) {
                val dir = psi.findDirectory(vf) ?: continue
                dirs +=
                    when (entry.displayName) {
                        "resources" ->
                            KonaResourcesDirectoryNode(myProject, dir, settings)
                        else ->
                            KonaScaffoldDirectoryNode(myProject, dir, settings, entry.displayName)
                    }
            } else {
                val file = psi.findFile(vf) ?: continue
                files += PsiFileNode(myProject, file, settings)
            }
        }
        return dirs + files
    }

    private fun fallbackChildren(projectDir: VirtualFile): Collection<AbstractTreeNode<*>> {
        val dir = PsiManager.getInstance(myProject).findDirectory(projectDir) ?: return emptyList()
        return listOf(PsiDirectoryNode(myProject, dir, settings))
    }

    override fun contains(file: VirtualFile): Boolean {
        val projectDir = myProject.guessProjectDir() ?: return false
        return file.path.startsWith(projectDir.path)
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = myProject.name
        presentation.setIcon(KonaIcons.KONA)
    }

    override fun getVirtualFile(): VirtualFile? = myProject.guessProjectDir()
}

/** Psi directory node with a short scaffold label (e.g. `public` not `resources/public`). */
private class KonaScaffoldDirectoryNode(
    project: Project,
    value: PsiDirectory,
    viewSettings: ViewSettings,
    private val label: String,
) : PsiDirectoryNode(project, value, viewSettings) {
    override fun update(data: PresentationData) {
        super.update(data)
        data.presentableText = label
        data.setIcon(KonaIcons.KONA)
    }
}

/**
 * Custom `resources` node: lists children of `src/main/resources` except `public`,
 * which is promoted to its own top-level Kona Project View entry.
 *
 * Extends [PsiDirectoryNode] so the Project View type-sort treats it as a folder
 * (folders first), not as a trailing miscellaneous node after `.env` / scripts.
 */
private class KonaResourcesDirectoryNode(
    project: Project,
    value: PsiDirectory,
    viewSettings: ViewSettings,
) : PsiDirectoryNode(project, value, viewSettings) {
    override fun getChildrenImpl(): Collection<AbstractTreeNode<*>> {
        val directory = value ?: return emptyList()
        val psi = PsiManager.getInstance(myProject)
        val dirs = mutableListOf<AbstractTreeNode<*>>()
        val files = mutableListOf<AbstractTreeNode<*>>()
        for (child in directory.virtualFile.children.orEmpty().sortedBy { it.name.lowercase() }) {
            if (child.isDirectory && child.name.equals("public", ignoreCase = true)) {
                continue
            }
            if (child.isDirectory) {
                val dir = psi.findDirectory(child) ?: continue
                dirs += PsiDirectoryNode(myProject, dir, settings)
            } else {
                val file = psi.findFile(child) ?: continue
                files += PsiFileNode(myProject, file, settings)
            }
        }
        return dirs + files
    }

    override fun contains(file: VirtualFile): Boolean {
        val root = virtualFile ?: return false
        if (!file.path.startsWith(root.path)) return false
        val publicDir = root.findChild("public")
        return publicDir == null || !file.path.startsWith(publicDir.path)
    }

    override fun update(data: PresentationData) {
        super.update(data)
        data.presentableText = "resources"
        data.setIcon(KonaIcons.KONA)
    }
}
