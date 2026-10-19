package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Aggregates DB column names from migrations and/or a live JDBC connection,
 * honoring [ColumnSourceMode].
 */
object DbColumnIndex {
    data class Snapshot(
        val byTable: Map<String, Set<String>>,
        val fromMigrations: Boolean,
        val fromJdbc: Boolean,
    ) {
        val allColumns: Set<String>
            get() = byTable.values.flatten().toSet()

        fun columnsForTable(table: String): Set<String> =
            byTable[table.lowercase()].orEmpty()
    }

    fun collect(
        projectRoot: Path,
        mode: ColumnSourceMode = ColumnSourceMode.BOTH,
        probeJdbc: Boolean = true,
    ): Snapshot {
        val useMigrations =
            mode == ColumnSourceMode.BOTH || mode == ColumnSourceMode.MIGRATIONS_ONLY
        val useJdbc =
            probeJdbc && (mode == ColumnSourceMode.BOTH || mode == ColumnSourceMode.CONNECTION_ONLY)

        val fromMigrations =
            if (useMigrations) {
                MigrationColumnParser.merge(readMigrationSources(projectRoot))
            } else {
                emptyMap()
            }

        var fromJdbc = emptyMap<String, Set<String>>()
        var jdbcOk = false
        if (useJdbc) {
            val env = EnvFile.load(projectRoot.resolve(".env"))
            val settings = EnvFile.dbSettings(env)
            if (settings != null) {
                fromJdbc = JdbcColumnProbe.columnsFor(settings)
                jdbcOk = fromJdbc.isNotEmpty()
            }
        }

        val merged = linkedMapOf<String, MutableSet<String>>()
        fun putAll(map: Map<String, Set<String>>) {
            for ((table, cols) in map) {
                merged.getOrPut(table.lowercase()) { linkedSetOf() } += cols
            }
        }
        putAll(fromMigrations)
        putAll(fromJdbc)
        return Snapshot(
            byTable = merged.mapValues { it.value.toSet() },
            fromMigrations = fromMigrations.isNotEmpty(),
            fromJdbc = jdbcOk,
        )
    }

    fun readMigrationSources(projectRoot: Path): List<String> {
        val dirs =
            listOf(
                projectRoot.resolve("src/main/kotlin/database/migrations"),
                projectRoot.resolve("src/main/java/database/migrations"),
            )
        val sources = mutableListOf<String>()
        for (dir in dirs) {
            if (!Files.isDirectory(dir)) continue
            Files.list(dir).use { stream ->
                stream
                    .filter {
                        val n = it.fileName.toString()
                        it.isRegularFile() && (n.endsWith(".kt") || n.endsWith(".java"))
                    }
                    .forEach { sources += Files.readString(it) }
            }
        }
        return sources
    }
}
