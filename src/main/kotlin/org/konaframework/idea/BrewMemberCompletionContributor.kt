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
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import java.nio.file.Path

/**
 * Completes Brew component actions / properties / computed members inside
 * `wire:*="…"` values and `{{ }}` echoes, using the owning component class
 * paired with the current Crema view.
 */
class BrewMemberCompletionContributor : CompletionContributor(), DumbAware {
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
                    val cremaFile = CremaPsi.completionFile(parameters) ?: return
                    val owner = resolveOwner(root, cremaFile) ?: return
                    val text = snap.text
                    val offset = snap.offset

                    val value = BrewWirePatterns.valueContextAt(text, offset)
                    if (value != null) {
                        val names =
                            when {
                                BrewComponentIndex.isActionAttribute(value.attributeName) ->
                                    owner.actions()
                                BrewComponentIndex.isPropertyAttribute(value.attributeName) ->
                                    owner.propertiesAndComputed()
                                else -> return
                            }
                        addMembers(result, value.prefix, names, owner, kindLabel(value.attributeName))
                        return
                    }

                    val echo = BrewWirePatterns.echoContextAt(text, offset) ?: return
                    addMembers(
                        result,
                        echo.prefix,
                        owner.propertiesAndComputed(),
                        owner,
                        "Brew property",
                    )
                }
            },
        )
    }

    private fun kindLabel(attributeName: String): String =
        if (BrewComponentIndex.isActionAttribute(attributeName)) "Brew action" else "Brew property"

    private fun addMembers(
        result: CompletionResultSet,
        prefix: String,
        names: List<String>,
        owner: BrewComponentIndex.ComponentInfo,
        typeText: String,
    ) {
        if (names.isEmpty()) return
        val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
        names
            .filter { it.startsWith(prefix, ignoreCase = true) || prefix.isEmpty() }
            .distinct()
            .sorted()
            .forEach { name ->
                KonaLookup.add(
                    matched::addElement,
                    LookupElementBuilder.create(name)
                        .withPresentableText(name)
                        .withTypeText("${owner.className} · $typeText", true)
                        .withIcon(KonaIcons.KONA_13),
                )
            }
    }

    private fun resolveOwner(root: Path, file: PsiFile): BrewComponentIndex.ComponentInfo? {
        val host = CremaPsi.hostFile(file)
        val hostPath = CremaPsi.hostPath(file)
        return BrewInjectionOwners.ownerFor(root, hostPath, host.text)
    }
}
