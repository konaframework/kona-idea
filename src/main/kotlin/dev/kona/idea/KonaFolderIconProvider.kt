package dev.kona.idea

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * Marks canonical Kona layout folders (bootstrap, routes, views, storage, …)
 * with the official Kona brand mark in the project view.
 */
class KonaFolderIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is PsiDirectory) return null
        val project = element.project
        val projectRoot = project.guessProjectDir() ?: return null
        val dir = element.virtualFile
        if (!dir.path.startsWith(projectRoot.path)) return null
        val relative =
            projectRoot.toNioPath().relativize(dir.toNioPath()).toString().replace('\\', '/')
        if (relative.isEmpty()) return null
        val detection = KonaLayout.detect(projectRoot.toNioPath())
        if (!detection.isKonaApp) return null
        val markers = KonaLayout.relativeMarkerDirs(detection.language).toSet() +
            setOf("storage", "storage/app", "storage/logs", "storage/framework")
        return if (relative in markers) KonaIcons.KONA else null
    }
}
