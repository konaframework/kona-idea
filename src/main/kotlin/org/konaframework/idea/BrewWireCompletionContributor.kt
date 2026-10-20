package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes Brew `wire:*` attributes inside Crema HTML tags.
 */
class BrewWireCompletionContributor : CompletionContributor(), DumbAware {
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
                    val snap = CremaPsi.snapshot(parameters) ?: return
                    val text = snap.text
                    val offset = snap.offset
                    val prefix = BrewWirePatterns.attributePrefixAt(text, offset) ?: return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
                    BrewWireAttributes.completionsForPrefix(prefix).forEach { label ->
                        matched.addElement(
                            LookupElementBuilder.create(label)
                                .withPresentableText(label)
                                .withTypeText("Brew wire", true)
                                .withIcon(KonaIcons.KONA_13)
                                .withInsertHandler(wireInsertHandler(label)),
                        )
                    }
                }
            },
        )
    }

    private fun wireInsertHandler(label: String): InsertHandler<LookupElement> =
        InsertHandler { ctx, _ ->
            val needsValue =
                label.startsWith("wire:model") ||
                    label.startsWith("wire:click") ||
                    label.startsWith("wire:submit") ||
                    label.startsWith("wire:target") ||
                    label.startsWith("wire:key") ||
                    label.startsWith("wire:confirm") ||
                    label.startsWith("wire:poll") ||
                    label.startsWith("wire:init") ||
                    label.startsWith("wire:show") ||
                    label.startsWith("wire:transition") ||
                    label.startsWith("wire:text") ||
                    label.startsWith("wire:ref") ||
                    label.contains(".class") ||
                    label.contains(".attr")
            val insertion = if (needsValue) "$label=\"\"" else label
            val start = ctx.startOffset
            ctx.document.replaceString(start, ctx.tailOffset, insertion)
            if (needsValue) {
                ctx.editor.caretModel.moveToOffset(start + label.length + 2)
            }
        }

}
