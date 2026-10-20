package org.konaframework.idea

/**
 * Detects Cadence model instance property access: `project.name`, `user.email`,
 * `user?.email`.
 *
 * Resolves the model class from:
 * 1. Nearby assignment `val user = User.find…` / `User user = …`
 * 2. Nearby parameter / type annotation `user: User` / `User user`
 * 3. Conventional instance name (`User` → `user`)
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
        val end = offset.coerceIn(0, text.length)
        var i = end - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        while (i >= 0 && isPropPart(text[i])) i--
        val nameStart = i + 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0 || text[i] != '.') return null
        val propPrefix =
            CompletionText.stripDummy(text.subSequence(nameStart, end).toString()).trim()
        var j = i - 1
        while (j >= 0 && (text[j] == '?' || text[j] == '!')) j--
        val identEnd = j + 1
        while (j >= 0 && isIdentStart(text[j])) j--
        if (identEnd == j + 1) return null
        val receiver = text.subSequence(j + 1, identEnd).toString()
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
                val type = m.groupValues[1]
                val rhs = m.groupValues[3]
                return if (type.isNotEmpty() && type[0].isUpperCase() && type != "var" && type != "val") {
                    type
                } else {
                    rhs
                }
            }
        }
        val escaped = Regex.escape(receiver)
        val ktParam = Regex("""\b$escaped\s*:\s*(\w+)""")
        ktParam.findAll(window).toList().asReversed().forEach { m ->
            val type = m.groupValues[1]
            if (type.isNotEmpty() && type[0].isUpperCase()) return type
        }
        val javaParam = Regex("""\b([A-Z]\w*(?:<[^>]*>)?)\s+$escaped\b""")
        javaParam.findAll(window).toList().asReversed().forEach { m ->
            val type = m.groupValues[1].substringBefore('<')
            if (type != "var" && type != "val") return type
        }
        return receiver.replaceFirstChar { it.uppercaseChar() }
    }

    fun isPropPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()
}
