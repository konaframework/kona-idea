package org.konaframework.idea

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Makes the app's JDBC drivers visible to [DriverManager] inside the IDE.
 *
 * IDEA does not use the Gradle runtime classpath, so `org.sqlite.JDBC` /
 * `org.postgresql.Driver` are missing unless we load them from the app's
 * `build/kona-runtime.classpath` (written by grind / `konaClasspath`).
 */
object ProjectJdbcDrivers {
    private val DRIVER_CLASSES =
        listOf(
            "org.sqlite.JDBC",
            "org.h2.Driver",
            "org.postgresql.Driver",
            "com.mysql.cj.jdbc.Driver",
            "org.mariadb.jdbc.Driver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "oracle.jdbc.OracleDriver",
        )

    private val registered = ConcurrentHashMap.newKeySet<String>()
    private val loaders = ConcurrentHashMap<String, URLClassLoader>()

    @JvmStatic
    fun ensure(projectRoot: Path?, className: String? = null) {
        if (className != null && alreadyAvailable(className)) {
            return
        }
        val root = projectRoot ?: return
        val urls =
            classpathJars(root).map { it.toUri().toURL() }.toTypedArray()
        if (urls.isEmpty()) {
            return
        }
        val key = root.toAbsolutePath().normalize().toString()
        val loader =
            loaders.computeIfAbsent(key) {
                URLClassLoader(urls, JdbcColumnProbe::class.java.classLoader)
            }
        val wanted = listOfNotNull(className) + DRIVER_CLASSES
        for (name in wanted.distinct()) {
            if (alreadyAvailable(name)) continue
            runCatching {
                val cls = Class.forName(name, true, loader)
                val driver = cls.getDeclaredConstructor().newInstance() as Driver
                DriverManager.registerDriver(Shim(driver))
                registered += name
            }
        }
    }

    fun classpathJars(projectRoot: Path): List<Path> {
        val cached = projectRoot.resolve("build/kona-runtime.classpath")
        if (!Files.isRegularFile(cached)) {
            return emptyList()
        }
        return runCatching { Files.readString(cached) }.getOrDefault("")
            .split(java.io.File.pathSeparator)
            .map { it.trim() }
            .filter { it.endsWith(".jar", ignoreCase = true) }
            .map { Path.of(it) }
            .filter { Files.isRegularFile(it) }
    }

    internal fun alreadyAvailable(className: String): Boolean =
        className in registered || runCatching { Class.forName(className) }.isSuccess

    /** DriverManager only accepts drivers from the system class loader. */
    internal class Shim(private val delegate: Driver) : Driver {
        override fun connect(url: String?, info: Properties?): Connection? = delegate.connect(url, info)

        override fun acceptsURL(url: String?): Boolean = delegate.acceptsURL(url)

        override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> =
            delegate.getPropertyInfo(url, info)

        override fun getMajorVersion(): Int = delegate.majorVersion

        override fun getMinorVersion(): Int = delegate.minorVersion

        override fun jdbcCompliant(): Boolean = delegate.jdbcCompliant()

        override fun getParentLogger(): Logger = delegate.parentLogger
    }
}
