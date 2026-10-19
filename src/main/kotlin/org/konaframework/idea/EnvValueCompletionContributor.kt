package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes known option values for env keys in `.env*` and in the default
 * argument of `Env.getOrDefault("KEY", "…")`.
 */
class EnvValueCompletionContributor : CompletionContributor(), DumbAware {
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
                    val file = parameters.originalFile
                    val text = file.viewProvider.contents
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val root = file.project.guessProjectDir()?.toNioPath()
                    val key: String
                    val prefix: String
                    if (EnvValueOptions.isDotEnvFamilyName(file.name)) {
                        val edit = EnvValueOptions.dotenvValuePrefixAt(text, offset) ?: return
                        key = edit.key
                        prefix = edit.prefix
                    } else {
                        val edit = EnvValueOptions.getOrDefaultValuePrefixAt(text, offset) ?: return
                        key = edit.key
                        prefix = edit.prefix
                    }
                    val options = EnvValueOptions.optionsFor(key, root)
                    if (options.isEmpty()) return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
                    EnvValueOptions.matching(options, prefix).forEach { option ->
                        var element =
                            LookupElementBuilder.create(option.value)
                                .withPresentableText(option.value)
                                .withTypeText(option.typeText, true)
                                .withIcon(KonaIcons.KONA_13)
                        if (option.tailText != null) {
                            element = element.withTailText(" (${option.tailText})", true)
                        }
                        matched.addElement(element)
                    }
                }
            },
        )
    }
}
