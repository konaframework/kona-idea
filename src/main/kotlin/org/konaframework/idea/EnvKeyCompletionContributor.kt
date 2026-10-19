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
 * Completes env key names inside `Env.get` / `getOrDefault` / `require`,
 * and in `.env` completes keys already referenced from source.
 */
class EnvKeyCompletionContributor : CompletionContributor(), DumbAware {
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
                    val root = file.project.guessProjectDir()?.toNioPath() ?: return
                    val dotenv = EnvKeys.isDotEnvFileName(file.name)
                    val prefix: String
                    val typeText: String
                    val keys: List<String>
                    if (dotenv) {
                        val edit = EnvKeys.dotenvKeyPrefixAt(text, offset) ?: return
                        prefix = edit.prefix
                        if (edit.interpolating) {
                            keys = EnvKeys.completionKeys(root)
                            typeText = "env"
                        } else {
                            keys = EnvKeys.referencedKeys(root)
                            typeText = "referenced"
                        }
                    } else {
                        prefix = EnvKeys.lookupPrefixAt(text, offset) ?: return
                        keys = EnvKeys.completionKeys(root)
                        typeText = "env"
                    }
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
                    EnvKeys.matching(keys, prefix).forEach { name ->
                        matched.addElement(
                            LookupElementBuilder.create(name)
                                .withPresentableText(name)
                                .withTypeText(typeText, true)
                                .withIcon(KonaIcons.KONA_13),
                        )
                    }
                }
            },
        )
    }
}
