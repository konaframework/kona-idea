package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes Firefly panel, resource, page, and widget names in Kotlin and Java.
 */
class FireflyCompletionContributor : CompletionContributor(), DumbAware {
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
                    val language = file.language.id.lowercase()
                    if (language != "kotlin" && language != "java") {
                        return
                    }
                    val items = FireflyCatalog.completions(file.text, parameters.offset)
                    for (item in items) {
                        result.addElement(
                            LookupElementBuilder.create(item.label).withTypeText(item.detail, true),
                        )
                    }
                }
            },
        )
    }
}
