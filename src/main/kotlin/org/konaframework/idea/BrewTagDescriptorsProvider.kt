package org.konaframework.idea

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
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
 * Also contributes tag-name variants and attribute descriptors from the component class.
 */
class BrewTagDescriptorsProvider : XmlElementDescriptorProvider, XmlTagNameProvider {
    override fun getDescriptor(tag: XmlTag): XmlElementDescriptor? {
        if (!isCrema(tag)) return null
        val name = tag.name
        if (!BrewTagPatterns.isBrewTagName(name)) return null
        return BrewTagElementDescriptor(name, tag.project)
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
                    KonaLookup.wrap(
                        LookupElementBuilder.create(html)
                            .withPresentableText("<$html />")
                            .withTypeText(info.viewName, true)
                            .withIcon(KonaIcons.KONA_13),
                    )
            }
        }
    }

    private fun isCrema(tag: XmlTag?): Boolean {
        val file = tag?.containingFile ?: return false
        return CremaPsi.isCrema(file)
    }

    /**
     * Concrete descriptor so HtmlUnknownTagInspection treats the tag as known.
     * [getDeclaration] enables Ctrl+Click on the tag name → component class.
     */
    private class BrewTagElementDescriptor(
        private val tagName: String,
        private val project: Project,
    ) : XmlElementDescriptor {
        override fun getDeclaration(): PsiElement? {
            val root = project.guessProjectDir()?.toNioPath() ?: return null
            val component = BrewTagPatterns.componentNameFromTag(tagName) ?: return null
            return BrewTagNavigation.elementForComponent(project, root, component, member = null)
        }

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

        override fun getAttributesDescriptors(context: XmlTag?): Array<XmlAttributeDescriptor> {
            val names = linkedSetOf<String>()
            names += BrewWireAttributes.BASE
            val root = project.guessProjectDir()?.toNioPath()
            val component = BrewTagPatterns.componentNameFromTag(tagName)
            if (root != null && component != null && KonaLayout.isKonaApp(root)) {
                BrewComponentIndex.findComponent(root, component)?.acceptedAttributeNames()?.let {
                    names += it
                }
            }
            return names.map { AnyXmlAttributeDescriptor(it) }.toTypedArray()
        }

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
