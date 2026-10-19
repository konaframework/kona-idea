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
 * Completes Cadence model attributes on instance access and attribute-key APIs:
 * - `user.` / `user.em` — columns, accessors, relations
 * - `user.getAttribute("…")` / `setAttribute` / `attribute` / `hasAttribute` /
 *   `getRawAttribute` / `setRawAttribute` — string keys
 *
 * Only fires when the receiver resolves to a discovered Cadence model
 * ([ModelIndex.find]), so unrelated locals are not spammed.
 */
class ModelPropertyCompletionContributor : CompletionContributor(), DumbAware {
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
                    if (!KonaLayout.isKonaApp(root)) return
                    val snap = CompletionText.of(parameters)
                    val mode = KonaIdeaSettings.getInstance(project).columnSourceMode

                    AttributeKeyPatterns.keyContextAt(snap.text, snap.offset)?.let { keyCtx ->
                        val modelClass = resolveModelClass(snap.text, keyCtx.receiver) ?: return
                        if (ModelIndex.find(root, modelClass) == null) return
                        offer(root, modelClass, mode, keyCtx.prefix, result)
                        return
                    }

                    val prop = ModelPropertyPatterns.propertyAt(snap.text, snap.offset) ?: return
                    val modelClass = prop.modelClass ?: return
                    if (ModelIndex.find(root, modelClass) == null) return
                    offer(root, modelClass, mode, prop.prefix, result)
                }
            },
        )
    }

    companion object {
        fun resolveModelClass(text: CharSequence, receiver: String?): String? {
            if (receiver.isNullOrBlank()) return null
            return if (receiver[0].isUpperCase()) {
                receiver
            } else {
                ModelPropertyPatterns.resolveModelClass(text, receiver)
            }
        }

        fun offer(
            root: java.nio.file.Path,
            modelClass: String,
            mode: ColumnSourceMode,
            prefix: String,
            result: CompletionResultSet,
        ) {
            val attrs =
                ModelAttributeIndex.attributesFor(
                    root,
                    modelClass,
                    mode = mode,
                    probeJdbc = true,
                )
            if (attrs.isEmpty()) return
            val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
            attrs
                .filter { it.name.startsWith(prefix, ignoreCase = true) || prefix.isEmpty() }
                .sortedBy { it.name }
                .forEach { attr ->
                    matched.addElement(
                        LookupElementBuilder.create(attr.name)
                            .withPresentableText(attr.name)
                            .withTypeText(attr.typeText, true)
                            .withIcon(KonaIcons.KONA_13),
                    )
                }
        }
    }
}
