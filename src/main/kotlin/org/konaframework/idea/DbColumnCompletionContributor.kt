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
 * Completes DB column names from migrations and/or a live JDBC connection.
 *
 * When the call has a model receiver (`User.where("…")`), suggestions are limited
 * to that model's table.
 */
class DbColumnCompletionContributor : CompletionContributor(), DumbAware {
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
                    val ctx = DbColumnPatterns.columnContextAt(snap.text, snap.offset) ?: return
                    val mode = KonaIdeaSettings.getInstance(project).columnSourceMode
                    val snapshot = DbColumnIndex.collect(root, mode = mode, probeJdbc = true)
                    val table = resolveTable(root, ctx.receiver)
                    val columns =
                        if (table != null) {
                            snapshot.columnsForTable(table)
                        } else {
                            snapshot.allColumns
                        }
                    if (columns.isEmpty()) return
                    val sourceHint =
                        when {
                            snapshot.fromJdbc && snapshot.fromMigrations -> "migration+JDBC"
                            snapshot.fromJdbc -> "JDBC"
                            else -> "migration"
                        }
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(ctx.prefix))
                    columns
                        .filter { it.startsWith(ctx.prefix, ignoreCase = true) || ctx.prefix.isEmpty() }
                        .sorted()
                        .forEach { col ->
                            val typeText =
                                buildString {
                                    append(sourceHint)
                                    if (table != null) append(" · ").append(table)
                                }
                            matched.addElement(
                                LookupElementBuilder.create(col)
                                    .withPresentableText(col)
                                    .withTypeText(typeText, true)
                                    .withIcon(KonaIcons.KONA_13),
                            )
                        }
                }
            },
        )
    }

    companion object {
        fun resolveTable(projectRoot: java.nio.file.Path, receiver: String?): String? {
            if (receiver.isNullOrBlank()) return null
            // PascalCase → model class
            if (receiver[0].isUpperCase()) {
                return ModelIndex.tableForClass(projectRoot, receiver)
            }
            // camelCase variable → conventional model class
            val modelClass = receiver.replaceFirstChar { it.uppercaseChar() }
            return ModelIndex.tableForClass(projectRoot, modelClass)
        }
    }
}
