package dev.kona.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Aggregates DB column names from migrations and an optional live JDBC connection.
 */
object DbColumnIndex {
    data class Snapshot(
        val byTable: Map<String, Set<String>>,
        val fromMigrations: Boolean,
        val fromJdbc: Boolean,
    ) {
        val allColumns: Set<String>
            get() = byTable.values.flatten().toSet()
    }

    fun collect(projectRoot: Path, probeJdbc: Boolean = true): Snapshot {
        val migrationSources = readMigrationSources(projectRoot)
        val fromMigrations = MigrationColumnParser.merge(migrationSources)
        var fromJdbc = emptyMap<String, Set<String>>()
        var jdbcOk = false
        if (probeJdbc) {
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
