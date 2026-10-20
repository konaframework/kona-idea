package org.konaframework.idea

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.FileIndentOptionsProvider

/**
 * Crema files use the same indent size / tabs as **Settings → Editor → Code Style → HTML**.
 */
class CremaFileIndentOptionsProvider : FileIndentOptionsProvider() {
    override fun getIndentOptions(
        settings: CodeStyleSettings,
        file: PsiFile,
    ): CommonCodeStyleSettings.IndentOptions? {
        if (!CremaFormatting.appliesToFileName(file.name)) {
            return null
        }
        return settings.getIndentOptions(HtmlFileType.INSTANCE)
    }

    override fun getIndentOptions(
        project: Project,
        settings: CodeStyleSettings,
        file: VirtualFile,
    ): CommonCodeStyleSettings.IndentOptions? {
        if (!CremaFormatting.appliesToFileName(file.name)) {
            return null
        }
        return settings.getIndentOptions(HtmlFileType.INSTANCE)
    }
}
