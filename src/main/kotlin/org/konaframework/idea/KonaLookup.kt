package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.util.Key

/**
 * Forces Kona suggestions to the top of the lookup list.
 *
 * IntelliJ contributor `order="first"` only affects *when* variants are added,
 * not sort order versus Java/Kotlin members. Every Kona [LookupElement] is
 * wrapped with [PrioritizedLookupElement] at [PRIORITY] and marked with
 * [MARKER] so [KonaCompletionWeigher] (`order="first"`) can boost them again.
 */
object KonaLookup {
    /** High enough to beat typical Java/Kotlin member priorities (~0–1000). */
    const val PRIORITY: Double = 1_000_000.0

    val MARKER: Key<Boolean> = Key.create("kona.lookup")

    fun wrap(element: LookupElement): LookupElement {
        element.putUserData(MARKER, true)
        val prioritized = PrioritizedLookupElement.withPriority(element, PRIORITY)
        prioritized.putUserData(MARKER, true)
        return prioritized
    }

    fun add(result: CompletionResultSet, element: LookupElement) {
        result.addElement(wrap(element))
    }

    fun isKona(element: LookupElement): Boolean {
        var current: LookupElement? = element
        while (current != null) {
            if (current.getUserData(MARKER) == true) return true
            val prioritized = current as? PrioritizedLookupElement<*>
            if (prioritized != null && prioritized.priority >= PRIORITY) return true
            current = (current as? LookupElementDecorator<*>)?.delegate
        }
        return false
    }

    /** Primary weigher comparable: Kona items sort above everything else. */
    fun weighScore(element: LookupElement): Int = if (isKona(element)) 1 else 0

    fun priorityOf(element: LookupElement): Double? {
        var current: LookupElement? = element
        while (current != null) {
            val prioritized = current as? PrioritizedLookupElement<*>
            if (prioritized != null) return prioritized.priority
            current = (current as? LookupElementDecorator<*>)?.delegate
        }
        return null
    }
}
