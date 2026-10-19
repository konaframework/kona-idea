package org.konaframework.idea

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlElementsGroup
import com.intellij.xml.XmlNSDescriptor
import com.intellij.xml.XmlTagNameProvider
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor

/**
 * Registers Brew `<brew:*>` tags with the HTML schema so IDEA does not report
 * “unknown HTML tag” / “attribute is not allowed here” on Crema views.
 * Also contributes tag-name variants for the built-in HTML tag completion.
 */
class BrewTagDescriptorsProvider : XmlElementDescriptorProvider, XmlTagNameProvider {
    override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
        if (!isCrema(tag)) return null
        val name = tag.name
        if (!BrewTagPatterns.isBrewTagName(name)) return null
        return BrewTagElementDescriptor(name)
    }

    override fun addTagNameVariants(
        elements: MutableList<LookupElement>,
        tag: XmlTag,
        prefix: String?,
    ) {
        if (!isCrema(tag)) return
        val root = tag.project.guessProjectDir()?.toNioPath() ?: return
        if (!KonaLayout.isKonaApp(root)) return
        val p = prefix.orEmpty()
        BrewComponentIndex.collect(root).forEach { info ->
            val html = BrewTagPatterns.htmlTag(info.componentName)
            if (p.isEmpty() ||
                html.startsWith(p, ignoreCase = true) ||
                info.componentName.startsWith(p, ignoreCase = true)
            ) {
                elements +=
                    LookupElementBuilder.create(html)
                        .withPresentableText("<$html />")
                        .withTypeText(info.viewName, true)
                        .withIcon(KonaIcons.KONA_13)
            }
        }
    }

    private fun isCrema(tag: XmlTag?): Boolean {
        val file = tag?.containingFile ?: return false
        return file.viewProvider.baseLanguage === CremaLanguage ||
            CremaFileType.isCremaFileName(file.name)
    }

    /**
     * Concrete descriptor (not [com.intellij.xml.impl.schema.AnyXmlElementDescriptor])
     * so [com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection] treats
     * the tag as known. Attributes are accepted freely; [BrewWireAnnotator] still
     * weak-warns on unknown `wire:*` names.
     */
    private class BrewTagElementDescriptor(
        private val tagName: String,
    ) : XmlElementDescriptor {
        override fun getDeclaration(): PsiElement? = null

        override fun getName(context: PsiElement?): String = tagName

        override fun getName(): String = tagName

        override fun init(element: PsiElement?) {}

        override fun getDependencies(): Array<Any> = emptyArray()

        override fun getQualifiedName(): String = tagName

        override fun getDefaultName(): String = tagName

        override fun getElementsDescriptors(context: XmlTag?): Array<XmlElementDescriptor> =
            XmlElementDescriptor.EMPTY_ARRAY

        override fun getElementDescriptor(childTag: XmlTag?, contextTag: XmlTag?): XmlElementDescriptor? =
            null

        override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> =
            BrewWireAttributes.BASE.map { AnyXmlAttributeDescriptor(it) }.toTypedArray()

        override fun getAttributeDescriptor(attributeName: String?, context: XmlTag?): XmlAttributeDescriptor? {
            if (attributeName.isNullOrEmpty()) return null
            return AnyXmlAttributeDescriptor(attributeName)
        }

        override fun getAttributeDescriptor(attribute: XmlAttribute?): XmlAttributeDescriptor? =
            attribute?.let { getAttributeDescriptor(it.name, it.parent) }

        override fun getNSDescriptor(): XmlNSDescriptor? = null

        override fun getTopGroup(): XmlElementsGroup? = null

        override fun getContentType(): Int = XmlElementDescriptor.CONTENT_TYPE_EMPTY

        override fun getDefaultValue(): String? = null
    }
}
