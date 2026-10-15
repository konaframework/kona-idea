package dev.kona.idea

import java.sql.DriverManager
import java.util.Properties

/**
 * Reads column metadata from a live JDBC connection (when a driver is available).
 * Failures are swallowed — callers fall back to migration parsing.
 */
object JdbcColumnProbe {
    fun columnsFor(
        settings: EnvFile.DbSettings,
        loginTimeoutSeconds: Int = 2,
    ): Map<String, Set<String>> =
        runCatching {
            if (settings.driver != null) {
                runCatching { Class.forName(settings.driver) }
            }
            DriverManager.setLoginTimeout(loginTimeoutSeconds)
            val props = Properties()
            settings.username?.let { props.setProperty("user", it) }
            settings.password?.let { props.setProperty("password", it) }
            DriverManager.getConnection(settings.url, props).use { conn ->
                val meta = conn.metaData
                val catalog = conn.catalog
                val schema = runCatching { conn.schema }.getOrNull()
                val byTable = linkedMapOf<String, MutableSet<String>>()
                meta.getTables(catalog, schema, "%", arrayOf("TABLE", "VIEW")).use { tables ->
                    while (tables.next()) {
                        val table = tables.getString("TABLE_NAME") ?: continue
                        meta.getColumns(catalog, schema, table, "%").use { cols ->
                            val set = byTable.getOrPut(table.lowercase()) { linkedSetOf() }
                            while (cols.next()) {
                                val col = cols.getString("COLUMN_NAME") ?: continue
                                set += col
                            }
                        }
                    }
                }
                byTable.mapValues { it.value.toSet() }
            }
        }.getOrDefault(emptyMap())
}
