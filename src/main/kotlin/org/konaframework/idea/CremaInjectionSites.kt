package org.konaframework.idea

/**
 * Finds Java/Kotlin string literals that should be injected as Crema.
 *
 * Sites:
 * - `// language=Crema` or block-comment `language=Crema` (IntelliLang-style) before a string
 * - Return / expression-body literals of `@CremaTemplate` methods
 * - Return / expression-body literals of `template()` that override
 *   [org.konaframework.brew.Component.template] (`override` / `@Override` /
 *   declared on a `Component` subclass)
 * - First argument of `inline(` / `BrewView.inline(` (Brew single-file markup)
 * - Arguments to parameters annotated `@CremaTemplate` (same-file callee)
 * - Expression annotated `@CremaTemplate """…"""`
 */
object CremaInjectionSites {
    enum class Reason {
        LANGUAGE_COMMENT,
        ANNOTATED_METHOD,
        ANNOTATED_EXPRESSION,
        ANNOTATED_PARAMETER,
        TEMPLATE_OVERRIDE,
        INLINE_CALL,
    }

    data class Site(
        val quoteStart: Int,
        val quoteEnd: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val reason: Reason,
        val textBlock: Boolean,
    ) {
        operator fun contains(offset: Int): Boolean = offset in quoteStart until quoteEnd
    }

    fun isJvmHostLanguage(languageId: String?): Boolean {
        if (languageId.isNullOrBlank()) return false
        return languageId.equals("JAVA", ignoreCase = true) ||
            languageId.equals("kotlin", ignoreCase = true)
    }

    fun shouldInject(source: CharSequence, hostStart: Int): Boolean =
        siteAt(source, hostStart) != null

    fun siteAt(source: CharSequence, offset: Int): Site? {
        if (offset < 0 || offset > source.length) return null
        return findAll(source).firstOrNull { offset in it }
    }

    fun findAll(source: CharSequence): List<Site> {
        if (source.isEmpty()) return emptyList()
        val literals = scanLiterals(source)
        if (literals.isEmpty()) return emptyList()
        val methods = findMethods(source)
        val componentRanges = componentClassRanges(source)
        val annotatedParams = annotatedParamCalls(source, methods)
        val out = linkedMapOf<Int, Site>()

        fun offer(lit: Literal, reason: Reason) {
            out.putIfAbsent(
                lit.quoteStart,
                Site(
                    quoteStart = lit.quoteStart,
                    quoteEnd = lit.quoteEnd,
                    contentStart = lit.contentStart,
                    contentEnd = lit.contentEnd,
                    reason = reason,
                    textBlock = lit.textBlock,
                ),
            )
        }

        for (lit in literals) {
            if (isLanguageCrema(lit.precedingComment)) {
                offer(lit, Reason.LANGUAGE_COMMENT)
                continue
            }
            if (expressionAnnotated(source, lit.quoteStart)) {
                offer(lit, Reason.ANNOTATED_EXPRESSION)
                continue
            }
            if (inlineCallArgument(source, lit.quoteStart)) {
                offer(lit, Reason.INLINE_CALL)
                continue
            }
            if (annotatedParams.any { lit.quoteStart in it }) {
                offer(lit, Reason.ANNOTATED_PARAMETER)
                continue
            }
            val method = methods.firstOrNull { lit.quoteStart in it.bodyStart until it.bodyEnd }
            if (method != null && isReturnOrExpressionLiteral(source, method, lit)) {
                when {
                    method.cremaAnnotated -> offer(lit, Reason.ANNOTATED_METHOD)
                    isTemplateOverride(method, componentRanges) ->
                        offer(lit, Reason.TEMPLATE_OVERRIDE)
                }
            }
        }
        return out.values.sortedBy { it.quoteStart }
    }

    internal data class Literal(
        val quoteStart: Int,
        val quoteEnd: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val textBlock: Boolean,
        val precedingComment: String?,
    )

    internal data class MethodSpan(
        val name: String,
        val signatureStart: Int,
        val bodyStart: Int,
        val bodyEnd: Int,
        val expressionBody: Boolean,
        val cremaAnnotated: Boolean,
        val overridden: Boolean,
        val paramCremaIndexes: Set<Int>,
    )

