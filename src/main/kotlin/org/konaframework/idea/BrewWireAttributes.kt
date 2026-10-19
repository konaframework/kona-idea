package org.konaframework.idea

/**
 * Known Brew `wire:*` HTML attributes (Livewire-shaped) used in Crema views.
 * Keep aligned with `js/brew` directive names.
 */
object BrewWireAttributes {
    /** Base attribute names (no modifiers). */
    val BASE: List<String> =
        listOf(
            "wire:model",
            "wire:click",
            "wire:submit",
            "wire:loading",
            "wire:target",
            "wire:ignore",
            "wire:key",
            "wire:dirty",
            "wire:offline",
            "wire:confirm",
            "wire:poll",
            "wire:init",
            "wire:show",
            "wire:transition",
            "wire:text",
            "wire:ref",
            "wire:id",
            "wire:name",
            "wire:snapshot",
            "wire:effects",
        )

    /** Common completion labels including frequent modifier variants. */
    val COMPLETION_LABELS: List<String> =
        listOf(
            "wire:model",
            "wire:model.live",
            "wire:model.blur",
            "wire:model.lazy",
            "wire:model.defer",
            "wire:click",
            "wire:click.prevent",
            "wire:click.stop",
            "wire:click.async",
            "wire:click.renderless",
            "wire:click.preserve-scroll",
            "wire:submit",
            "wire:submit.prevent",
            "wire:loading",
            "wire:loading.class",
            "wire:loading.attr",
            "wire:loading.remove",
            "wire:loading.flex",
            "wire:target",
            "wire:target.except",
            "wire:ignore",
            "wire:ignore.self",
            "wire:key",
            "wire:dirty",
            "wire:dirty.class",
            "wire:dirty.remove",
            "wire:offline",
            "wire:offline.class",
            "wire:offline.attr",
            "wire:offline.remove",
            "wire:confirm",
            "wire:confirm.prompt",
            "wire:poll",
            "wire:poll.5s",
            "wire:init",
            "wire:show",
            "wire:transition",
            "wire:text",
            "wire:ref",
        )

    private val BASE_SET: Set<String> = BASE.toSet()
    private val BASE_NAMES: Set<String> =
        BASE.map { it.removePrefix("wire:") }.toSet()

    fun isKnownAttribute(raw: String): Boolean {
        val name = raw.substringBefore('=').trim()
        if (name in BASE_SET || name in COMPLETION_LABELS) return true
        if (!name.startsWith("wire:")) return false
        val rest = name.removePrefix("wire:")
        val base = rest.substringBefore('.')
        return base in BASE_NAMES
    }

    fun completionsForPrefix(prefix: String): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty() || p == "wire" || p == "wire:") {
            return COMPLETION_LABELS
        }
        return COMPLETION_LABELS.filter { it.startsWith(p, ignoreCase = true) }
    }
}
