package dev.kona.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/** Completes DB column names from migrations and/or a live JDBC connection. */
class DbColumnCompletionContributor : CompletionContributor() {
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
                    val prefix = DbColumnPatterns.columnPrefixAt(text, offset) ?: return
                    val snapshot = DbColumnIndex.collect(root, probeJdbc = true)
                    if (snapshot.allColumns.isEmpty()) return
                    val sourceHint =
                        when {
                            snapshot.fromJdbc && snapshot.fromMigrations -> "migration+JDBC"
                            snapshot.fromJdbc -> "JDBC"
                            else -> "migration"
                        }
                    val matched = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
                    snapshot.allColumns
                        .filter { it.startsWith(prefix, ignoreCase = true) || prefix.isEmpty() }
                        .sorted()
                        .forEach { col ->
                            val tables =
                                snapshot.byTable.entries
                                    .filter { col in it.value }
                                    .map { it.key }
                                    .take(3)
                                    .joinToString(", ")
                            matched.addElement(
                                LookupElementBuilder.create(col)
                                    .withPresentableText(col)
                                    .withTypeText(
                                        buildString {
                                            append(sourceHint)
                                            if (tables.isNotEmpty()) append(" · ").append(tables)
                                        },
                                        true,
                                    )
                                    .withIcon(KonaIcons.KONA_13),
                            )
                        }
                }
            },
        )
    }
}
