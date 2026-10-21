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
import com.intellij.util.ProcessingContext

/**
 * Completes DB column names from migrations and/or a live JDBC connection.
 *
 * When the call has a model receiver (`User.where("…")`), suggestions are limited
 * to that model's table.
 */
class DbColumnCompletionContributor : CompletionContributor(), DumbAware {
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
                    val root = ModelPropertyCompletionContributor.appRoot(parameters) ?: return
                    val matcherPrefix =
                        CompletionText.effectivePrefix("", result.prefixMatcher.prefix)
                    val mode = KonaIdeaSettings.getInstance(parameters.position.project).columnSourceMode
                    val copyText = parameters.position.containingFile.viewProvider.contents
                    var suggestions =
                        CadenceCompletion.columnSuggestions(
                            root,
                            copyText,
                            parameters.offset,
                            mode = mode,
                            probeJdbc = true,
                            matcherPrefix = matcherPrefix,
                        )
                    if (suggestions.isEmpty()) {
                        val original = parameters.originalFile.viewProvider.contents
                        val origOffset =
                            CompletionText.offsetInOriginal(parameters.offset, original.length)
                        suggestions =
                            CadenceCompletion.columnSuggestions(
                                root,
                                original,
                                origOffset,
                                mode = mode,
                                probeJdbc = true,
                                matcherPrefix = matcherPrefix,
                            )
                    }
                    if (suggestions.isEmpty()) return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(""))
                    suggestions.forEach { col ->
                        KonaLookup.add(
                            matched::addElement,
                            LookupElementBuilder.create(col.name)
                                .withPresentableText(col.name)
                                .withTypeText(col.typeText, true)
                                .withIcon(KonaIcons.KONA_13),
                        )
                    }
                }
            },
        )
    }

    companion object {
        fun resolveTable(
            projectRoot: java.nio.file.Path,
            receiver: String?,
            source: CharSequence? = null,
        ): String? = CadenceCompletion.resolveTable(projectRoot, receiver, source)
    }
}
