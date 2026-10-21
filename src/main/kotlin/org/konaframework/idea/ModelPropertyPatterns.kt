package org.konaframework.idea

/**
 * Detects Cadence model instance property access: `project.name`, `user.email`,
 * `user?.email`.
 *
 * Resolves the model class from the **type** of the receiver, not only from
 * `User.query().first()`:
 * 1. Kotlin typed local / param — `val user: User?`, `user: User`
 * 2. Kotlin / Java casts — `as? User`, `as User`, `(User) Auth.user()`
 * 3. Cadence factory/query assignment — `val user = User.find…`
 * 4. Java typed local / param — `User user = …`
 * 5. Conventional instance name (`User` → `user`)
 */
object ModelPropertyPatterns {
    data class PropertyContext(
        val receiver: String,
        val prefix: String,
        val modelClass: String?,
    )

    private val KT_BINDING =
        Regex("""(?:lateinit\s+)?(?:val|var)\s+(\w+)\b([^;\n{]*)""")
    private val JAVA_ASSIGN =
        Regex("""(?:(?:public|protected|private|final|static)\s+)*(\w+(?:\s*<[^>]*>)?)\s+(\w+)\s*=\s*([^;]*)""")
    private val KT_PARAM =
        Regex("""(?<![.\w])(\w+)\s*:\s*([\w.]+)\s*\??""")
    private val JAVA_PARAM =
        Regex("""\b([A-Z]\w*(?:\s*<[^>]*>)?)\s+(\w+)\b""")
    private val KT_CAST =
        Regex("""(?:^|[\s(])as\??\s+([\w.]+)""")
    private val JAVA_CAST =
        Regex("""=\s*\(\s*([\w.]+)\s*\)""")
    private val MODEL_CALL =
        Regex(
            """=\s*([\w.]+)\s*\.\s*(?:query|newQuery|find|findOrFail|findOrNew|findMany|first|firstOrFail|firstOrNew|firstOrCreate|create|createMany|make|hydrate|all|get|withTrashed|onlyTrashed)\b""",
        )
    private val NOT_TYPES =
        setOf(
            "var", "val", "this", "super", "null", "true", "false", "return",
            "if", "new", "class", "object", "fun", "void", "int", "long",
            "boolean", "String", "Any", "Unit",
        )

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
        val modelClass = resolveModelClass(text, receiver, end)
        return PropertyContext(receiver, propPrefix, modelClass)
    }

    fun resolveModelClass(text: CharSequence, receiver: String, offset: Int = text.length): String? {
        if (receiver.isBlank() || receiver == "this" || receiver == "super") {
            return ModelIndex.enclosingCadenceClass(text, offset)
        }
        val src = text.toString()
        typeFromLastKotlinBinding(src, receiver)?.let { return it }
        typeFromLastJavaBinding(src, receiver)?.let { return it }
        typeFromKotlinParam(src, receiver)?.let { return it }
        typeFromJavaParam(src, receiver)?.let { return it }
        return receiver.replaceFirstChar { it.uppercaseChar() }
    }

    /** Strip generics, nullability, and packages: `app.models.User?` → `User`. */
    fun simpleClassName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val name =
            raw.trim()
                .substringAfterLast('.')
                .substringBefore('<')
                .trimEnd('?')
                .trim()
        if (name.isEmpty() || !name[0].isUpperCase()) return null
        if (name in NOT_TYPES) return null
        return name
    }

    fun isPropPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun typeFromLastKotlinBinding(src: String, receiver: String): String? {
        val matches = KT_BINDING.findAll(src).filter { it.groupValues[1] == receiver }.toList()
        if (matches.isEmpty()) return null
        val m = matches.last()
        val span = m.groupValues[2] + "\n" + continuedSpan(src, m.range.last + 1)
        typeFromAnnotation(span)?.let { return it }
        typeFromCast(span)?.let { return it }
        typeFromModelCall(span)?.let { return it }
        return null
    }

    private fun typeFromLastJavaBinding(src: String, receiver: String): String? {
        val matches = JAVA_ASSIGN.findAll(src).filter { it.groupValues[2] == receiver }.toList()
        if (matches.isEmpty()) return null
        val m = matches.last()
        val declared = simpleClassName(m.groupValues[1])
        if (declared != null) return declared
        val span = "= " + m.groupValues[3] + "\n" + continuedSpan(src, m.range.last + 1)
        typeFromCast(span)?.let { return it }
        typeFromModelCall(span)?.let { return it }
        return null
    }

    private fun typeFromKotlinParam(src: String, receiver: String): String? =
        KT_PARAM.findAll(src).toList().asReversed()
            .firstNotNullOfOrNull { m ->
                if (m.groupValues[1] == receiver) simpleClassName(m.groupValues[2]) else null
            }

    private fun typeFromJavaParam(src: String, receiver: String): String? =
        JAVA_PARAM.findAll(src).toList().asReversed()
            .firstNotNullOfOrNull { m ->
                if (m.groupValues[2] == receiver) simpleClassName(m.groupValues[1]) else null
            }

    private fun typeFromAnnotation(span: String): String? {
        val trimmed = span.trimStart()
        if (!trimmed.startsWith(':')) return null
        val m = Regex("""^:\s*([\w.]+)""").find(trimmed) ?: return null
        return simpleClassName(m.groupValues[1])
    }

    private fun typeFromCast(span: String): String? {
        KT_CAST.findAll(span).lastOrNull()?.let { return simpleClassName(it.groupValues[1]) }
        JAVA_CAST.findAll(span).lastOrNull()?.let { return simpleClassName(it.groupValues[1]) }
        return null
    }

    private fun typeFromModelCall(span: String): String? {
        val m = MODEL_CALL.find(span) ?: return null
        return simpleModelName(m.groupValues[1])
    }

    /**
     * `User`, `app.models.User`, `User.QUERY`, `User.Companion` → `User`.
     * Java scaffolds keep a static `QUERY` companion field.
     */
    fun simpleModelName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        for (part in raw.trim().split('.').asReversed()) {
            if (part.equals("QUERY", ignoreCase = true) || part.equals("Companion", ignoreCase = true)) {
                continue
            }
            simpleClassName(part)?.let { return it }
        }
        return simpleClassName(raw)
    }

    /** New statement even when indented (DashboardController: `val session` after `val user`). */
    private val NEW_STATEMENT =
        Regex(
            """^\s*(?:lateinit\s+)?(?:val|var|fun|return|if|for|while|when|class|object|override|public|private|protected|internal)\b""",
        )

    /** Indented continuation lines after a declaration (`as? User` on the next line). */
    private fun continuedSpan(src: String, after: Int): String {
        if (after < 0 || after >= src.length) return ""
        val lines = mutableListOf<String>()
        for (line in src.substring(after).lineSequence()) {
            if (line.isBlank()) {
                lines += line
                continue
            }
            if (!line[0].isWhitespace()) break
            if (NEW_STATEMENT.containsMatchIn(line)) break
            lines += line
        }
        return lines.joinToString("\n")
    }
}
