package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir

/**
 * Completes grind command names inside `Grind.call(` / `Grind.callSilently(`.
 */
class GrindCallCompletionContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val root = ModelPropertyCompletionContributor.appRoot(parameters)
            ?: parameters.position.project.guessProjectDir()?.toNioPath()?.takeIf { KonaLayout.isKonaApp(it) }
            ?: return
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
        val ctx =
            GrindCallPatterns.callContextAt(snap.text, snap.offset)
                ?: GrindCallPatterns.callContextAt(original.text, original.offset)
                ?: return
        val prefix = ctx.prefix.ifEmpty { matcherPrefix }
        offer(root, prefix, ctx.quoted, result)
    }

    companion object {
        fun offer(
            root: java.nio.file.Path,
            prefix: String,
            quoted: Boolean,
            result: CompletionResultSet,
        ) {
            val commands = GrindCommandIndex.commands(root)
            if (commands.isEmpty()) return
            val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
            commands
                .filter { it.name.startsWith(prefix, ignoreCase = true) || prefix.isEmpty() }
                .sortedBy { it.name }
                .forEach { cmd ->
                    val typeText = if (cmd.custom) "grind · custom" else "grind"
                    var builder =
                        LookupElementBuilder.create(cmd.name)
                            .withPresentableText(cmd.name)
                            .withTypeText(typeText, true)
                            .withTailText(if (cmd.summary.isNotEmpty()) "  ${cmd.summary}" else null, true)
                            .withIcon(KonaIcons.KONA_13)
                    if (!quoted) {
                        builder = builder.withInsertHandler(quoteInsertHandler(cmd.name))
                    }
                    matched.addElement(PrioritizedLookupElement.withPriority(builder, 1000.0))
                }
        }

        private fun quoteInsertHandler(name: String): InsertHandler<LookupElement> =
            InsertHandler { ctx, _ ->
                ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, "\"$name\"")
            }
    }
}
