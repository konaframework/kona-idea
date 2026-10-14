package dev.kona.idea

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

class CremaSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
        project: Project?,
        virtualFile: VirtualFile?,
    ): SyntaxHighlighter = CremaSyntaxHighlighter
}

object CremaSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = CremaLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            CremaTokens.COMMENT -> COMMENT_KEYS
            CremaTokens.ECHO, CremaTokens.RAW_ECHO -> ECHO_KEYS
            CremaTokens.DIRECTIVE -> DIRECTIVE_KEYS
            else -> EMPTY
        }

    private val COMMENT_KEYS = arrayOf(CremaColors.COMMENT)
    private val ECHO_KEYS = arrayOf(CremaColors.ECHO)
    private val DIRECTIVE_KEYS = arrayOf(CremaColors.DIRECTIVE)
    private val EMPTY = emptyArray<TextAttributesKey>()
}

object CremaColors {
    val COMMENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(
            "CREMA_COMMENT",
            DefaultLanguageHighlighterColors.BLOCK_COMMENT,
        )
    val ECHO: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(
            "CREMA_ECHO",
            DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR,
        )
    val DIRECTIVE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(
            "CREMA_DIRECTIVE",
            DefaultLanguageHighlighterColors.KEYWORD,
        )
}
