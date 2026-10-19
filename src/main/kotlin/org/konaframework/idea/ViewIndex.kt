package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * Discovers Crema view names under `src/main/resources/views`
 * (dotted names: `home`, `layouts.app`, `partials/nav` → `partials.nav`).
 */
object ViewIndex {
    val EXTENSIONS: List<String> = listOf(".crema.html", ".crema.kt")

    fun viewsRoot(projectRoot: Path): Path = projectRoot.resolve("src/main/resources/views")

    fun listNames(projectRoot: Path): List<String> {
        val root = viewsRoot(projectRoot)
        if (!Files.isDirectory(root)) return emptyList()
        val names = linkedSetOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }.forEach { file ->
                val name = file.fileName.toString()
                val ext = EXTENSIONS.firstOrNull { name.endsWith(it, ignoreCase = true) } ?: return@forEach
                val relative = root.relativize(file).toString().replace('\\', '/')
                val withoutExt = relative.removeSuffix(ext)
                names += withoutExt.replace('/', '.')
            }
        }
        return names.sorted()
    }

    fun resolve(projectRoot: Path, viewName: String): Path? {
        val relative = viewName.replace('.', '/')
        val root = viewsRoot(projectRoot)
        for (ext in EXTENSIONS) {
            val candidate = root.resolve(relative + ext)
            if (Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    fun relativeDisplay(projectRoot: Path, viewFile: Path): String? {
        val root = viewsRoot(projectRoot)
        if (!viewFile.startsWith(root)) return null
        val relative = viewFile.relativeTo(root).toString().replace('\\', '/')
        val ext = EXTENSIONS.firstOrNull { relative.endsWith(it, ignoreCase = true) } ?: return null
        return relative.removeSuffix(ext).replace('/', '.')
    }
}
