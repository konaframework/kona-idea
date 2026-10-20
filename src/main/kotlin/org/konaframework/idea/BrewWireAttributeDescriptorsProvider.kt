package org.konaframework.idea

import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider

/**
 * Tells the HTML schema that known Brew `wire:*` names are legal on Crema tags.
 * [HtmlUnknownAttributeInspection](com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection)
 * only reports "attribute is not allowed here" when the tag descriptor has no
 * attribute descriptor. Unknown `wire:*` names stay undescribed so that
 * inspection still applies, and [BrewWireAnnotator] keeps its weak warning.
 */
class BrewWireAttributeDescriptorsProvider : XmlAttributeDescriptorsProvider {
    override fun getAttributeDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> {
        if (!isCrema(context)) return emptyArray()
        return BrewWireAttributes.BASE
            .map { Descriptor(it) }
            .toTypedArray()
    }

    override fun getAttributeDescriptor(attributeName: String?, context: XmlTag?): XmlAttributeDescriptor? {
        if (!isCrema(context) || attributeName.isNullOrEmpty()) return null
        if (!BrewWireAttributes.isAllowedHtmlAttribute(attributeName)) return null
        return Descriptor(attributeName)
    }

    private fun isCrema(context: XmlTag?): Boolean {
        val file = context?.containingFile ?: return false
        return CremaPsi.isCrema(file)
    }

    private class Descriptor(private val attrName: String) : XmlAttributeDescriptor {
        override fun getDeclaration(): PsiElement? = null

        override fun getName(): String = attrName

        override fun getName(context: PsiElement?): String = attrName

        override fun init(element: PsiElement?) {}

        override fun getDependencies(): Array<Any> = emptyArray()

        override fun isRequired(): Boolean = false

        override fun isFixed(): Boolean = false

        override fun hasIdType(): Boolean = false

        override fun hasIdRefType(): Boolean = false

        override fun getDefaultValue(): String? = null

        override fun isEnumerated(): Boolean = false

        override fun getEnumeratedValues(): Array<String>? = null

        override fun validateValue(context: XmlElement?, value: String?): String? = null
    }
}