    private val BETWEEN_COMMENT_AND_STRING =
        Regex("""[\s\w.()<>,=:@\[\]?]*""")
    private val LANGUAGE_CREMA =
        Regex("""language\s*=\s*Crema\b""", RegexOption.IGNORE_CASE)
    private val CREMA_ANN = Regex("""@(?:[\w.]+\.)?CremaTemplate\b""")
    private val OVERRIDE_ANN = Regex("""@Override\b""")
    private val KT_OVERRIDE = Regex("""\boverride\b""")
    private val TRIM_SUFFIX =
        Regex("""\s*\.\s*trim(?:Indent|Margin)\s*\(\s*(?:["'][^"']*["']\s*)?\)""")
    private val INLINE_BEFORE =
        Regex("""(?i)(?:\bBrewView\s*\.\s*)?\binline\s*\(\s*$""")
    private val CLASS_HEAD =
        Regex(
            """(?m)^[ \t]*(?:(?:public|protected|private|internal|open|abstract|static|final)\s+)*class\s+(\w+)\b([^\{]*)\{""",
        )

    internal fun isLanguageCrema(comment: String?): Boolean {
        if (comment.isNullOrBlank()) return false
        val body =
            comment
                .trim()
                .removePrefix("//")
                .removePrefix("/*")
                .removeSuffix("*/")
                .trim()
        return LANGUAGE_CREMA.containsMatchIn(body)
    }

