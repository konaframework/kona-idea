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
 * Completes Crema echo helpers (`url`, `route`, `asset`, …) and conventional
 * variables (`errors`, `loop.index`, …) inside `{{ }}` / `{!! !!}`.
 *
 * Autopopup is already scheduled by [CremaTypedHandler] whenever the caret is
 * inside an echo ([BrewWirePatterns.echoContextAt]). Without this contributor
 * the popup had nothing to show unless a Brew component owned the view.
 */
class CremaEchoCompletionContributor : CompletionContributor(), DumbAware {
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
                    val echo = BrewWirePatterns.echoContextAt(snap.text, snap.offset) ?: return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(echo.prefix))
                    CremaEchoCatalog.completions(echo.prefix).forEach { item ->
                        var builder =
                            LookupElementBuilder.create(item.name)
                                .withPresentableText(item.name)
                                .withTypeText(item.detail, true)
                                .withIcon(KonaIcons.KONA_13)
                        if (item.helper) {
                            builder =
                                builder
                                    .withTailText("()", true)
                                    .withInsertHandler(helperInsertHandler(item.insertText))
                        }
                        KonaLookup.add(matched, builder)
                    }
                }
            },
        )
    }

    companion object {
        fun helperInsertHandler(insertText: String): InsertHandler<LookupElement> =
            InsertHandler { ctx, _ ->
                ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, insertText)
                ctx.editor.caretModel.moveToOffset(
                    ctx.startOffset + CremaEchoCompletion.caretOffsetInInsert(insertText),
                )
            }
    }
}
