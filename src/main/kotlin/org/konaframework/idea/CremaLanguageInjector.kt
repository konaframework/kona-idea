package org.konaframework.idea

import com.intellij.psi.ElementManipulators
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects [CremaLanguage] into Java/Kotlin string hosts detected by [CremaInjectionSites].
 *
 * Uses [ElementManipulators.getValueTextRange] so Java text-block indent and Kotlin
 * `trimIndent()` / `trimMargin()` strip correctly. Community Edition — no IntelliLang XML.
 */
class CremaLanguageInjector : LanguageInjector {
    override fun getLanguagesToInject(
        host: PsiLanguageInjectionHost,
        injectionPlacesRegistrar: InjectedLanguagePlaces,
    ) {
        if (!host.isValidHost) return
        if (!CremaInjectionSites.isJvmHostLanguage(host.language.id)) return
        val file = host.containingFile ?: return
        if (CremaPsi.isCrema(file)) return
        val source = file.text ?: return
        val start = host.textRange.startOffset
        if (!CremaInjectionSites.shouldInject(source, start)) return
        val range = ElementManipulators.getValueTextRange(host)
        if (range.isEmpty) return
        injectionPlacesRegistrar.addPlace(CremaLanguage, range, null, null)
    }
}
