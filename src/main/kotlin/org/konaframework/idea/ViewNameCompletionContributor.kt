package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/** Autocomplete dotted Crema view names in `view(…)`, `@extends`, `@include`, etc. */
class ViewNameCompletionContributor : CompletionContributor() {
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
                    val project = parameters.position.project
                    val root = project.guessProjectDir()?.toNioPath() ?: return
                    if (!KonaLayout.isKonaApp(root) &&
                        !java.nio.file.Files.isDirectory(ViewIndex.viewsRoot(root))
                    ) {
                        return
                    }
                    val text = parameters.originalFile.viewProvider.contents
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val prefix = ViewNamePatterns.completionsPrefix(text, offset) ?: return
                    val names = ViewIndex.listNames(root)
                    if (names.isEmpty()) return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
                    names.filter { it.startsWith(prefix) || prefix.isEmpty() }.forEach { name ->
                        KonaLookup.add(
                            matched,
                            LookupElementBuilder.create(name)
                                .withPresentableText(name)
                                .withTypeText("Crema view", true)
                                .withIcon(KonaIcons.KONA_13),
                        )
                    }
                }
            },
        )
    }
}
