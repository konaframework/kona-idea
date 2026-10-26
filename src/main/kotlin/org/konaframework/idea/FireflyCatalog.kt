package org.konaframework.idea

import java.io.InputStream

/**
 * Panel, resource, page, and widget names from the generated Firefly catalog.
 */
object FireflyCatalog {
    data class Item(
        val label: String,
        val detail: String,
    )

    data class TypeRecord(
        val name: String,
        val packageName: String,
        val kind: String,
        val methods: List<String>,
    )

    @JvmStatic
    fun types(): List<TypeRecord> = parse(text(FireflyCatalog::class.java.getResourceAsStream("firefly-catalog.json")))

    @JvmStatic
    fun text(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        val bytes = stream.readBytes()
        stream.close()
        return String(bytes, Charsets.UTF_8)
    }

    @JvmStatic
    fun parse(body: String): List<TypeRecord> {
        if (!body.contains("\"types\"")) {
            return emptyList()
        }
        val out = ArrayList<TypeRecord>()
        val chunks = body.split("\"name\"")
        var index = 1
        while (index < chunks.size) {
            val chunk = chunks[index]
            val name = quoted(chunk)
            if (name.isNotEmpty()) {
                out.add(
                    TypeRecord(
                        name = name,
                        packageName = field(chunk, "package"),
                        kind = field(chunk, "kind"),
                        methods = methods(chunk),
                    ),
                )
            }
            index = index + 1
        }
        return out
    }

    @JvmStatic
    fun completions(text: String, offset: Int): List<Item> {
        val types = types()
        val head = text.substring(0, offset.coerceIn(0, text.length))
        val prefix = trailingIdent(head)
        val before = head.substring(0, head.length - prefix.length)
        val receiver = receiverName(before)
        if (receiver != null) {
            return methodItems(receiver, prefix, types)
        }
        if (endsWithDot(before)) {
            return emptyList()
        }
        if (prefix.isEmpty()) {
            return emptyList()
        }
        val needle = prefix.lowercase()
        val items = ArrayList<Item>()
        for (type in types) {
            if (type.name.lowercase().startsWith(needle)) {
                items.add(Item(type.name, type.packageName + " · " + type.kind))
            }
        }
        return items
    }

    private fun methodItems(
        receiver: String,
        prefix: String,
        types: List<TypeRecord>,
    ): List<Item> {
        val type = find(receiver, types)
        if (type == null) {
            return emptyList()
        }
        val needle = prefix.lowercase()
        val items = ArrayList<Item>()
        for (method in type.methods) {
            val matches =
                if (needle.isEmpty()) {
                    true
                } else {
                    method.lowercase().startsWith(needle)
                }
            if (matches) {
                items.add(Item(method, type.name + "." + method + "()"))
            }
        }
        return items
    }

    private fun find(name: String, types: List<TypeRecord>): TypeRecord? {
        for (type in types) {
            if (type.name == name) {
                return type
            }
        }
        return null
    }

    private fun receiverName(before: String): String? {
        val trimmed = trimTail(before)
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed[trimmed.length - 1] != '.') {
            return null
        }
        var left = trimTail(trimmed.substring(0, trimmed.length - 1))
        if (left.isNotEmpty() && left[left.length - 1] == ')') {
            val cut = skipCall(left)
            if (cut < 0) {
                return null
            }
            left = left.substring(0, cut)
        }
        val name = trailingIdent(left)
        if (name.isEmpty()) {
            return null
        }
        return name
    }

    private fun skipCall(value: String): Int {
        var depth = 0
        var index = value.length - 1
        while (index >= 0) {
            val ch = value[index]
            if (ch == ')') {
                depth = depth + 1
            } else if (ch == '(') {
                depth = depth - 1
                if (depth == 0) {
                    return index
                }
            }
            index = index - 1
        }
        return -1
    }

    private fun endsWithDot(before: String): Boolean {
        val trimmed = trimTail(before)
        if (trimmed.isEmpty()) {
            return false
        }
        return trimmed[trimmed.length - 1] == '.'
    }

    private fun quoted(chunk: String): String {
        val start = chunk.indexOf('"')
        if (start < 0) {
            return ""
        }
        val end = chunk.indexOf('"', start + 1)
        if (end < 0) {
            return ""
        }
        return chunk.substring(start + 1, end)
    }

    private fun field(chunk: String, key: String): String {
        val marker = "\"$key\""
        val at = chunk.indexOf(marker)
        if (at < 0) {
            return ""
        }
        return quoted(chunk.substring(at + marker.length))
    }

    private fun methods(chunk: String): List<String> {
        val at = chunk.indexOf("\"methods\"")
        if (at < 0) {
            return emptyList()
        }
        val open = chunk.indexOf('[', at)
        if (open < 0) {
            return emptyList()
        }
        val close = chunk.indexOf(']', open)
        if (close < 0) {
            return emptyList()
        }
        val body = chunk.substring(open + 1, close)
        val names = ArrayList<String>()
        var cursor = 0
        while (cursor < body.length) {
            val start = body.indexOf('"', cursor)
            if (start < 0) {
                break
            }
            val end = body.indexOf('"', start + 1)
            if (end < 0) {
                break
            }
            val name = body.substring(start + 1, end)
            if (name.isNotEmpty()) {
                names.add(name)
            }
            cursor = end + 1
        }
        return names
    }

    private fun trailingIdent(value: String): String {
        var index = value.length
        while (index > 0 && ident(value[index - 1])) {
            index = index - 1
        }
        return value.substring(index)
    }

    private fun trimTail(value: String): String {
        var end = value.length
        while (end > 0) {
            val ch = value[end - 1]
            if (ch != ' ' && ch != '\n' && ch != '\t') {
                break
            }
            end = end - 1
        }
        return value.substring(0, end)
    }

    private fun ident(ch: Char): Boolean {
        if (ch >= 'a' && ch <= 'z') {
            return true
        }
        if (ch >= 'A' && ch <= 'Z') {
            return true
        }
        if (ch >= '0' && ch <= '9') {
            return true
        }
        return ch == '_'
    }
}
