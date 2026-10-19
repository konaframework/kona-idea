package org.konaframework.idea

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Paints HTML with the platform highlighter and Crema syntax on top. */
class CremaEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter = CremaEditorHighlighter(project, virtualFile, colors)
}

class CremaEditorHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme,
) : LayeredLexerEditorHighlighter(CremaSyntaxHighlighter, colors) {
    init {
        val html =
            SyntaxHighlighterFactory.getSyntaxHighlighter(
                HTMLLanguage.INSTANCE,
                project,
                virtualFile,
            )
        registerLayer(CremaTokens.TEMPLATE_DATA, LayerDescriptor(html, ""))
    }
}
