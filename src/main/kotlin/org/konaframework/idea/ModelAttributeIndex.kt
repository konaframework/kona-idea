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
        val info = ModelIndex.find(projectRoot, className) ?: return emptyList()
        val source = info.sourcePath?.takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }.orEmpty()
        val parsed = parseSource(source)
        val columns =
            DbColumnIndex.collect(projectRoot, mode = mode, probeJdbc = probeJdbc)
                .columnsForTable(info.table)
        return merge(columns, info.table, parsed)
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
