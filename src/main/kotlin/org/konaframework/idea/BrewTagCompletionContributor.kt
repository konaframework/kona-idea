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
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes Brew `<brew:component>` tags from [BrewComponentIndex]
 * (`app.brew` classes / `@BrewComponent`, convention views under `views/brew/`).
 */
class BrewTagCompletionContributor : CompletionContributor(), DumbAware {
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
                    val project = parameters.position.project
                    val root = project.guessProjectDir()?.toNioPath() ?: return
                    if (!KonaLayout.isKonaApp(root)) return
                    val text = parameters.originalFile.viewProvider.contents
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val tag = BrewTagPatterns.tagPrefixAt(text, offset) ?: return
                    val components = BrewComponentIndex.collect(root)
                    if (components.isEmpty()) return
                    // After `<brew:`, match the component name; otherwise match
                    // whatever was typed after `<` (`bre` / `brew` / `brew:…`).
                    val matcherPrefix =
                        if (tag.afterColon) {
                            tag.prefix
                        } else {
                            typedAfterLt(text, offset).orEmpty()
                        }
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(matcherPrefix))
                    components
                        .filter {
                            it.componentName.startsWith(tag.prefix, ignoreCase = true) ||
                                tag.prefix.isEmpty()
                        }
                        .forEach { info ->
                            val lookup =
                                if (tag.afterColon) {
                                    info.componentName
                                } else {
                                    BrewTagPatterns.htmlTag(info.componentName)
                                }
                            KonaLookup.add(
                                matched,
                                LookupElementBuilder.create(lookup)
                                    .withPresentableText(
                                        "<${BrewTagPatterns.htmlTag(info.componentName)} />",
                                    )
                                    .withTypeText(info.viewName, true)
                                    .withIcon(KonaIcons.KONA_13)
                                    .withInsertHandler(selfClosingInsertHandler(tag.afterColon)),
                            )
                        }
                }
            },
        )
    }

    private fun typedAfterLt(text: CharSequence, offset: Int): String? {
        var i = offset - 1
        while (i >= 0 && BrewTagPatterns.isTagPart(text[i])) i--
        if (i < 0 || text[i] != '<') return null
        return text.subSequence(i + 1, offset).toString()
    }

    private fun selfClosingInsertHandler(afterColon: Boolean): InsertHandler<LookupElement> =
        InsertHandler { ctx, item ->
            val doc = ctx.document
            val text = doc.charsSequence
            val end = ctx.tailOffset
            val start =
                if (afterColon) {
                    ctx.startOffset
                } else {
                    // Replace everything after `<` so `bre` / `brew` become `brew:name`.
                    val lt = text.lastIndexOf('<', (end - 1).coerceAtLeast(0))
                    if (lt >= 0) lt + 1 else ctx.startOffset
                }
            val insertion = "${item.lookupString} />"
            doc.replaceString(start, end, insertion)
            ctx.editor.caretModel.moveToOffset(start + insertion.length)
        }

    private fun isCremaFile(file: com.intellij.psi.PsiFile): Boolean =
        file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
}
