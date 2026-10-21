package org.konaframework.idea

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.util.Key

/**
 * Forces Kona suggestions to the top of the lookup list.
 *
 * IntelliJ contributor `order="first"` only affects *when* variants are added.
 * Sorting uses [CompletionWeigher]s: this object's weigher (before `priority` /
 * `prefix` / `stats` / `explicitProximity`), then those platform weighers.
 * Items added after the lookup *freezes* stay at the bottom — so callers
 * should [addAll] / [offer] in one batch.
 *
 * Priority stays in the same order of magnitude as 0.5.12 (`1000`). A value
 * like `1_000_000` overflowed some platform/ML ranking weights to negative
 * ints and dropped the items from the list.
 */
object KonaLookup {
    /** High enough to beat typical Java/Kotlin member priorities (~0–100). */
    const val PRIORITY: Double = 1_000.0

    /** Secondary rank via the `explicitProximity` weigher. */
    const val PROXIMITY: Int = 1_000

    val MARKER: Key<Boolean> = Key.create("kona.lookup")

    enum class Rank {
        OTHER,
        KONA,
    }

    fun wrap(element: LookupElement): LookupElement {
        element.putUserData(MARKER, true)
        // Priority must be the outermost wrap: the `priority` weigher reads the
        // first PrioritizedLookupElement, and a proximity wrap reports 0.
        var out: LookupElement = PrioritizedLookupElement.withExplicitProximity(element, PROXIMITY)
        out = PrioritizedLookupElement.withPriority(out, PRIORITY)
        out.putUserData(MARKER, true)
        return out
    }

    fun add(sink: (LookupElement) -> Unit, element: LookupElement) {
        sink(wrap(element))
    }

    /**
     * Rank every item and strip dummy identifiers from [prefix] so the prefix
     * weigher treats Kona rows as matches. Callers must [publish] the batch
     * through `CompletionResultSet.addAllElements` (one-by-one add after freeze
     * pins items at the bottom of the list).
     */
    fun addAll(
        prefix: String,
        elements: Collection<LookupElement>,
        publish: (matcherPrefix: String, wrapped: List<LookupElement>) -> Unit,
    ) {
        if (elements.isEmpty()) return
        publish(CompletionText.effectivePrefix(prefix), elements.map { wrap(it) })
    }

    fun offer(
        prefix: String,
        element: LookupElement,
        publish: (matcherPrefix: String, wrapped: List<LookupElement>) -> Unit,
    ) {
        addAll(prefix, listOf(element), publish)
    }

    fun isKona(element: LookupElement): Boolean {
        var current: LookupElement? = element
        var hops = 0
        while (current != null && hops < 16) {
            if (current.getUserData(MARKER) == true) return true
            current = (current as? LookupElementDecorator<*>)?.delegate
            hops++
        }
        return false
    }

    /** Primary weigher comparable: Kona items sort above everything else. */
    fun weighScore(element: LookupElement): Rank =
        if (isKona(element)) Rank.KONA else Rank.OTHER

    fun priorityOf(element: LookupElement): Double? {
        var current: LookupElement? = element
        var hops = 0
        while (current != null && hops < 16) {
            val prioritized = current as? PrioritizedLookupElement<*>
            if (prioritized != null) return prioritized.priority
            current = (current as? LookupElementDecorator<*>)?.delegate
            hops++
        }
        return null
    }

    fun proximityOf(element: LookupElement): Int? {
        var current: LookupElement? = element
        var hops = 0
        while (current != null && hops < 16) {
            val prioritized = current as? PrioritizedLookupElement<*>
            if (prioritized != null && prioritized.explicitProximity != 0) {
                return prioritized.explicitProximity
            }
            current = (current as? LookupElementDecorator<*>)?.delegate
            hops++
        }
        return null
    }
}
