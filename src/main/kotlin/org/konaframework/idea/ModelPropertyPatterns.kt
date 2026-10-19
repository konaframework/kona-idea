package org.konaframework.idea

/**
 * Detects Cadence model instance property access: `project.name`, `user.email`.
 *
 * Resolves the model class from:
 * 1. Conventional instance name (`Project` → `project`)
 * 2. Nearby assignment `val project = Project.find…` / `Project project = …`
 */
object ModelPropertyPatterns {
    data class PropertyContext(
        val receiver: String,
        val prefix: String,
        val modelClass: String?,
    )

    private val ASSIGN_KT =
        Regex(
            """(?:val|var)\s+(\w+)\s*(?::\s*[\w.<>,?\s]+)?\s*=\s*(\w+)\s*\.""",
        )
    private val ASSIGN_JAVA =
        Regex("""(\w+)\s+(\w+)\s*=\s*(\w+)\s*\.""")

    fun propertyAt(text: CharSequence, offset: Int): PropertyContext? {
        var i = offset - 1
        while (i >= 0 && isPropPart(text[i])) i--
        if (i < 0 || text[i] != '.') return null
        val propPrefix = text.subSequence(i + 1, offset).toString()
        var j = i - 1
        while (j >= 0 && isIdentStart(text[j])) j--
        if (j == i - 1) return null
        val receiver = text.subSequence(j + 1, i).toString()
        if (receiver.isEmpty() || receiver[0].isUpperCase()) return null
        val modelClass = resolveModelClass(text, receiver)
        return PropertyContext(receiver, propPrefix, modelClass)
    }

    fun resolveModelClass(text: CharSequence, receiver: String): String? {
        val window = text.toString()
        ASSIGN_KT.findAll(window).toList().asReversed().forEach { m ->
            if (m.groupValues[1] == receiver) return m.groupValues[2]
        }
        ASSIGN_JAVA.findAll(window).toList().asReversed().forEach { m ->
            if (m.groupValues[2] == receiver) {
                // Prefer explicit type when it looks like a model (PascalCase).
                val type = m.groupValues[1]
                val rhs = m.groupValues[3]
                return if (type[0].isUpperCase() && type != "var" && type != "val") type else rhs
            }
        }
        // Conventional: project → Project
        return receiver.replaceFirstChar { it.uppercaseChar() }
    }

    fun isPropPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
