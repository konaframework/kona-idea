package org.konaframework.idea

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KonaLookupTest {
    @Test
    fun wrapUsesPrioritizedLookupElementAndMarker() {
        val raw = testLookup("email")
        val wrapped = KonaLookup.wrap(raw)
        assertTrue(wrapped is PrioritizedLookupElement<*>)
        assertEquals(KonaLookup.PRIORITY, KonaLookup.priorityOf(wrapped)!!, 0.0)
        assertTrue(KonaLookup.isKona(wrapped))
        assertEquals(1, KonaLookup.weighScore(wrapped))
        assertEquals("email", wrapped.lookupString)
        assertEquals(true, wrapped.getUserData(KonaLookup.MARKER))
        assertTrue(raw.getUserData(KonaLookup.MARKER) == true)
    }

    @Test
    fun weighScoreIsZeroForLanguageMembers() {
        val method = testLookup("toString")
        assertFalse(KonaLookup.isKona(method))
        assertEquals(0, KonaLookup.weighScore(method))
        assertNull(KonaLookup.priorityOf(method))
    }

    @Test
    fun doubleWrapStaysKona() {
        val once = KonaLookup.wrap(testLookup("name"))
        val twice = KonaLookup.wrap(once)
        assertTrue(KonaLookup.isKona(twice))
        assertEquals(KonaLookup.PRIORITY, KonaLookup.priorityOf(twice)!!, 0.0)
        assertEquals(1, KonaLookup.weighScore(twice))
    }

    @Test
    fun highPriorityWithoutMarkerStillCounts() {
        val raw = testLookup("email")
        val prioritized = PrioritizedLookupElement.withPriority(raw, KonaLookup.PRIORITY)
        assertTrue(KonaLookup.isKona(prioritized))
        assertEquals(1, KonaLookup.weighScore(prioritized))
        val low = PrioritizedLookupElement.withPriority(testLookup("toString"), 10.0)
        assertFalse(KonaLookup.isKona(low))
        assertEquals(0, KonaLookup.weighScore(low))
        assertEquals(10.0, KonaLookup.priorityOf(low)!!, 0.0)
    }

    private fun testLookup(name: String): LookupElement =
        object : LookupElement() {
            override fun getLookupString(): String = name
        }
}
