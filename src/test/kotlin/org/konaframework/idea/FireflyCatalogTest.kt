package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class FireflyCatalogTest {
    @Test
    fun catalogListsShippedTypes() {
        val types = FireflyCatalog.types()
        assertTrue(types.any { it.name == "Panel" && it.kind == "panel" })
        assertTrue(types.any { it.name == "Resource" && it.kind == "resource" })
        assertTrue(types.any { it.name == "Page" && it.kind == "page" })
        assertTrue(types.any { it.name == "ChartWidget" && it.kind == "widget" && it.methods.contains("engine") })
        assertEquals("", FireflyCatalog.text(null))
        assertTrue(FireflyCatalog.parse("").isEmpty())
        assertTrue(FireflyCatalog.parse("{\"types\":[{\"name\":\"\"}]}").isEmpty())
        val bare = FireflyCatalog.parse("{\"types\":[{\"name\":\"Desk\"}]}")
        assertEquals("Desk", bare[0].name)
        assertEquals("", bare[0].packageName)
        assertTrue(bare[0].methods.isEmpty())
        val noted = FireflyCatalog.parse("{\"types\":[{\"name\":\"Note\",\"methods\":[\"\",\"id\"]}]}")
        assertEquals(listOf("id"), noted[0].methods)
        val unclosed = FireflyCatalog.parse("{\"types\":[{\"name\":\"Open\",\"methods\":[\"id\"}")
        assertTrue(unclosed[0].methods.isEmpty())
        val nobracket = FireflyCatalog.parse("{\"types\":[{\"name\":\"Plain\",\"methods\":}]}")
        assertTrue(nobracket[0].methods.isEmpty())
        val half = FireflyCatalog.parse("{\"types\":[{\"name\":\"Half\",\"methods\":[\"id]}]]}")
        assertTrue(half[0].methods.isEmpty())
        assertTrue(FireflyCatalog.parse("{\"types\":[{\"name\":\"abc}]}").isEmpty())
        assertEquals("", FireflyCatalog.parse("{\"types\":[{\"name\":}]}").singleOrNull()?.name ?: "")
    }

    @Test
    fun completesTypesAndMethods() {
        assertTrue(FireflyCatalog.completions("Pan", 3).any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions("zzz", 3).isEmpty())
        assertTrue(FireflyCatalog.completions("", 0).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel().pa", "Panel().pa".length).any { it.label == "path" })
        assertTrue(FireflyCatalog.completions("ChartWidget.", "ChartWidget.".length).any { it.label == "engine" })
        assertTrue(FireflyCatalog.completions("ChartWidget.zzz", "ChartWidget.zzz".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Missing.", "Missing.".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel).", "Panel).".length).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel((x)).", "Panel((x)).".length).any { it.label == "id" })
        assertTrue(FireflyCatalog.completions(" .", 2).isEmpty())
        assertTrue(FireflyCatalog.completions("Panel()\t\n .", "Panel()\t\n .".length).any { it.label == "id" })
        assertTrue(FireflyCatalog.completions("{Panel", "{Panel".length).any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions(":Panel", ":Panel".length).any { it.label == "Panel" })
        assertTrue(FireflyCatalog.completions("@A1_b", "@A1_b".length).isEmpty())
        val bytes = "{\"types\":[{\"name\":\"Only\"}]}".toByteArray()
        assertTrue(FireflyCatalog.text(ByteArrayInputStream(bytes)).contains("Only"))
    }
}
