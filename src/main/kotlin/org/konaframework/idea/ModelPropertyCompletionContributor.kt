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
        val snap =
            CompletionText.fromContents(
                parameters.position.containingFile.viewProvider.contents,
                parameters.offset,
            )
        val matcherPrefix = CompletionText.stripDummy(result.prefixMatcher.prefix)
        val original =
            CompletionText.fromContents(
                parameters.originalFile.viewProvider.contents,
                parameters.offset,
            )
        val request =
            ModelCompletion.requestAt(snap.text, snap.offset)
                ?: ModelCompletion.requestAt(original.text, original.offset)
                ?: return
        val prefix = request.prefix.ifEmpty { matcherPrefix }
        val mode = KonaIdeaSettings.getInstance(parameters.position.project).columnSourceMode
        offer(root, request.modelClass, mode, prefix, request.quoted, result)
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

        fun appRoot(parameters: CompletionParameters): java.nio.file.Path? {
            val fromFile =
                parameters.originalFile.virtualFile?.parent?.let { vf ->
                    runCatching { vf.toNioPath() }.getOrNull()
                }
            KonaLayout.appRootNear(fromFile)?.let { return it }
            val guessed = parameters.position.project.guessProjectDir()?.toNioPath() ?: return null
            return guessed.takeIf { KonaLayout.isKonaApp(it) }
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
            if (attrs.isEmpty()) return
            val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
            attrs
                .filter { it.name.startsWith(prefix, ignoreCase = true) || prefix.isEmpty() }
                .sortedBy { it.name }
                .forEach { attr ->
                    var builder =
                        LookupElementBuilder.create(attr.name)
                            .withPresentableText(attr.name)
                            .withTypeText(attr.typeText, true)
                            .withIcon(KonaIcons.KONA_13)
                    if (!quoted) {
                        builder = builder.withInsertHandler(quoteInsertHandler(attr.name))
                    }
                    KonaLookup.add(matched, builder)
                }
        }

        private fun quoteInsertHandler(name: String): InsertHandler<LookupElement> =
            InsertHandler { ctx, _ ->
                ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, "\"$name\"")
            }
    }
}
