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
 * Completes Cadence model attributes / DB columns on instance property access:
 * `project.description`, `user.email` (from migrations and/or JDBC per settings).
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
                    val text = parameters.originalFile.viewProvider.contents
                    val offset = parameters.offset.coerceIn(0, text.length)
                    val prop = ModelPropertyPatterns.propertyAt(text, offset) ?: return
                    val modelClass = prop.modelClass ?: return
                    val table = ModelIndex.tableForClass(root, modelClass) ?: return
                    val mode = KonaIdeaSettings.getInstance(project).columnSourceMode
                    val snapshot = DbColumnIndex.collect(root, mode = mode, probeJdbc = true)
                    val columns = snapshot.columnsForTable(table)
                    if (columns.isEmpty()) return
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prop.prefix))
                    columns
                        .filter { it.startsWith(prop.prefix, ignoreCase = true) || prop.prefix.isEmpty() }
                        .sorted()
                        .forEach { col ->
                            matched.addElement(
                                LookupElementBuilder.create(col)
                                    .withPresentableText(col)
                                    .withTypeText("$modelClass · $table", true)
                                    .withIcon(KonaIcons.KONA_13),
                            )
                        }
                }
            },
        )
    }
}
