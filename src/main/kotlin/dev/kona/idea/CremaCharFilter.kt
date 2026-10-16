package dev.kona.idea

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup

/**
 * Keeps Crema `@directive` and `<x-*>` completion open while typing identifier /
 * tag characters (default CharFilter would hide the popup on `@`, `-`, `/`, `.`, `:`).
 */
class CremaCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
        val file = lookup.psiFile ?: return null
        if (!isCrema(file)) return null
        if (!lookup.isCompletion) return null

        val editor = lookup.editor
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset.coerceIn(0, text.length)

        if (CremaCompletion.prefixBefore(text, offset).isNotEmpty() ||
            CremaCompletion.justTypedAt(text, offset)
        ) {
            if (c == '@' || CremaDirectives.isIdentPart(c)) {
                return Result.ADD_TO_PREFIX
            }
        }

        if (XComponentPatterns.tagPrefixAt(text, offset) != null ||
            XComponentPatterns.wantsSlotChild(text, offset)
        ) {
            if (c == '-' || c == '/' || c == '.' || c == ':' || c == '_' ||
                c.isLetterOrDigit()
            ) {
                return Result.ADD_TO_PREFIX
            }
        }
        return null
    }

    private fun isCrema(file: com.intellij.psi.PsiFile): Boolean =
        file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
}
