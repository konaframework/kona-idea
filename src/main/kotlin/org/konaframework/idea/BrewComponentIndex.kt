package org.konaframework.idea

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Discovers Brew components under `app/brew` (Kotlin/Java) and maps them to
 * convention views (`views/brew/<name>.crema.html`) plus public members used
 * by `wire:*` values and `{{ }}` echoes.
 *
 * Mirrors framework naming in `ComponentRegistry` / `ComponentMeta` without
 * loading classes at runtime.
 */
object BrewComponentIndex {
    const val DEFAULT_VIEW_PREFIX: String = "brew"
    const val DEFAULT_NAMESPACE: String = "app.brew"

    enum class MemberKind {
        PROPERTY,
        ACTION,
        COMPUTED,
    }

    data class Member(
        val name: String,
        val kind: MemberKind,
    )

    data class ComponentInfo(
        val className: String,
        /** Framework component name, e.g. `todos` or `admin.show-posts`. */
        val componentName: String,
        /** Convention view name, e.g. `brew.todos`, or an explicit `view("…")`. */
        val viewName: String,
        val sourcePath: Path?,
        val members: List<Member>,
    ) {
        fun actions(): List<String> =
            members.filter { it.kind == MemberKind.ACTION }.map { it.name }

        fun propertiesAndComputed(): List<String> =
            members
                .filter { it.kind == MemberKind.PROPERTY || it.kind == MemberKind.COMPUTED }
                .map { it.name }
    }

    private val KOTLIN_CLASS =
        Regex("""(?:open\s+|abstract\s+)?class\s+(\w+)\s*(?:<[^>]*>)?\s*:\s*Component\b""")
    private val JAVA_CLASS =
        Regex("""(?:public\s+|abstract\s+)?class\s+(\w+)\s+extends\s+Component\b""")
    private val BREW_COMPONENT_ANN =
        Regex("""@BrewComponent\(\s*["']([^"']+)["']\s*\)""")
    private val RENDER_VIEW_KT =
        Regex(
            """(?:override\s+)?fun\s+render\s*\([^)]*\)[^{=\n]*(?:=\s*view\(\s*["']([^"']+)["']|\{\s*[^}]*?view\(\s*["']([^"']+)["'])""",
        )
    private val RENDER_VIEW_JAVA =
        Regex(
            """(?:public\s+)?Object\s+render\s*\(\s*\)\s*\{[\s\S]*?view\(\s*["']([^"']+)["']""",
        )
    private val KT_PROP =
        Regex("""(?m)^[ \t]*(?:public\s+)?(?:override\s+)?(?:var|val)\s+(\w+)\b""")
    private val KT_FUN =
        Regex(
            """(?m)^[ \t]*(?:@[A-Za-z.]+(?:\([^)]*\))?[ \t]*\n[ \t]*)*(?:public\s+)?(?:override\s+)?fun\s+(\w+)\s*\(""",
        )
    private val KT_COMPUTED =
        Regex(
            """(?m)^[ \t]*@Computed(?:\([^)]*\))?[ \t]*\n[ \t]*(?:public\s+)?(?:override\s+)?fun\s+(\w+)\s*\(""",
        )
    private val JAVA_FIELD =
        Regex("""(?m)^[ \t]*public\s+(?!class\b|static\b|void\b)[\w.<>,\s\[\]]+\s+(\w+)\s*[=;]""")
    private val JAVA_METHOD =
        Regex(
            """(?m)^[ \t]*(?:@[A-Za-z.]+(?:\([^)]*\))?[ \t]*\n[ \t]*)*public\s+(?:static\s+)?[\w.<>,\s\[\]]+\s+(\w+)\s*\(""",
        )
    private val JAVA_COMPUTED =
        Regex(
            """(?m)^[ \t]*@Computed(?:\([^)]*\))?[ \t]*\n[ \t]*public\s+[\w.<>,\s\[\]]+\s+(\w+)\s*\(""",
        )

