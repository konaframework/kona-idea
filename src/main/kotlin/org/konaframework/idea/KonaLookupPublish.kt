package org.konaframework.idea

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement

/** Platform wiring: batch-publish ranked Kona lookups onto a result set. */
internal fun CompletionResultSet.addKona(
    prefix: String,
    elements: Collection<LookupElement>,
) {
    KonaLookup.addAll(prefix, elements) { p, wrapped ->
        withPrefixMatcher(PlainPrefixMatcher(p)).addAllElements(wrapped)
    }
}

internal fun CompletionResultSet.offerKona(
    prefix: String,
    element: LookupElement,
) {
    addKona(prefix, listOf(element))
}
