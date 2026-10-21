package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Completion weigher registered `order="first"` so Kona variants outrank
 * language-plugin members even when [PrioritizedLookupElement] is not enough.
 */
class KonaCompletionWeigher : CompletionWeigher() {
    override fun weigh(
        element: LookupElement,
        location: CompletionLocation,
    ): Comparable<*> = runCatching { KonaLookup.weighScore(element) }.getOrDefault(0)
}
