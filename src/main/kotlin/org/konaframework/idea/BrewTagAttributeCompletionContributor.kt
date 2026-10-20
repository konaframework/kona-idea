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
 * Completes attributes on `<brew:component …>` from the component class
 * (public properties + `mount(...)` parameters), plus common Brew flags.
 */
class BrewTagAttributeCompletionContributor : CompletionContributor(), DumbAware {
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
                    val root = project.guessProjectDir()?.toNioPath() ?: return
                    if (!KonaLayout.isKonaApp(root)) return
                    val text = snap.text
                    val offset = snap.offset
                    val ctx = BrewTagPatterns.attributeNameContextAt(text, offset) ?: return
                    val info = BrewComponentIndex.findComponent(root, ctx.componentName) ?: return
                    val names = info.acceptedAttributeNames()
                    if (names.isEmpty()) return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(ctx.prefix))
                    names
                        .filter { it.startsWith(ctx.prefix, ignoreCase = true) || ctx.prefix.isEmpty() }
                        .distinct()
                        .sorted()
                        .forEach { name ->
                            KonaLookup.add(
                                matched,
                                LookupElementBuilder.create(name)
                                    .withPresentableText(name)
                                    .withTypeText("${info.className} · Brew attr", true)
                                    .withIcon(KonaIcons.KONA_13),
                            )
                        }
                }
            },
        )
    }

}
