package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir

/**
 * Completes Cadence model attributes on instance access and attribute-key APIs:
 * - `user.` / `user.em` / `user?.em` — columns, fillable, accessors, relations
 * - `user.getAttribute("…")` / `get("` / `user["…"]` — string keys
 * - `user.getAttribute(<caret>)` — same keys, inserts quotes
 *
 * Uses [fillCompletionVariants] (not `extend` patterns) so Kotlin member
 * completion still receives our variants, and strips IDEA's dummy identifier
 * from the prefix matcher.
 */
class ModelPropertyCompletionContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val root = appRoot(parameters) ?: return
        val matcherPrefix = CompletionText.effectivePrefix("", result.prefixMatcher.prefix)
        val mode = KonaIdeaSettings.getInstance(parameters.position.project).columnSourceMode
        val suggestions = suggestionsFor(parameters, root, mode, matcherPrefix)
        if (suggestions.isEmpty()) return
        offer(suggestions, result)
    }

    companion object {
        fun resolveModelClass(
            text: CharSequence,
            receiver: String?,
            offset: Int = text.length,
        ): String? {
            if (receiver.isNullOrBlank() || receiver == "this" || receiver == "super") {
                return ModelIndex.enclosingCadenceClass(text, offset)
            }
            if (receiver[0].isUpperCase()) {
                return ModelPropertyPatterns.simpleModelName(receiver) ?: receiver
            }
            return ModelPropertyPatterns.resolveModelClass(text, receiver, offset)
        }

        fun appRoot(parameters: CompletionParameters): java.nio.file.Path? {
            val vf = parameters.originalFile.virtualFile
            var current = vf
            while (current != null) {
                val node = current
                val path =
                    runCatching { node.toNioPath() }.getOrNull()
                        ?: runCatching { java.nio.file.Path.of(node.path) }.getOrNull()
                KonaLayout.completionRootNear(path)?.let { return it }
                current = node.parent
            }
            val guessed =
                runCatching { parameters.position.project.guessProjectDir()?.toNioPath() }.getOrNull()
            return KonaLayout.completionRootNear(guessed)
        }

        fun suggestionsFor(
            parameters: CompletionParameters,
            root: java.nio.file.Path,
            mode: ColumnSourceMode,
            matcherPrefix: String,
        ): List<CadenceCompletion.Suggestion> {
            val copyText = parameters.position.containingFile.viewProvider.contents
            CadenceCompletion.modelSuggestions(
                root,
                copyText,
                parameters.offset,
                mode = mode,
                probeJdbc = true,
                matcherPrefix = matcherPrefix,
            ).takeIf { it.isNotEmpty() }?.let { return it }
            val original = parameters.originalFile.viewProvider.contents
            val origOffset = CompletionText.offsetInOriginal(parameters.offset, original.length)
            return CadenceCompletion.modelSuggestions(
                root,
                original,
                origOffset,
                mode = mode,
                probeJdbc = true,
                matcherPrefix = matcherPrefix,
            )
        }

        fun offer(
            suggestions: List<CadenceCompletion.Suggestion>,
            result: CompletionResultSet,
        ) {
            if (suggestions.isEmpty()) return
            val matched = result.withPrefixMatcher(PlainPrefixMatcher(""))
            suggestions.forEach { attr ->
                var builder =
                    LookupElementBuilder.create(attr.name)
                        .withPresentableText(attr.name)
                        .withTypeText(attr.typeText, true)
                        .withIcon(KonaIcons.KONA_13)
                if (!attr.quoted) {
                    builder = builder.withInsertHandler(quoteInsertHandler(attr.name))
                }
                KonaLookup.add(matched::addElement, builder)
            }
        }

        fun offer(
            root: java.nio.file.Path,
            modelClass: String,
            mode: ColumnSourceMode,
            prefix: String,
            quoted: Boolean,
            result: CompletionResultSet,
        ) {
            val attrs =
                ModelAttributeIndex.attributesFor(
                    root,
                    modelClass,
                    mode = mode,
                    probeJdbc = true,
                )
            val suggestions =
                attrs
                    .filter {
                        val p = CompletionText.effectivePrefix(prefix)
                        p.isEmpty() || it.name.startsWith(p, ignoreCase = true)
                    }.sortedBy { it.name }
                    .map {
                        CadenceCompletion.Suggestion(
                            name = it.name,
                            typeText = it.typeText,
                            kind = if (quoted) CadenceCompletion.Kind.KEY else CadenceCompletion.Kind.PROPERTY,
                            quoted = quoted,
                            modelClass = modelClass,
                        )
                    }
            offer(suggestions, result)
        }

        private fun quoteInsertHandler(name: String): InsertHandler<LookupElement> =
            InsertHandler { ctx, _ ->
                ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, "\"$name\"")
            }
    }
}
