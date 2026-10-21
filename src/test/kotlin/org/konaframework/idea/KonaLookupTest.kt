package org.konaframework.idea

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
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
        assertEquals(KonaLookup.PROXIMITY, KonaLookup.proximityOf(wrapped)!!)
        assertTrue(KonaLookup.isKona(wrapped))
        assertEquals(KonaLookup.Rank.KONA, KonaLookup.weighScore(wrapped))
        assertEquals("email", wrapped.lookupString)
        assertEquals(true, wrapped.getUserData(KonaLookup.MARKER))
        assertTrue(raw.getUserData(KonaLookup.MARKER) == true)
        assertTrue(KonaLookup.Rank.KONA > KonaLookup.Rank.OTHER)
    }

    @Test
    fun weighScoreIsOtherForLanguageMembers() {
        val method = testLookup("toString")
        assertFalse(KonaLookup.isKona(method))
        assertEquals(KonaLookup.Rank.OTHER, KonaLookup.weighScore(method))
        assertNull(KonaLookup.priorityOf(method))
        assertNull(KonaLookup.proximityOf(method))
    }

    @Test
    fun doubleWrapStaysKona() {
        val once = KonaLookup.wrap(testLookup("name"))
        val twice = KonaLookup.wrap(once)
        assertTrue(KonaLookup.isKona(twice))
        assertEquals(KonaLookup.PRIORITY, KonaLookup.priorityOf(twice)!!, 0.0)
        assertEquals(KonaLookup.PROXIMITY, KonaLookup.proximityOf(twice)!!)
        assertEquals(KonaLookup.Rank.KONA, KonaLookup.weighScore(twice))
    }

    @Test
    fun rankingUsesMarkerNotHugePriority() {
        val raw = testLookup("email")
        val prioritized = PrioritizedLookupElement.withPriority(raw, 1_000_000.0)
        assertFalse(KonaLookup.isKona(prioritized))
        assertEquals(KonaLookup.Rank.OTHER, KonaLookup.weighScore(prioritized))
        assertEquals(1_000_000.0, KonaLookup.priorityOf(prioritized)!!, 0.0)
        assertNull(KonaLookup.proximityOf(prioritized))
        val low = PrioritizedLookupElement.withPriority(testLookup("toString"), 10.0)
        assertFalse(KonaLookup.isKona(low))
        assertEquals(KonaLookup.Rank.OTHER, KonaLookup.weighScore(low))
        assertEquals(10.0, KonaLookup.priorityOf(low)!!, 0.0)
        assertEquals(1_000.0, KonaLookup.PRIORITY, 0.0)
        assertEquals(1_000, KonaLookup.PROXIMITY)
    }

    @Test
    fun unmarkedDecoratorChainIsNotKona() {
        var el: LookupElement = testLookup("toString")
        repeat(10) { el = PrioritizedLookupElement.withPriority(el, 1.0) }
        assertFalse(KonaLookup.isKona(el))
        assertEquals(KonaLookup.Rank.OTHER, KonaLookup.weighScore(el))
        assertEquals(1.0, KonaLookup.priorityOf(el)!!, 0.0)
        assertNull(KonaLookup.proximityOf(el))
    }

    @Test
    fun hopLimitStopsWalkingDecorators() {
        var el: LookupElement = testLookup("toString")
        repeat(20) { el = Chain(el) }
        assertFalse(KonaLookup.isKona(el))
        assertNull(KonaLookup.priorityOf(el))
        assertNull(KonaLookup.proximityOf(el))
        assertEquals(KonaLookup.Rank.OTHER, KonaLookup.weighScore(el))
    }

    @Test
    fun addSinksWrappedElement() {
        val items = mutableListOf<LookupElement>()
        KonaLookup.add(items::add, testLookup("email"))
        assertEquals(1, items.size)
        assertEquals("email", items.single().lookupString)
        assertTrue(KonaLookup.isKona(items.single()))
        assertEquals(KonaLookup.PRIORITY, KonaLookup.priorityOf(items.single())!!, 0.0)
        assertEquals(KonaLookup.PROXIMITY, KonaLookup.proximityOf(items.single())!!)
    }

    @Test
    fun addAllPublishesWrappedBatchWithStrippedPrefix() {
        val captured = mutableListOf<LookupElement>()
        var matcherPrefix = "unset"
        KonaLookup.addAll(
            "em" + CompletionText.DUMMY,
            listOf(testLookup("email"), testLookup("name")),
        ) { prefix, wrapped ->
            matcherPrefix = prefix
            captured.addAll(wrapped)
        }
        assertEquals("em", matcherPrefix)
        assertEquals(2, captured.size)
        assertTrue(captured.all { KonaLookup.isKona(it) })
        assertTrue(captured.all { KonaLookup.priorityOf(it) == KonaLookup.PRIORITY })
        assertTrue(captured.all { KonaLookup.proximityOf(it) == KonaLookup.PROXIMITY })
        assertEquals(listOf("email", "name"), captured.map { it.lookupString })
    }

    @Test
    fun addAllSkipsEmptyAndOfferWrapsOne() {
        var called = false
        KonaLookup.addAll("x", emptyList()) { _, _ -> called = true }
        assertFalse(called)

        val items = mutableListOf<LookupElement>()
        var prefix = "unset"
        KonaLookup.offer("", testLookup("email")) { p, wrapped ->
            prefix = p
            items.addAll(wrapped)
        }
        assertEquals("", prefix)
        assertEquals(1, items.size)
        assertTrue(KonaLookup.isKona(items.single()))
        assertEquals(KonaLookup.Rank.KONA, KonaLookup.weighScore(items.single()))
    }

    @Test
    fun wrapPreservesLookupElementBuilderString() {
        val raw =
            com.intellij.codeInsight.lookup.LookupElementBuilder.create("email")
                .withTypeText("column · users", true)
        val wrapped = KonaLookup.wrap(raw)
        assertEquals("email", wrapped.lookupString)
        assertTrue(KonaLookup.isKona(wrapped))
        assertEquals(KonaLookup.Rank.KONA, KonaLookup.weighScore(wrapped))
        assertEquals(KonaLookup.PRIORITY, KonaLookup.priorityOf(wrapped)!!, 0.0)
        assertEquals(KonaLookup.PROXIMITY, KonaLookup.proximityOf(wrapped)!!)
    }

    private fun testLookup(name: String): LookupElement =
        object : LookupElement() {
            override fun getLookupString(): String = name
        }

    private class Chain(delegate: LookupElement) : LookupElementDecorator<LookupElement>(delegate)
}