    /** Lifecycle + non-action names from `ComponentMeta`. */
    private val LIFECYCLE_EXACT =
        setOf(
            "mount", "boot", "booted", "exception", "hydrate", "dehydrate",
            "updating", "updated", "rendering", "rendered", "scriptSrc",
        )
    private val LIFECYCLE_PREFIXED = Regex("^(hydrate|dehydrate|updating|updated)[A-Z]")
    private val NON_ACTIONS = setOf("equals", "hashCode", "toString", "copy", "render", "template")
    private val DATA_CLASS = Regex("component\\d+")

    fun brewDirs(projectRoot: Path): List<Path> =
        listOf(
            projectRoot.resolve("src/main/kotlin/app/brew"),
            projectRoot.resolve("src/main/java/app/brew"),
        )

    fun collect(projectRoot: Path): List<ComponentInfo> {
        val results = linkedMapOf<String, ComponentInfo>()
        for (dir in brewDirs(projectRoot)) {
            if (!Files.isDirectory(dir)) continue
            Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    val name = file.fileName.toString()
                    if (!name.endsWith(".kt") && !name.endsWith(".java")) return@forEach
                    val relativePkg = packageRelativeToBrew(dir, file)
                    parseSource(Files.readString(file), file, relativePkg).forEach { info ->
                        results[info.componentName] = info
                    }
                }
            }
        }
        return results.values.sortedBy { it.componentName }
    }

    fun byComponentName(projectRoot: Path): Map<String, ComponentInfo> =
        collect(projectRoot).associateBy { it.componentName }

    fun byViewName(projectRoot: Path): Map<String, ComponentInfo> =
        collect(projectRoot).associateBy { it.viewName }

    /**
     * Resolve the Brew component that owns [viewFile] under `src/main/resources/views`.
     */
    fun ownerForViewFile(projectRoot: Path, viewFile: Path): ComponentInfo? {
        val viewName = ViewIndex.relativeDisplay(projectRoot, viewFile) ?: return null
        return ownerForViewName(projectRoot, viewName)
    }

    fun ownerForViewName(projectRoot: Path, viewName: String): ComponentInfo? {
        val byView = byViewName(projectRoot)
        byView[viewName]?.let { return it }
        val prefix = "$DEFAULT_VIEW_PREFIX."
        if (viewName.startsWith(prefix)) {
            val componentName = viewName.removePrefix(prefix)
            return byComponentName(projectRoot)[componentName]
        }
        return null
    }

    fun parseSource(
        source: String,
        sourcePath: Path? = null,
        packageRelative: String = "",
    ): List<ComponentInfo> {
        val classes = mutableListOf<Triple<String, IntRange, Boolean>>()
        KOTLIN_CLASS.findAll(source).forEach { classes += Triple(it.groupValues[1], it.range, true) }
        JAVA_CLASS.findAll(source).forEach { classes += Triple(it.groupValues[1], it.range, false) }
        if (classes.isEmpty()) return emptyList()

        return classes.mapIndexed { idx, (name, range, kotlin) ->
            val end = classes.getOrNull(idx + 1)?.second?.first ?: source.length
            val body = source.substring(range.first, end)
            val before = source.substring(0, range.first)
            val annotated =
                BREW_COMPONENT_ANN.findAll(before).lastOrNull()?.groupValues?.get(1)
            val componentName =
                annotated
                    ?: if (packageRelative.isEmpty()) {
                        kebab(name)
                    } else {
                        packageRelative.replace('_', '-') + "." + kebab(name)
                    }
            val explicitView =
                if (kotlin) {
                    RENDER_VIEW_KT.find(body)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                } else {
                    RENDER_VIEW_JAVA.find(body)?.groupValues?.get(1)
                }
            val viewName = explicitView ?: "$DEFAULT_VIEW_PREFIX.$componentName"
            ComponentInfo(
                className = name,
                componentName = componentName,
                viewName = viewName,
                sourcePath = sourcePath,
                members = parseMembers(body, kotlin),
            )
        }
    }

    fun kebab(value: String): String =
        value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
            .lowercase()

    fun isActionAttribute(rawName: String): Boolean {
        val base = wireBase(rawName)
        return base in setOf("click", "submit", "init", "target")
    }

    fun isPropertyAttribute(rawName: String): Boolean {
        val base = wireBase(rawName)
        return base in setOf("model", "show", "text", "dirty")
    }

    private fun wireBase(rawName: String): String {
        val name = rawName.substringBefore('=').trim().lowercase()
        val rest = name.removePrefix("wire:")
        return rest.substringBefore('.')
    }

    private fun parseMembers(body: String, kotlin: Boolean): List<Member> {
        // Ignore nested classes / companion objects so their fields are not treated as component state.
        val scope = outerClassBody(body)
        val out = linkedMapOf<String, Member>()
        if (kotlin) {
            KT_PROP.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isNotEmpty()) out[n] = Member(n, MemberKind.PROPERTY)
            }
            KT_COMPUTED.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isNotEmpty() && !isExcludedAction(n)) {
                    out[n] = Member(n, MemberKind.COMPUTED)
                }
            }
            KT_FUN.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isEmpty() || isExcludedAction(n) || out[n]?.kind == MemberKind.COMPUTED) return@forEach
                if (out[n]?.kind == MemberKind.PROPERTY) return@forEach
                // Skip Kotlin property accessors when present as fun getX — rare in Brew components.
                if (n.startsWith("get") || n.startsWith("set") || n.startsWith("is")) {
                    val rest = n.drop(if (n.startsWith("is")) 2 else 3)
                    if (rest.isNotEmpty() && rest[0].isUpperCase()) {
                        val prop = rest.replaceFirstChar { it.lowercase() }
                        if (out.containsKey(prop)) return@forEach
                    }
                }
                out.putIfAbsent(n, Member(n, MemberKind.ACTION))
            }
        } else {
            JAVA_FIELD.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isNotEmpty()) out[n] = Member(n, MemberKind.PROPERTY)
            }
            JAVA_COMPUTED.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isNotEmpty() && !isExcludedAction(n)) {
                    out[n] = Member(n, MemberKind.COMPUTED)
                }
            }
            JAVA_METHOD.findAll(scope).forEach { m ->
                val n = m.groupValues[1]
                if (n.isEmpty() || isExcludedAction(n) || out[n]?.kind == MemberKind.COMPUTED) return@forEach
                if (n.startsWith("get") || n.startsWith("set") || n.startsWith("is")) {
                    val rest = n.drop(if (n.startsWith("is")) 2 else 3)
                    if (rest.isNotEmpty() && rest[0].isUpperCase()) {
                        val prop = rest.replaceFirstChar { it.lowercase() }
                        if (out.containsKey(prop)) return@forEach
                    }
                }
                out.putIfAbsent(n, Member(n, MemberKind.ACTION))
            }
        }
        return out.values.toList()
    }

    private val NESTED_TYPE =
        Regex(
            """(?m)^[ \t]+(?:companion\s+object|(?:(?:public|private|protected|internal|static|data|inner)\s+)*class\s+\w+)""",
        )

    /** Truncate at the first nested type so inner fields are not treated as component state. */
    private fun outerClassBody(body: String): String {
        val nested = NESTED_TYPE.find(body) ?: return body
        return body.substring(0, nested.range.first)
    }

    private fun isExcludedAction(name: String): Boolean =
        name in LIFECYCLE_EXACT ||
            name in NON_ACTIONS ||
            LIFECYCLE_PREFIXED.containsMatchIn(name) ||
            DATA_CLASS.matches(name)

    private fun packageRelativeToBrew(brewRoot: Path, file: Path): String {
        val parent = file.parent ?: return ""
        if (parent == brewRoot) return ""
        return brewRoot.relativize(parent).toString().replace('\\', '/').replace('/', '.')
    }
}
