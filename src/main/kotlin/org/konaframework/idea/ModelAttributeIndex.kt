package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path

/**
 * Catalog of completable Cadence model attributes: DB columns, accessors/mutators,
 * appends, and relation method names.
 */
object ModelAttributeIndex {
    enum class Kind {
        COLUMN,
        FILLABLE,
        CAST,
        ACCESSOR,
        APPEND,
        RELATION,
    }

    data class Attribute(
        val name: String,
        val kind: Kind,
        /** Lookup typeText, e.g. `column · users`, `accessor`, `relation · HasMany`. */
        val typeText: String,
    )

    private val ACCESSOR_EXCLUDES = setOf("Appended", "Mutated", "Raw")

    private val RELATION_ALT =
        listOf(
            "hasMany",
            "hasOne",
            "belongsTo",
            "belongsToMany",
            "morphMany",
            "morphOne",
            "morphTo",
            "morphToMany",
            "hasManyThrough",
            "hasOneThrough",
            "morphedByMany",
        ).joinToString("|")

    private val KT_ACCESSOR =
        Regex("""(?:fun|override\s+fun)\s+get(\w+)Attribute\s*\(""")
    private val JAVA_ACCESSOR =
        Regex("""(?:public|protected)\s+[\w.<>,\s?]+\s+get(\w+)Attribute\s*\(""")
    private val KT_MUTATOR =
        Regex("""(?:fun|override\s+fun)\s+set(\w+)Attribute\s*\(""")
    private val JAVA_MUTATOR =
        Regex("""(?:public|protected)\s+[\w.<>,\s?]+\s+set(\w+)Attribute\s*\(""")

    private val KT_APPENDS =
        Regex("""(?:override\s+)?(?:val|var)\s+appends\s*=\s*(?:setOf|listOf|mutableSetOf)\s*\(([^)]*)\)""")
    private val JAVA_APPENDS_FIELD =
        Regex("""(?:protected|public|private)?\s*(?:final\s+)?Set\s*<\s*String\s*>\s+appends\s*=\s*Set\.of\s*\(([^)]*)\)""")
    private val JAVA_APPENDS_GETTER =
        Regex("""(?:getAppends|appends)\s*\(\s*\)\s*\{[^}]*?(?:Set\.of|List\.of)\s*\(([^)]*)\)""")

    private val KT_STRING_SET =
        Regex(
            """(?:override\s+)?(?:val|var)\s+(fillable|hidden|visible|guarded)\s*=\s*(?:setOf|listOf|mutableSetOf)\s*\(([^)]*)\)""",
        )
    private val JAVA_STRING_SET_FIELD =
        Regex(
            """(?:protected|public|private)?\s*(?:final\s+)?Set\s*<\s*String\s*>\s+(fillable|hidden|visible|guarded)\s*=\s*Set\.of\s*\(([^)]*)\)""",
        )
    private val JAVA_STRING_SET_GETTER =
        Regex(
            """get(Fillable|Hidden|Visible|Guarded)\s*\(\s*\)\s*\{[^}]*?(?:Set\.of|List\.of)\s*\(([^)]*)\)""",
        )
    private val KT_CASTS =
        Regex("""(?:override\s+)?(?:val|var)\s+casts\s*=\s*mapOf\s*\(([^)]*)\)""")
    private val JAVA_CASTS =
        Regex("""getCasts\s*\(\s*\)\s*\{[\s\S]{0,800}?Map\.of\s*\(([^)]*)\)""")
    private val KT_TIMESTAMPS_OFF =
        Regex("""(?:override\s+)?(?:val|var)\s+timestamps\s*=\s*false""")
    private val JAVA_TIMESTAMPS_OFF =
        Regex("""getTimestamps\s*\(\s*\)\s*\{[^}]*return\s*false""")

    private val KT_RELATION =
        Regex(
            """fun\s+(\w+)\s*\([^)]*\)\s*(?::[^{=]+)?\s*(?:=\s*|\{\s*(?:return\s+)?)($RELATION_ALT)\s*\(""",
        )
    private val JAVA_RELATION =
        Regex(
            """(?:public|protected|private)\s+[\w.<>,\s?]+\s+(\w+)\s*\([^)]*\)\s*\{[^{}]{0,400}?\b($RELATION_ALT)\s*\(""",
        )

    private val CAMEL_SPLIT = Regex("([a-z0-9])([A-Z])")
    private val ACRONYM_SPLIT = Regex("([A-Z]+)([A-Z][a-z])")

