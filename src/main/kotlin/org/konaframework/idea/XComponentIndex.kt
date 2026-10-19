package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Crema x-component tags resolve to views under the components. prefix (see CremaRuntime).
 *
 * Scans views/components for .crema.html / .crema.kt files and extracts
 * @slot('name') / echo-style slot hints from component bodies.
 */
object XComponentIndex {
    data class ComponentInfo(
        /** Tag name without `x-` prefix, e.g. `alert` or `inputs.button`. */
        val tagName: String,
        /** View name, e.g. `components.alert`. */
        val viewName: String,
        val path: Path,
        val slotNames: Set<String>,
    )

    private val SLOT_DIRECTIVE = Regex("""@slot\(\s*['"]([^'"]+)['"]\s*\)""")
    private val YIELD_OR_ECHO =
        Regex("""(?:\{\{\s*\$?(\w+)\s*\}\}|@yield\(\s*['"]([^'"]+)['"]\s*\))""")

    fun componentsRoot(projectRoot: Path): Path =
        ViewIndex.viewsRoot(projectRoot).resolve("components")

    fun list(projectRoot: Path): List<ComponentInfo> {
        val root = componentsRoot(projectRoot)
        if (!Files.isDirectory(root)) return emptyList()
        val results = mutableListOf<ComponentInfo>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }.forEach { file ->
                val name = file.fileName.toString()
                val ext = ViewIndex.EXTENSIONS.firstOrNull { name.endsWith(it, ignoreCase = true) }
                    ?: return@forEach
                val relative = root.relativize(file).toString().replace('\\', '/')
                val withoutExt = relative.removeSuffix(ext)
                val tagName = withoutExt.replace('/', '.')
                val viewName = "components.$tagName"
                val body = runCatching { Files.readString(file) }.getOrDefault("")
                results +=
                    ComponentInfo(
                        tagName = tagName,
                        viewName = viewName,
                        path = file,
                        slotNames = extractSlotHints(body),
                    )
            }
        }
        return results.sortedBy { it.tagName }
    }

    fun tagNames(projectRoot: Path): List<String> = list(projectRoot).map { it.tagName }

    fun extractSlotHints(body: String): Set<String> {
        val slots = linkedSetOf<String>()
        SLOT_DIRECTIVE.findAll(body).forEach { slots += it.groupValues[1] }
        // Common named-slot variables rendered in component templates.
        YIELD_OR_ECHO.findAll(body).forEach { m ->
            val name = m.groupValues[1].ifEmpty { m.groupValues[2] }
            if (name.isNotEmpty() && name != "slot" && name != "attributes") {
                slots += name
            }
        }
        return slots
    }
}
