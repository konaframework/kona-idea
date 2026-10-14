package dev.kona.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class CremaCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile().withLanguage(CremaLanguage)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val text = parameters.originalFile.text
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val prefix = CremaCompletion.prefixBefore(text, offset)
                    CremaDirectives.completionsForPrefix(prefix).forEach { label ->
                        result.addElement(LookupElementBuilder.create(label).withPresentableText(label))
                    }
                }
            },
        )
    }
}

/** Pure helpers for Crema `@` directive completion. */
object CremaCompletion {
    fun prefixBefore(text: CharSequence, offset: Int): String {
        var i = offset - 1
        while (i >= 0 && CremaDirectives.isIdentPart(text[i])) i--
        if (i >= 0 && text[i] == '@') {
            return text.subSequence(i, offset).toString()
        }
        return ""
    }
}