    internal fun scanLiterals(source: CharSequence): List<Literal> {
        val out = mutableListOf<Literal>()
        var i = 0
        var lastComment: String? = null
        var lastCommentEnd = -1
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    val start = i
                    i = skipLine(source, i + 2)
                    lastComment = source.subSequence(start, i).toString()
                    lastCommentEnd = i
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    val start = i
                    i = skipBlockComment(source, i + 2)
                    lastComment = source.subSequence(start, i).toString()
                    lastCommentEnd = i
                }
                c == '\'' -> {
                    i = skipCharLiteral(source, i)
                }
                c == '"' -> {
                    val lit = parseString(source, i) ?: break
                    val comment =
                        if (lastCommentEnd >= 0 &&
                            commentApplies(source, lastCommentEnd, lit.quoteStart)
                        ) {
                            lastComment
                        } else {
                            null
                        }
                    out +=
                        Literal(
                            lit.quoteStart,
                            lit.quoteEnd,
                            lit.contentStart,
                            lit.contentEnd,
                            lit.textBlock,
                            comment,
                        )
                    i = lit.quoteEnd
                    lastComment = null
                    lastCommentEnd = -1
                }
                else -> i++
            }
        }
        return out
    }

    private fun commentApplies(source: CharSequence, commentEnd: Int, quoteStart: Int): Boolean {
        if (commentEnd < 0 || quoteStart < commentEnd) return false
        if (quoteStart - commentEnd > 280) return false
        val between = source.subSequence(commentEnd, quoteStart).toString()
        return BETWEEN_COMMENT_AND_STRING.matches(between)
    }

    internal fun parseString(source: CharSequence, start: Int): Literal? {
        if (start >= source.length || source[start] != '"') return null
        val textBlock =
            start + 2 < source.length && source[start + 1] == '"' && source[start + 2] == '"'
        if (textBlock) {
            val contentStart = start + 3
            var i = contentStart
            while (i + 2 < source.length) {
                if (source[i] == '"' && source[i + 1] == '"' && source[i + 2] == '"') {
                    return Literal(start, i + 3, contentStart, i, true, null)
                }
                i++
            }
            return Literal(start, source.length, contentStart, source.length, true, null)
        }
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i += 2
                '"' -> return Literal(start, i + 1, start + 1, i, false, null)
                '\n' -> return Literal(start, i, start + 1, i, false, null)
                else -> i++
            }
        }
        return Literal(start, source.length, start + 1, source.length, false, null)
    }

    internal fun findMethods(source: CharSequence): List<MethodSpan> {
        val out = mutableListOf<MethodSpan>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> i = skipLine(source, i + 2)
                c == '/' && i + 1 < source.length && source[i + 1] == '*' ->
                    i = skipBlockComment(source, i + 2)
                c == '\'' -> i = skipCharLiteral(source, i)
                c == '"' -> i = parseString(source, i)?.quoteEnd ?: source.length
                c == 'f' && matchIdent(source, i, "fun") -> {
                    val kt = parseKotlinFun(source, i)
                    if (kt != null) {
                        out += kt
                        i = maxOf(kt.bodyEnd, i + 3)
                    } else {
                        i += 3
                    }
                }
                c == '(' -> {
                    val java = parseJavaMethod(source, i)
                    if (java != null) {
                        out += java
                        i = maxOf(java.bodyEnd, i + 1)
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return out
    }

    private fun parseKotlinFun(source: CharSequence, funStart: Int): MethodSpan? {
        val header = headerBefore(source, funStart)
        var i = skipWs(source, funStart + 3)
        if (i >= source.length || !source[i].isJavaIdentifierStart()) return null
        val nameStart = i
        i = skipIdent(source, i)
        val name = source.subSequence(nameStart, i).toString()
        i = skipWs(source, i)
        if (i < source.length && source[i] == '<') {
            i = skipBalanced(source, i, '<', '>') + 1
            i = skipWs(source, i)
        }
        if (i >= source.length || source[i] != '(') return null
        val params = paramList(source, i) ?: return null
        i = skipWs(source, params.endExclusive)
        if (i < source.length && source[i] == ':') {
            i = skipType(source, i + 1)
            i = skipWs(source, i)
        }
        val crema = CREMA_ANN.containsMatchIn(header.text)
        val overridden = KT_OVERRIDE.containsMatchIn(header.text)
        val paramCrema = cremaParamIndexes(params.inner)
        return when {
            i < source.length && source[i] == '=' -> {
                val bodyStart = i + 1
                val bodyEnd = expressionBodyEnd(source, i)
                MethodSpan(name, header.start, bodyStart, bodyEnd, true, crema, overridden, paramCrema)
            }
            i < source.length && source[i] == '{' -> {
                val bodyEnd = skipBalanced(source, i, '{', '}') + 1
                MethodSpan(name, header.start, i, bodyEnd, false, crema, overridden, paramCrema)
            }
            else ->
                MethodSpan(
                    name,
                    header.start,
                    i.coerceAtMost(source.length),
                    i.coerceAtMost(source.length),
                    false,
                    crema,
                    overridden,
                    paramCrema,
                )
        }
    }

    private val JAVA_METHOD =
        Regex(
            """(?:(?:public|protected|private|static|final|native|synchronized|default|abstract)\s+)+""" +
                """[\w.<>,?\[\]\s]+\s+(\w+)\s*$""",
        )
    private val JAVA_SKIP =
        setOf("if", "for", "while", "switch", "catch", "synchronized", "return", "new", "throw")

    private fun parseJavaMethod(source: CharSequence, at: Int): MethodSpan? {
        if (at >= source.length || source[at] != '(') return null
        if (at == 0) return null
        var identEnd = at
        while (identEnd > 0 && source[identEnd - 1].isWhitespace()) identEnd--
        if (identEnd <= 0 || !source[identEnd - 1].isJavaIdentifierPart()) return null
        var identStart = identEnd - 1
        while (identStart > 0 && source[identStart - 1].isJavaIdentifierPart()) identStart--
        val name = source.subSequence(identStart, identEnd).toString()
        if (name in JAVA_SKIP || name.firstOrNull()?.isUpperCase() == true) return null
        val before = source.subSequence(0, identStart).toString()
        val lineStart = before.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val line = before.substring(lineStart)
        val lineWithName = source.subSequence(lineStart, identEnd).toString()
        if (!JAVA_METHOD.containsMatchIn(lineWithName) && !looksLikeJavaReturnType(line)) return null
        val header = headerBefore(source, identStart)
        val params = paramList(source, at) ?: return null
        var i = skipWs(source, params.endExclusive)
        if (i < source.length && source[i] == '{') {
            val bodyEnd = skipBalanced(source, i, '{', '}') + 1
            return MethodSpan(
                name = name,
                signatureStart = header.start,
                bodyStart = i,
                bodyEnd = bodyEnd,
                expressionBody = false,
                cremaAnnotated = CREMA_ANN.containsMatchIn(header.text) || CREMA_ANN.containsMatchIn(line),
                overridden = OVERRIDE_ANN.containsMatchIn(header.text) ||
                    OVERRIDE_ANN.containsMatchIn(line) ||
                    KT_OVERRIDE.containsMatchIn(header.text),
                paramCremaIndexes = cremaParamIndexes(params.inner),
            )
        }
        return null
    }

    private fun looksLikeJavaReturnType(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val last = trimmed.substringAfterLast(' ')
        if (trimmed in JAVA_SKIP || last in JAVA_SKIP) return false
        return Regex("""[\w.<>,?\[\]]+$""").matches(trimmed)
    }

    private data class Header(val start: Int, val text: String)

    private fun headerBefore(source: CharSequence, keywordStart: Int): Header {
        var i = keywordStart
        var start = keywordStart
        var lines = 0
        while (i > 0 && lines < 12) {
            i--
            if (source[i] == '\n') {
                lines++
                val lineStart = source.subSequence(0, i).toString().lastIndexOf('\n').let { p ->
                    if (p < 0) 0 else p + 1
                }
                val line = source.subSequence(lineStart, i).toString().trim()
                if (line.startsWith("@") ||
                    line.startsWith("override") ||
                    line.startsWith("public") ||
                    line.startsWith("protected") ||
                    line.startsWith("private") ||
                    line.startsWith("open") ||
                    line.startsWith("internal") ||
                    line.startsWith("actual") ||
                    line.startsWith("expect") ||
                    line.startsWith("suspend") ||
                    line.isEmpty()
                ) {
                    start = lineStart
                    continue
                }
                break
            }
        }
        return Header(start, source.subSequence(start, keywordStart).toString())
    }

    private data class Params(val inner: String, val endExclusive: Int)

    private fun paramList(source: CharSequence, openParen: Int): Params? {
        if (openParen >= source.length || source[openParen] != '(') return null
        val close = skipBalanced(source, openParen, '(', ')')
        if (close < 0) return null
        return Params(source.subSequence(openParen + 1, close).toString(), close + 1)
    }

    private fun cremaParamIndexes(inner: String): Set<Int> {
        if (inner.isBlank()) return emptySet()
        val indexes = mutableSetOf<Int>()
        val args = splitTopLevel(inner, ',')
        args.forEachIndexed { idx, arg ->
            if (CREMA_ANN.containsMatchIn(arg)) indexes += idx
        }
        return indexes
    }

    private fun annotatedParamCalls(source: CharSequence, methods: List<MethodSpan>): List<IntRange> {
        val byName = methods.filter { it.paramCremaIndexes.isNotEmpty() }
        if (byName.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        for (method in byName) {
            val pattern = Regex("""\b${Regex.escape(method.name)}\s*\(""")
            pattern.findAll(source).forEach { m ->
                if (m.range.first >= method.signatureStart && m.range.first < method.bodyEnd) return@forEach
                val open = m.range.last
                val params = paramList(source, open) ?: return@forEach
                val args = splitTopLevel(params.inner, ',')
                for (idx in method.paramCremaIndexes) {
                    val arg = args.getOrNull(idx) ?: continue
                    val local = arg.indexOf('"')
                    if (local < 0) continue
                    val abs = (open + 1) + args.take(idx).sumOf { it.length + 1 } + local
                    val lit = parseString(source, abs) ?: continue
                    ranges += lit.quoteStart until lit.quoteEnd
                }
            }
        }
        return ranges
    }

    private fun splitTopLevel(text: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var depth = 0
        var i = 0
        while (i < text.length) {
            when (val c = text[i]) {
                '"', '\'' -> {
                    val rest = text.subSequence(i, text.length)
                    val lit =
                        if (c == '"') {
                            parseString(rest, 0)
                        } else {
                            val end = skipCharLiteral(rest, 0)
                            Literal(0, end, 1, end.coerceAtMost(rest.length), false, null)
                        }
                    if (lit != null) {
                        buf.append(rest.subSequence(0, lit.quoteEnd))
                        i += lit.quoteEnd
                        continue
                    }
                }
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                sep -> if (depth == 0) {
                    out += buf.toString()
                    buf.clear()
                    i++
                    continue
                }
            }
            buf.append(text[i])
            i++
        }
        out += buf.toString()
        return out
    }

    private fun expressionBodyEnd(source: CharSequence, eq: Int): Int {
        var i = skipWs(source, eq + 1)
        if (i < source.length && source[i] == '"') {
            val lit = parseString(source, i) ?: return source.length
            i = lit.quoteEnd
            val rest = source.subSequence(i, source.length)
            val trim = TRIM_SUFFIX.find(rest)
            if (trim != null && trim.range.first == 0) i += trim.value.length
            return i
        }
        while (i < source.length && source[i] != '\n' && source[i] != '}') i++
        return i
    }

    private fun isReturnOrExpressionLiteral(
        source: CharSequence,
        method: MethodSpan,
        lit: Literal,
    ): Boolean {
        if (method.expressionBody) {
            val afterEq = skipWs(source, method.bodyStart)
            return lit.quoteStart == afterEq ||
                (afterEq < source.length && source[afterEq] == '(' && lit.quoteStart > afterEq)
        }
        return isReturnLiteral(source, method.bodyStart, method.bodyEnd, lit.quoteStart)
    }

    private fun isReturnLiteral(
        source: CharSequence,
        bodyStart: Int,
        bodyEnd: Int,
        quoteStart: Int,
    ): Boolean {
        var i = bodyStart
        var depth = 0
        while (i < quoteStart && i < bodyEnd) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> i = skipLine(source, i + 2)
                c == '/' && i + 1 < source.length && source[i + 1] == '*' ->
                    i = skipBlockComment(source, i + 2)
                c == '"' -> i = parseString(source, i)?.quoteEnd ?: bodyEnd
                c == '\'' -> i = skipCharLiteral(source, i)
                c == '{' -> {
                    depth++
                    i++
                }
                c == '}' -> {
                    depth--
                    i++
                }
                depth <= 1 && matchIdent(source, i, "return") -> {
                    var j = skipWs(source, i + 6)
                    while (j < quoteStart && source[j] == '(') j = skipWs(source, j + 1)
                    return j == quoteStart
                }
                else -> i++
            }
        }
        return false
    }

    private fun isTemplateOverride(method: MethodSpan, componentRanges: List<IntRange>): Boolean {
        if (method.name != "template") return false
        if (method.overridden) return true
        return componentRanges.any { method.signatureStart in it }
    }

    internal fun componentClassRanges(source: CharSequence): List<IntRange> {
        val out = mutableListOf<IntRange>()
        CLASS_HEAD.findAll(source).forEach { m ->
            val clause = m.groupValues[2]
            val isComponent =
                Regex(""":\s*Component\b""").containsMatchIn(clause) ||
                    Regex("""\bextends\s+Component\b""").containsMatchIn(clause)
            if (!isComponent) return@forEach
            val open = m.range.last
            val close = skipBalanced(source, open, '{', '}')
            if (close >= 0) out += m.range.first..close
        }
        return out
    }

    private fun inlineCallArgument(source: CharSequence, quoteStart: Int): Boolean {
        val before = source.subSequence(0, quoteStart).toString()
        val window = before.takeLast(80)
        return INLINE_BEFORE.containsMatchIn(window)
    }

    private fun expressionAnnotated(source: CharSequence, quoteStart: Int): Boolean {
        var i = quoteStart
        while (i > 0 && source[i - 1].isWhitespace()) i--
        if (i <= 0) return false
        val before = source.subSequence(0, i)
        val window = before.takeLast(80).toString()
        return Regex("""@(?:[\w.]+\.)?CremaTemplate$""").containsMatchIn(window)
    }

    private fun skipWs(source: CharSequence, start: Int): Int {
        var i = start
        while (i < source.length && source[i].isWhitespace()) i++
        return i
    }

    private fun skipLine(source: CharSequence, start: Int): Int {
        var i = start
        while (i < source.length && source[i] != '\n') i++
        if (i < source.length) i++
        return i
    }

    private fun skipBlockComment(source: CharSequence, start: Int): Int {
        var i = start
        while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) i++
        return if (i + 1 < source.length) i + 2 else source.length
    }

    private fun skipCharLiteral(source: CharSequence, start: Int): Int {
        var i = start + 1
        if (i < source.length && source[i] == '\\') i += 2 else i++
        if (i < source.length && source[i] == '\'') i++
        return i
    }

    private fun skipIdent(source: CharSequence, start: Int): Int {
        var i = start
        while (i < source.length && source[i].isJavaIdentifierPart()) i++
        return i
    }

    private fun skipType(source: CharSequence, start: Int): Int {
        var i = skipWs(source, start)
        while (i < source.length) {
            val c = source[i]
            when {
                c.isJavaIdentifierPart() || c == '.' || c == '?' || c == '*' -> i++
                c == '<' -> i = skipBalanced(source, i, '<', '>') + 1
                else -> break
            }
        }
        return i
    }

    private fun matchIdent(source: CharSequence, start: Int, ident: String): Boolean {
        if (start + ident.length > source.length) return false
        for (k in ident.indices) {
            if (source[start + k] != ident[k]) return false
        }
        val after = start + ident.length
        val beforeOk = start == 0 || !source[start - 1].isJavaIdentifierPart()
        val afterOk = after >= source.length || !source[after].isJavaIdentifierPart()
        return beforeOk && afterOk
    }

    private fun skipBalanced(source: CharSequence, open: Int, openCh: Char, closeCh: Char): Int {
        if (open >= source.length || source[open] != openCh) return -1
        var depth = 0
        var i = open
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> i = skipLine(source, i + 2)
                c == '/' && i + 1 < source.length && source[i + 1] == '*' ->
                    i = skipBlockComment(source, i + 2)
                c == '"' -> i = parseString(source, i)?.quoteEnd ?: source.length
                c == '\'' -> i = skipCharLiteral(source, i)
                c == openCh -> {
                    depth++
                    i++
                }
                c == closeCh -> {
                    depth--
                    i++
                    if (depth == 0) return i - 1
                }
                else -> i++
            }
        }
        return source.length - 1
    }
}

