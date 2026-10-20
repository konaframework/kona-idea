package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

/**
 * Native `@directive` completion for Crema views.
 *
 * Crema uses a template-language view provider with an HTML data language, so the caret
 * is often an HTML PSI element. Registered for `language="any"` in plugin.xml and gated
 * on the Crema file type / base language.
 *
 * Autopopup is triggered by [CremaTypedHandler] when typing `@` (and while typing the
 * directive name). [CremaCharFilter] keeps the lookup open for identifier characters.
 */
class CremaCompletionContributor : CompletionContributor(), DumbAware {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    if (!isCremaFile(parameters.originalFile)) return
                    val text = parameters.originalFile.viewProvider.contents
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val prefix = CremaCompletion.prefixBefore(text, offset)
                    if (prefix.isEmpty() && !CremaCompletion.justTypedAt(text, offset)) return
                    val effectivePrefix = prefix.ifEmpty { "@" }
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(effectivePrefix))
                    // Always show the full filtered set on autopopup (do not wait for Ctrl+Space).
                    CremaDirectives.completionsForPrefix(effectivePrefix).forEach { label ->
                        KonaLookup.add(
                            matched,
                            LookupElementBuilder.create(label)
                                .withPresentableText(label)
                                .withTypeText("Crema", true)
                                .withIcon(KonaIcons.KONA_13)
                                .withInsertHandler { ctx, _ ->
                                    val doc = ctx.document
                                    val start = ctx.startOffset
                                    if (start > 0 &&
                                        doc.charsSequence[start - 1] == '@' &&
                                        label.startsWith("@")
                                    ) {
                                        doc.replaceString(start - 1, ctx.tailOffset, label)
                                        ctx.editor.caretModel.moveToOffset(start - 1 + label.length)
                                    }
                                },
                        )
                    }
                }
            },
        )
    }

    private fun isCremaFile(file: PsiFile): Boolean =
        file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
}

/** Pure helpers for Crema `@` directive completion. */
object CremaCompletion {
    fun prefixBefore(text: CharSequence, offset: Int): String {
        var i = offset - 1
        while (i >= 0 && CremaDirectives.isIdentPart(text[i])) i--
        if (i >= 0 && text[i] == '@') {
            // `@@` is an escaped literal @, not a directive.
            if (i > 0 && text[i - 1] == '@') return ""
            return text.subSequence(i, offset).toString()
        }
        return ""
    }

    /** True when the caret sits immediately after a lone `@` that starts a directive. */
    fun justTypedAt(text: CharSequence, offset: Int): Boolean {
        if (offset < 1 || text[offset - 1] != '@') return false
        if (offset >= 2 && text[offset - 2] == '@') return false
        return true
    }

    /** Whether autopopup should offer directive suggestions at [offset]. */
    fun shouldAutopopup(text: CharSequence, offset: Int): Boolean =
        prefixBefore(text, offset).isNotEmpty() || justTypedAt(text, offset)
}
