package org.konaframework.idea

import java.nio.file.Path

/**
 * Testable Cadence completion pipeline: dummy-identifier rewind, model
 * instance / attribute-key contexts, and `where("` column strings.
 *
 * IntelliJ contributors only wrap [Suggestion] values for the lookup list.
 * Keep this object free of PSI so regression tests can exercise the same
 * path `user.`, `getAttribute()`, `get()`, and `User.where("` use in the IDE.
 */
object CadenceCompletion {
    enum class Kind {
        PROPERTY,
        KEY,
        COLUMN,
    }

    data class Suggestion(
        val name: String,
        val typeText: String,
        val kind: Kind,
        val quoted: Boolean,
        val modelClass: String? = null,
        val table: String? = null,
    )

    fun collect(
        root: Path,
        text: CharSequence,
        offset: Int,
        mode: ColumnSourceMode = ColumnSourceMode.MIGRATIONS_ONLY,
        probeJdbc: Boolean = false,
        matcherPrefix: String = "",
    ): List<Suggestion> {
        val matcher = CompletionText.effectivePrefix("", matcherPrefix)
        val fromModel = modelSuggestions(root, text, offset, mode, probeJdbc, matcher)
        if (fromModel.isNotEmpty()) return fromModel
        return columnSuggestions(root, text, offset, mode, probeJdbc, matcher)
    }

    fun modelSuggestions(
        root: Path,
        text: CharSequence,
        offset: Int,
        mode: ColumnSourceMode = ColumnSourceMode.MIGRATIONS_ONLY,
        probeJdbc: Boolean = false,
        matcherPrefix: String = "",
    ): List<Suggestion> {
        val snap = CompletionText.fromContents(text, offset)
        val request = ModelCompletion.requestAt(snap.text, snap.offset) ?: return emptyList()
        val prefix = CompletionText.effectivePrefix(request.prefix, matcherPrefix)
        val attrs =
            ModelAttributeIndex.attributesFor(
                root,
                request.modelClass,
                mode = mode,
                probeJdbc = probeJdbc,
            )
        val kind = if (request.isKey) Kind.KEY else Kind.PROPERTY
        return attrs
            .filter { prefix.isEmpty() || it.name.startsWith(prefix, ignoreCase = true) }
            .sortedBy { it.name }
            .map {
                Suggestion(
                    name = it.name,
                    typeText = it.typeText,
                    kind = kind,
                    quoted = request.quoted,
                    modelClass = request.modelClass,
                )
            }
    }

    fun columnSuggestions(
        root: Path,
        text: CharSequence,
        offset: Int,
        mode: ColumnSourceMode = ColumnSourceMode.MIGRATIONS_ONLY,
        probeJdbc: Boolean = false,
        matcherPrefix: String = "",
    ): List<Suggestion> {
        val snap = CompletionText.fromContents(text, offset)
        val ctx = DbColumnPatterns.columnContextAt(snap.text, snap.offset) ?: return emptyList()
        val prefix = CompletionText.effectivePrefix(ctx.prefix, matcherPrefix)
        val snapshot = DbColumnIndex.collect(root, mode = mode, probeJdbc = probeJdbc)
        val table = resolveTable(root, ctx.receiver, snap.text)
        val columns =
            if (table != null) {
                snapshot.columnsForTable(table)
            } else {
                snapshot.allColumns
            }
        if (columns.isEmpty()) return emptyList()
        val sourceHint = sourceLabel(snapshot.fromJdbc, snapshot.fromMigrations)
        return columns
            .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
            .sorted()
            .map { col ->
                val typeText =
                    buildString {
                        append(sourceHint)
                        if (table != null) append(" · ").append(table)
                    }
                Suggestion(
                    name = col,
                    typeText = typeText,
                    kind = Kind.COLUMN,
                    quoted = true,
                    table = table,
                )
            }
    }

    fun resolveTable(
        projectRoot: Path,
        receiver: String?,
        source: CharSequence? = null,
    ): String? {
        if (receiver.isNullOrBlank()) return null
        val modelClass =
            if (receiver[0].isUpperCase()) {
                receiver
            } else {
                ModelPropertyPatterns.resolveModelClass(source ?: "", receiver)!!
            }
        return ModelIndex.tableForClass(projectRoot, modelClass)
    }

    fun sourceLabel(fromJdbc: Boolean, fromMigrations: Boolean): String =
        when {
            fromJdbc && fromMigrations -> "migration+JDBC"
            fromJdbc -> "JDBC"
            else -> "migration"
        }
}
