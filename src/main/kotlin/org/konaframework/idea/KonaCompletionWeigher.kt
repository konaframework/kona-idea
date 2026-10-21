package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Completion weigher registered `order="first, before:priority, before:prefix"`
 * so Kona variants outrank language-plugin members. [KonaLookup.Rank.KONA] is
 * greater than [KonaLookup.Rank.OTHER], so the sorter keeps Kona rows at the
 * top of the lookup list.
 */
class KonaCompletionWeigher : CompletionWeigher() {
    override fun weigh(
        element: LookupElement,
        location: CompletionLocation,
    ): Comparable<*> =
        runCatching { KonaLookup.weighScore(element) }.getOrDefault(KonaLookup.Rank.OTHER)
}
