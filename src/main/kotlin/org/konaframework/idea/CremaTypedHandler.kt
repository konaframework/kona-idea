package org.konaframework.idea

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Crema typed handler:
 * - `{{` auto-close
 * - `@` directive autopopup (and continue while typing the directive name)
 * - `<x-` component autopopup
 *
 * Letter keys only intercept autopopup when already inside an `@directive` or
 * `<x-…>` context so normal HTML tag completion still works.
 *
 * Uses [AutoPopupController.scheduleAutoPopup] with Editor only (2024.2 API).
 * Context gating happens in completion contributors.
 */
class CremaTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (!isCrema(file)) return Result.CONTINUE

        if (c != '{') return Result.CONTINUE
        val document = editor.document
        val caret = editor.caretModel.offset
        if (caret < 2) return Result.CONTINUE
        val text = document.charsSequence
        if (text[caret - 1] != '{' || text[caret - 2] != '{') return Result.CONTINUE
        if (caret >= 3 && text[caret - 3] == '{') return Result.CONTINUE
        if (text.startsWith("}}", caret)) return Result.CONTINUE

        var insertAt = caret
        if (insertAt < text.length && text[insertAt] == '}' &&
            (insertAt + 1 >= text.length || text[insertAt + 1] != '}')
        ) {
            document.deleteString(insertAt, insertAt + 1)
        }

        document.insertString(insertAt, "  }}")
        editor.caretModel.moveToOffset(insertAt + 1)
        return Result.STOP
    }

    override fun checkAutoPopup(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        // PSI may be stale — gate on file name / view-provider base language only.
        if (!looksLikeCrema(file)) return Result.CONTINUE

        val popup = AutoPopupController.getInstance(project)
        when {
            c == '@' -> {
                popup.scheduleAutoPopup(editor)
                return Result.STOP
            }
            CremaDirectives.isIdentPart(c) -> {
                if (!directivePrefixAtCaret(editor) && !xComponentContext(editor)) {
                    return Result.CONTINUE
                }
                popup.scheduleAutoPopup(editor)
                return Result.STOP
            }
            c == '<' || c == '-' || c == '/' || c == '.' || c == ':' -> {
                // Schedule; contributors no-op when not in an x-component context.
                popup.scheduleAutoPopup(editor)
                return Result.STOP
            }
            c == '{' || c == '}' -> return Result.STOP
        }
        return Result.CONTINUE
    }

    private fun directivePrefixAtCaret(editor: Editor): Boolean {
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset.coerceIn(0, text.length)
        return CremaCompletion.shouldAutopopup(text, offset)
    }

    private fun xComponentContext(editor: Editor): Boolean {
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset.coerceIn(0, text.length)
        return XComponentPatterns.tagPrefixAt(text, offset) != null ||
            XComponentPatterns.wantsSlotChild(text, offset)
    }

    private fun looksLikeCrema(file: PsiFile): Boolean =
        CremaFileType.isCremaFileName(file.name) ||
            file.viewProvider.baseLanguage === CremaLanguage

    private fun isCrema(file: PsiFile): Boolean =
        file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
}
