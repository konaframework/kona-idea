package dev.kona.idea

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/** New → Crema View under resources/views (or any directory). */
class NewCremaViewAction :
    CreateFileFromTemplateAction("Crema View", "Create a Kona Crema view", CremaFileType.INSTANCE.icon),
    DumbAware {
    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder,
    ) {
        builder
            .setTitle("New Crema View")
            .addKind("Crema View", CremaFileType.INSTANCE.icon, "Kona Crema View.crema.html")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        "Crema View"
}
