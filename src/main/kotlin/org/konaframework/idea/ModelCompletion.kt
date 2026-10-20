package org.konaframework.idea

/**
 * Decides whether the caret is on a Cadence instance member or attribute-key
 * call, and which model class to complete for.
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
                ModelPropertyCompletionContributor.resolveModelClass(text, key.receiver) ?: return@let null
            return Request(
                receiver = key.receiver.orEmpty(),
                modelClass = modelClass,
                prefix = key.prefix,
                quoted = key.quoted,
                isKey = true,
            )
        }
        val prop = ModelPropertyPatterns.propertyAt(text, offset) ?: return null
        val modelClass = prop.modelClass ?: return null
        return Request(
            receiver = prop.receiver,
            modelClass = modelClass,
            prefix = prop.prefix,
            quoted = true,
            isKey = false,
        )
    }
}
