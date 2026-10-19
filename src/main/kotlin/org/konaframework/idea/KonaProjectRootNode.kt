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
        return entries.mapNotNull { entry ->
            val vf = projectDir.findFileByRelativePath(entry.relativePath) ?: return@mapNotNull null
            if (vf.isDirectory) {
                val dir = psi.findDirectory(vf) ?: return@mapNotNull null
                when (entry.displayName) {
                    "resources" ->
                        KonaResourcesDirectoryNode(myProject, dir, settings)
                    else ->
                        KonaScaffoldDirectoryNode(myProject, dir, settings, entry.displayName)
                }
            } else {
                val file = psi.findFile(vf) ?: return@mapNotNull null
                PsiFileNode(myProject, file, settings)
            }
        }
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
 */
private class KonaResourcesDirectoryNode(
    project: Project,
    private val directory: PsiDirectory,
    private val viewSettings: ViewSettings,
) : ProjectViewNode<PsiDirectory>(project, directory, viewSettings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val psi = PsiManager.getInstance(myProject)
        val out = mutableListOf<AbstractTreeNode<*>>()
        for (child in directory.virtualFile.children.orEmpty()) {
            if (child.isDirectory && child.name.equals("public", ignoreCase = true)) {
                continue
            }
            if (child.isDirectory) {
                val dir = psi.findDirectory(child) ?: continue
                out += PsiDirectoryNode(myProject, dir, viewSettings)
            } else {
                val file = psi.findFile(child) ?: continue
                out += PsiFileNode(myProject, file, viewSettings)
            }
        }
        return out
    }

    override fun contains(file: VirtualFile): Boolean {
        val root = directory.virtualFile
        if (!file.path.startsWith(root.path)) return false
        // Files under resources/public belong to the top-level public node, not here.
        val publicDir = root.findChild("public")
        return publicDir == null || !file.path.startsWith(publicDir.path)
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = "resources"
        presentation.setIcon(KonaIcons.KONA)
    }

    override fun getVirtualFile(): VirtualFile = directory.virtualFile

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = false

    override fun navigate(requestFocus: Boolean) {
        // Opening the directory in the project view is enough; no editor navigation.
    }
}
