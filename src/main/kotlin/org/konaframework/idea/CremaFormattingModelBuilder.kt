package org.konaframework.idea

import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder

/**
 * Reformat Code / indent-on-enter for Crema views.
 *
 * Delegates to the HTML formatter on the template-data PSI root produced by
 * [CremaFileViewProvider]. HTML code-style settings (indent, attribute wrap,
 * tag spacing) apply; Crema tokens are outer-language elements and stay put.
 */
class CremaFormattingModelBuilder : SimpleTemplateLanguageFormattingModelBuilder()