/**
 * File/language checks shared by completion, navigation, and annotators.
 * Injected fragments are Crema when [injected] is true and the fragment language is Crema,
 * even if the host file is Java/Kotlin.
 */
object CremaContexts {
    const val LANGUAGE_ID: String = "Crema"

    fun isCremaLanguageId(id: String?): Boolean =
        !id.isNullOrBlank() && id.equals(LANGUAGE_ID, ignoreCase = true)

    fun looksLikeCrema(
        fileName: String,
        languageId: String? = null,
        baseLanguageId: String? = null,
        injected: Boolean = false,
        injectedLanguageId: String? = null,
    ): Boolean {
        if (CremaFileType.isCremaFileName(fileName)) return true
        if (isCremaLanguageId(languageId) || isCremaLanguageId(baseLanguageId)) return true
        if (injected && isCremaLanguageId(injectedLanguageId ?: languageId)) return true
        return false
    }
}

/**
 * Resolve the Brew component that owns a Crema buffer: a views/brew convention
 * pairing, or the host Java/Kotlin Component class for an injected `template()`.
 */
object BrewInjectionOwners {
    fun ownerFor(
        projectRoot: java.nio.file.Path,
        hostPath: java.nio.file.Path?,
        hostSource: String? = null,
        hostOffset: Int = 0,
    ): BrewComponentIndex.ComponentInfo? {
        if (hostPath != null) {
            BrewComponentIndex.ownerForViewFile(projectRoot, hostPath)?.let { return it }
        }
        val source =
            hostSource
                ?: hostPath
                    ?.takeIf { java.nio.file.Files.isRegularFile(it) }
                    ?.let { runCatching { java.nio.file.Files.readString(it) }.getOrNull() }
                ?: return null
        val parsed = BrewComponentIndex.parseSource(source, hostPath)
        if (parsed.isEmpty()) return null
        if (parsed.size == 1) return parsed[0]
        val ranked =
            parsed.map { info ->
                val start =
                    Regex("""\bclass\s+${Regex.escape(info.className)}\b""")
                        .find(source)?.range?.first
                        ?: BrewComponentIndex.offsetOfClass(source, info.className)
                        ?: 0
                info to start
            }
        return ranked
            .filter { (_, start) -> hostOffset >= start }
            .maxByOrNull { it.second }
            ?.first
            ?: parsed.first()
    }
}
