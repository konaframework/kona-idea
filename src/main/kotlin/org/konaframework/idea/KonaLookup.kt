package org.konaframework.idea

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
 *
 * Priority stays in the same order of magnitude as 0.5.12 (`1000`). A value
 * like `1_000_000` overflowed some platform/ML ranking weights to negative
 * ints and dropped the items from the list — which is how Cadence column
 * completion disappeared after the “Kona first” ranking change.
 */
object KonaLookup {
    /** High enough to beat typical Java/Kotlin member priorities (~0–100). */
    const val PRIORITY: Double = 1_000.0

    val MARKER: Key<Boolean> = Key.create("kona.lookup")

    fun wrap(element: LookupElement): LookupElement {
        element.putUserData(MARKER, true)
        val prioritized = PrioritizedLookupElement.withPriority(element, PRIORITY)
        prioritized.putUserData(MARKER, true)
        return prioritized
    }

    fun add(sink: (LookupElement) -> Unit, element: LookupElement) {
        sink(wrap(element))
    }

    fun isKona(element: LookupElement): Boolean {
        var current: LookupElement? = element
        var hops = 0
        while (current != null && hops < 8) {
            if (current.getUserData(MARKER) == true) return true
            current = (current as? LookupElementDecorator<*>)?.delegate
            hops++
        }
        return false
    }

    /** Primary weigher comparable: Kona items sort above everything else. */
    fun weighScore(element: LookupElement): Int = if (isKona(element)) 1 else 0

    fun priorityOf(element: LookupElement): Double? {
        var current: LookupElement? = element
        var hops = 0
        while (current != null && hops < 8) {
            val prioritized = current as? PrioritizedLookupElement<*>
            if (prioritized != null) return prioritized.priority
            current = (current as? LookupElementDecorator<*>)?.delegate
            hops++
        }
        return null
    }
}
