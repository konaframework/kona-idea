package org.konaframework.idea

/**
 * Decides whether the caret is on a Cadence instance member or attribute-key
 * call, and which model class to complete for.
 *
 * The model class is the Kotlin/Java **type** of the receiver (cast,
 * annotation, or query assignment) via [ModelPropertyPatterns.resolveModelClass].
 */
object ModelCompletion {
    data class Request(
        val receiver: String,
        val modelClass: String,
        val prefix: String,
        val quoted: Boolean,
        val isKey: Boolean,
    )

    fun requestAt(text: CharSequence, offset: Int): Request? {
        AttributeKeyPatterns.keyContextAt(text, offset)?.let { key ->
            val modelClass =
                ModelPropertyCompletionContributor.resolveModelClass(text, key.receiver, offset)
                    ?: conventionClass(key.receiver)
                    ?: ""
            return Request(
                receiver = key.receiver.orEmpty(),
                modelClass = modelClass,
                prefix = CompletionText.stripDummy(key.prefix),
                quoted = key.quoted,
                isKey = true,
            )
        }
        val prop = ModelPropertyPatterns.propertyAt(text, offset) ?: return null
        val modelClass = prop.modelClass ?: conventionClass(prop.receiver) ?: return null
        return Request(
            receiver = prop.receiver,
            modelClass = modelClass,
            prefix = CompletionText.stripDummy(prop.prefix),
            quoted = true,
            isKey = false,
        )
    }

    /** `user` → `User` when the type is unknown (seeders, services, params typed as Authenticatable). */
    fun conventionClass(receiver: String?): String? {
        if (receiver.isNullOrBlank()) return null
        if (receiver == "this" || receiver == "super") return null
        val simple = receiver.substringAfterLast('.')
        if (simple.isEmpty() || !simple[0].isLowerCase()) return null
        return simple.replaceFirstChar { it.uppercaseChar() }
    }
}
