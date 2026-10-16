package dev.kona.idea

/**
 * Parses Kona/Cadence migration source for table → column names.
 *
 * Understands Kotlin and Java stubs from `grind make:migration`:
 * `schema.create("users") { … }`, `schema.table("users") { … }`,
 * `id()`, `string("email")`, `timestamps()`, `morphs("taggable")`, `dropColumn(…)`.
 */
object MigrationColumnParser {
    private val CREATE_OR_TABLE =
        Regex("""schema\s*\.\s*(?:create|table)\s*\(\s*["']([^"']+)["']""")

    private val COLUMN_CALL =
        Regex(
            """\b(?:string|text|integer|bigInteger|boolean|bool|timestamp|foreignId|softDeletes|id)\s*\(\s*(?:["']([^"']+)["'])?\s*""",
        )

    private val TIMESTAMPS = Regex("""\btimestamps\s*\(\s*\)""")
    private val MORPHS = Regex("""\b(?:nullableMorphs|uuidMorphs|morphs)\s*\(\s*["']([^"']+)["']""")
    private val DROP_COLUMN =
        Regex("""\bdropColumn\s*\(\s*((?:["'][^"']+["']\s*,?\s*)+)\)""")

    data class TableColumns(
        val table: String,
        val columns: Set<String>,
        val dropped: Set<String> = emptySet(),
    )

    fun parse(source: String): List<TableColumns> {
        val results = mutableListOf<TableColumns>()
        val matches = CREATE_OR_TABLE.findAll(source).toList()
        if (matches.isEmpty()) return emptyList()

        for (idx in matches.indices) {
            val m = matches[idx]
            val table = m.groupValues[1]
            val blockStart = m.range.last + 1
            val blockEnd = matches.getOrNull(idx + 1)?.range?.first ?: source.length
            val block = source.substring(blockStart, blockEnd)
            val cols = linkedSetOf<String>()
            val dropped = linkedSetOf<String>()

            COLUMN_CALL.findAll(block).forEach { call ->
                val method = call.value.substringBefore('(').trim()
                val explicit = call.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                when {
                    method == "id" -> cols += (explicit ?: "id")
                    method == "softDeletes" -> cols += (explicit ?: "deleted_at")
                    explicit != null -> cols += explicit
                }
            }
            if (TIMESTAMPS.containsMatchIn(block)) {
                cols += "created_at"
                cols += "updated_at"
            }
            MORPHS.findAll(block).forEach { morph ->
                val base = morph.groupValues[1]
                cols += "${base}_type"
                cols += "${base}_id"
            }
            DROP_COLUMN.findAll(block).forEach { drop ->
                Regex("""["']([^"']+)["']""").findAll(drop.groupValues[1]).forEach {
                    dropped += it.groupValues[1]
                }
            }
            cols.removeAll(dropped)
            if (cols.isNotEmpty() || dropped.isNotEmpty()) {
                results += TableColumns(table, cols, dropped)
            }
        }
        return results
    }

    /** Merge create/alter/drop across many migration files into a net column set per table. */
    fun merge(sources: Iterable<String>): Map<String, Set<String>> {
        val byTable = linkedMapOf<String, MutableSet<String>>()
        for (source in sources) {
            for (tableCols in parse(source)) {
                val set = byTable.getOrPut(tableCols.table) { linkedSetOf() }
                set += tableCols.columns
                set.removeAll(tableCols.dropped)
            }
        }
        return byTable.mapValues { it.value.toSet() }
    }

    fun allColumns(merged: Map<String, Set<String>>): Set<String> =
        merged.values.flatten().toSet()
}
