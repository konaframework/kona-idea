package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes Crema `<x-component>` tags and `<x-slot` / `<x-slot:name>` hints
 * from views under `resources/views/components`.
 */
class XComponentCompletionContributor : CompletionContributor(), DumbAware {
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
                    val project = parameters.position.project
                    val root = project.guessProjectDir()?.toNioPath()
                    val text = snap.text
                    val offset = snap.offset

                    if (XComponentPatterns.wantsSlotChild(text, offset)) {
                        result.addKona(
                            "",
                            listOf(
                                LookupElementBuilder.create("x-slot name=\"\"")
                                    .withPresentableText("<x-slot name=\"…\">")
                                    .withTypeText("Crema slot", true)
                                    .bold()
                                    .withIcon(KonaIcons.KONA_13)
                                    .withInsertHandler(slotNameInsertHandler()),
                                LookupElementBuilder.create("x-slot:")
                                    .withPresentableText("<x-slot:…>")
                                    .withTypeText("Crema slot", true)
                                    .bold()
                                    .withIcon(KonaIcons.KONA_13),
                            ),
                        )
                        return
                    }

                    val tag = XComponentPatterns.tagPrefixAt(text, offset) ?: return
                    if (tag.isSlot) {
                        val components = root?.let { XComponentIndex.list(it) }.orEmpty()
                        val slots =
                            components.flatMap { it.slotNames }.toSet().ifEmpty {
                                setOf("title", "footer", "header", "body")
                            }
                        val slotElements =
                            slots
                                .filter { it.startsWith(tag.prefix, ignoreCase = true) || tag.prefix.isEmpty() }
                                .sorted()
                                .map { name ->
                                    LookupElementBuilder.create(name)
                                        .withPresentableText(name)
                                        .withTypeText("x-slot", true)
                                        .bold()
                                        .withIcon(KonaIcons.KONA_13)
                                }
                        result.addKona(tag.prefix, slotElements)
                        return
                    }

                    val components = root?.let { XComponentIndex.list(it) }.orEmpty()
                    if (components.isEmpty() && tag.prefix.isEmpty()) {
                        // Still offer the x-slot keyword when no components exist.
                        offerBareXSlot(result, tag.prefix)
                        return
                    }
                    val tagElements =
                        components
                            .filter {
                                it.tagName.startsWith(tag.prefix, ignoreCase = true) ||
                                    tag.prefix.isEmpty()
                            }
                            .map { info ->
                                LookupElementBuilder.create(info.tagName)
                                    .withPresentableText("<x-${info.tagName}>")
                                    .withTypeText(info.viewName, true)
                                    .bold()
                                    .withIcon(KonaIcons.KONA_13)
                                    .withInsertHandler(componentInsertHandler(info.tagName))
                            }
                    result.addKona(tag.prefix, tagElements)
                    if ("slot".startsWith(tag.prefix, ignoreCase = true) || tag.prefix.isEmpty()) {
                        offerBareXSlot(result, tag.prefix)
                    }
                }
            },
        )
    }

    private fun offerBareXSlot(result: CompletionResultSet, prefix: String) {
        if (!"slot".startsWith(prefix, ignoreCase = true) && prefix.isNotEmpty()) return
        result.offerKona(
            prefix,
            LookupElementBuilder.create("slot")
                .withPresentableText("<x-slot>")
                .withTypeText("Crema named slot", true)
                .bold()
                .withIcon(KonaIcons.KONA_13)
                .withInsertHandler { ctx, _ ->
                    val doc = ctx.document
                    doc.replaceString(ctx.startOffset, ctx.tailOffset, "slot name=\"\">")
                    ctx.editor.caretModel.moveToOffset(ctx.startOffset + "slot name=\"".length)
                },
        )
    }

    private fun componentInsertHandler(tagName: String): InsertHandler<LookupElement> =
        InsertHandler { ctx, _ ->
            val doc = ctx.document
            val start = ctx.startOffset
            // Replace the typed prefix with tagName>…</x-tagName> or self-closing.
            val insertion = "$tagName></x-$tagName>"
            doc.replaceString(start, ctx.tailOffset, insertion)
            // Caret between open and close: <x-tagName>|</x-tagName>
            ctx.editor.caretModel.moveToOffset(start + tagName.length + 1)
        }

    private fun slotNameInsertHandler(): InsertHandler<LookupElement> =
        InsertHandler { ctx, _ ->
            val doc = ctx.document
            val start = ctx.startOffset
            doc.replaceString(start, ctx.tailOffset, "x-slot name=\"\"></x-slot>")
            ctx.editor.caretModel.moveToOffset(start + "x-slot name=\"".length)
        }

}
