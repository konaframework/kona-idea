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
        val resolved =
            attributesPreferringCadence(
                root,
                request.modelClass,
                request.receiver,
                request.isKey,
                mode,
                probeJdbc,
            )
        val kind = if (request.isKey) Kind.KEY else Kind.PROPERTY
        return resolved.attrs
            .filter { prefix.isEmpty() || it.name.startsWith(prefix, ignoreCase = true) }
            .sortedBy { it.name }
            .map {
                Suggestion(
                    name = it.name,
                    typeText = it.typeText,
                    kind = kind,
                    quoted = request.quoted,
                    modelClass = resolved.modelClass,
                )
            }
    }

    /**
     * Prefer a discovered Cadence model. If the PSI/type guess is `Session` /
     * `Authenticatable` / anything without columns, fall back to the instance
     * name (`user` → `User`) and, for attribute-key APIs, to every known column.
     */
    fun attributesPreferringCadence(
        root: Path,
        modelClass: String,
        receiver: String,
        unscopedKeys: Boolean,
        mode: ColumnSourceMode,
        probeJdbc: Boolean,
    ): PreferredAttributes {
        fun load(name: String): List<ModelAttributeIndex.Attribute> =
            if (name.isBlank()) {
                emptyList()
            } else {
                ModelAttributeIndex.attributesFor(root, name, mode = mode, probeJdbc = probeJdbc)
            }

        val primary = load(modelClass)
        if (primary.isNotEmpty() && ModelIndex.find(root, modelClass) != null) {
            return PreferredAttributes(primary, modelClass)
        }
        val convention = ModelCompletion.conventionClass(receiver)
        if (convention != null && !convention.equals(modelClass, ignoreCase = true)) {
            val fallback = load(convention)
            if (fallback.isNotEmpty()) {
                return PreferredAttributes(fallback, convention)
            }
        }
        if (primary.isNotEmpty()) {
            return PreferredAttributes(primary, modelClass)
        }
        if (unscopedKeys) {
            val any = ModelAttributeIndex.attributesForAny(root, mode = mode, probeJdbc = probeJdbc)
            if (any.isNotEmpty()) {
                return PreferredAttributes(any, modelClass.ifBlank { null })
            }
        }
        return PreferredAttributes(emptyList(), modelClass.ifBlank { null })
    }

    data class PreferredAttributes(
        val attrs: List<ModelAttributeIndex.Attribute>,
        val modelClass: String?,
    )

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
        val table = resolveTable(root, ctx.receiver, snap.text, snap.offset, snapshot)
        val columns =
            if (table != null) {
                snapshot.columnsForTable(table).ifEmpty { snapshot.allColumns }
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
        offset: Int? = null,
        snapshot: DbColumnIndex.Snapshot? = null,
    ): String? {
        val at = offset ?: source?.length ?: 0
        val modelClass =
            ModelPropertyCompletionContributor.resolveModelClass(source ?: "", receiver, at)
        val fromResolved = modelClass?.let { ModelIndex.tableForClass(projectRoot, it) }
        if (fromResolved != null && isKnownTable(projectRoot, modelClass, fromResolved, snapshot)) {
            return fromResolved
        }
        val convention = ModelCompletion.conventionClass(receiver)
        if (convention != null && !convention.equals(modelClass, ignoreCase = true)) {
            val fromConv = ModelIndex.tableForClass(projectRoot, convention)
            if (fromConv != null && isKnownTable(projectRoot, convention, fromConv, snapshot)) {
                return fromConv
            }
        }
        return fromResolved
    }

    private fun isKnownTable(
        projectRoot: Path,
        className: String?,
        table: String,
        snapshot: DbColumnIndex.Snapshot?,
    ): Boolean {
        if (className != null && ModelIndex.find(projectRoot, className) != null) return true
        if (snapshot != null) return snapshot.columnsForTable(table).isNotEmpty()
        return false
    }

    fun sourceLabel(fromJdbc: Boolean, fromMigrations: Boolean): String =
        when {
            fromJdbc && fromMigrations -> "migration+JDBC"
            fromJdbc -> "JDBC"
            else -> "migration"
        }
}