    fun attributesFor(
        projectRoot: Path,
        className: String,
        mode: ColumnSourceMode = ColumnSourceMode.BOTH,
        probeJdbc: Boolean = true,
    ): List<Attribute> {
        val simple = className.substringAfterLast('.')
        if (simple.isEmpty() || !simple[0].isUpperCase()) return emptyList()
        val info = ModelIndex.find(projectRoot, simple)
        val table = info?.table ?: ModelIndex.tableForClass(projectRoot, simple) ?: return emptyList()
        val source = info?.sourcePath?.takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }.orEmpty()
        val parsed = parseSource(source)
        val columns =
            DbColumnIndex.collect(projectRoot, mode = mode, probeJdbc = probeJdbc)
                .columnsForTable(table)
                .toMutableSet()
        val known = info != null || columns.isNotEmpty() || parsed.isNotEmpty()
        if (!known) return emptyList()
        columns += typicalCadenceColumns(source)
        return merge(columns, table, parsed)
    }

    fun parseSource(source: String): List<Attribute> {
        if (source.isBlank()) return emptyList()
        val out = linkedMapOf<String, Attribute>()

        fun put(name: String, kind: Kind, typeText: String) {
            if (name.isEmpty()) return
            out.putIfAbsent(name, Attribute(name, kind, typeText))
        }

        for (studly in accessorStudlies(source)) {
            val snake = studlyToSnake(studly)
            val camel = studlyToCamel(studly)
            put(snake, Kind.ACCESSOR, "accessor")
            if (camel != snake) put(camel, Kind.ACCESSOR, "accessor")
        }
        for (studly in mutatorStudlies(source)) {
            val snake = studlyToSnake(studly)
            val camel = studlyToCamel(studly)
            put(snake, Kind.ACCESSOR, "accessor")
            if (camel != snake) put(camel, Kind.ACCESSOR, "accessor")
        }
        for (name in appendNames(source)) {
            put(name, Kind.APPEND, "append")
        }
        for ((field, names) in stringSetFields(source)) {
            for (name in names) put(name, Kind.FILLABLE, field)
        }
        for (name in castKeys(source)) {
            put(name, Kind.CAST, "cast")
        }
        for ((method, rel) in relationMethods(source)) {
            val label = rel.replaceFirstChar { it.uppercaseChar() }
            put(method, Kind.RELATION, "relation · $label")
        }
        return out.values.toList()
    }

    fun merge(
        columns: Set<String>,
        table: String,
        fromSource: List<Attribute>,
    ): List<Attribute> {
        val out = linkedMapOf<String, Attribute>()
        val colType = if (table.isNotEmpty()) "column · $table" else "column"
        columns.sorted().forEach { col ->
            out[col] = Attribute(col, Kind.COLUMN, colType)
        }
        // Source attrs fill gaps (and add camel aliases / relations / appends).
        for (attr in fromSource) {
            out.putIfAbsent(attr.name, attr)
        }
        return out.values.toList()
    }

    fun accessorStudlies(source: String): List<String> {
        val found = linkedSetOf<String>()
        KT_ACCESSOR.findAll(source).map { it.groupValues[1] }.forEach { found += it }
        JAVA_ACCESSOR.findAll(source).map { it.groupValues[1] }.forEach { found += it }
        return found.filter { it !in ACCESSOR_EXCLUDES }
    }

    fun mutatorStudlies(source: String): List<String> {
        val found = linkedSetOf<String>()
        KT_MUTATOR.findAll(source).map { it.groupValues[1] }.forEach { found += it }
        JAVA_MUTATOR.findAll(source).map { it.groupValues[1] }.forEach { found += it }
        return found.filter { it !in ACCESSOR_EXCLUDES }
    }

    fun appendNames(source: String): List<String> {
        val raw =
            KT_APPENDS.find(source)?.groupValues?.get(1)
                ?: JAVA_APPENDS_FIELD.find(source)?.groupValues?.get(1)
                ?: JAVA_APPENDS_GETTER.find(source)?.groupValues?.get(1)
                ?: return emptyList()
        return STRING_LIT.findAll(raw).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toList()
    }

    fun stringSetFields(source: String): List<Pair<String, List<String>>> {
        val out = linkedMapOf<String, List<String>>()
        fun put(field: String, raw: String) {
            val names = STRING_LIT.findAll(raw).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toList()
            if (names.isNotEmpty()) out.putIfAbsent(field.lowercase(), names)
        }
        KT_STRING_SET.findAll(source).forEach { put(it.groupValues[1], it.groupValues[2]) }
        JAVA_STRING_SET_FIELD.findAll(source).forEach { put(it.groupValues[1], it.groupValues[2]) }
        JAVA_STRING_SET_GETTER.findAll(source).forEach { put(it.groupValues[1], it.groupValues[2]) }
        return out.entries.map { it.key to it.value }
    }

    fun castKeys(source: String): List<String> {
        val raw =
            KT_CASTS.find(source)?.groupValues?.get(1)
                ?: JAVA_CASTS.find(source)?.groupValues?.get(1)
                ?: return emptyList()
        return STRING_LIT.findAll(raw).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toList()
    }

    fun typicalCadenceColumns(source: String): Set<String> {
        val cols = linkedSetOf("id")
        val timestampsOff =
            KT_TIMESTAMPS_OFF.containsMatchIn(source) || JAVA_TIMESTAMPS_OFF.containsMatchIn(source)
        if (!timestampsOff) {
            cols += "created_at"
            cols += "updated_at"
        }
        if (source.contains("SoftDeletes") || Regex("""\bsoftDeletes\s*\(""").containsMatchIn(source)) {
            cols += "deleted_at"
        }
        return cols
    }

    fun relationMethods(source: String): List<Pair<String, String>> {
        val out = linkedMapOf<String, String>()
        KT_RELATION.findAll(source).forEach { m ->
            out.putIfAbsent(m.groupValues[1], m.groupValues[2])
        }
        JAVA_RELATION.findAll(source).forEach { m ->
            out.putIfAbsent(m.groupValues[1], m.groupValues[2])
        }
        return out.entries.map { it.key to it.value }
    }

    /** `FullName` → `full_name`. */
    fun studlyToSnake(studly: String): String {
        if (studly.isEmpty()) return studly
        val step = ACRONYM_SPLIT.replace(studly, "$1_$2")
        return CAMEL_SPLIT.replace(step, "$1_$2").lowercase()
    }

    /** `FullName` → `fullName`. */
    fun studlyToCamel(studly: String): String {
        if (studly.isEmpty()) return studly
        return studly.replaceFirstChar { it.lowercaseChar() }
    }

    private val STRING_LIT = Regex("""["']([^"']+)["']""")
}
