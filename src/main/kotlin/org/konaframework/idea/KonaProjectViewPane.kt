package org.konaframework.idea

import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.tree.DefaultTreeModel

/**
 * Project View presentation for Kona apps: top-level scaffold folders
 * (`app`, `routes`, `views`, `storage`, …) instead of deep package paths.
 *
 * Available alongside the default Project / Packages views.
 */
class KonaProjectViewPane(project: Project) : ProjectViewPane(project) {
    override fun getTitle(): String = TITLE

    override fun getIcon(): Icon = KonaIcons.KONA

    @NonNls
    override fun getId(): String = ID

    override fun getWeight(): Int = WEIGHT

    override fun createSelectInTarget(): SelectInTarget =
        object : ProjectViewSelectInTarget(myProject) {
            override fun toString(): String = TITLE

            override fun getMinorViewId(): String = ID

            override fun getWeight(): Float = WEIGHT.toFloat()
        }

    override fun createStructure(): ProjectAbstractTreeStructureBase =
        object : ProjectTreeStructure(myProject, ID) {
            override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
                KonaProjectRootNode(project, settings)

            override fun isToBuildChildrenInBackground(element: Any): Boolean = true
        }

    override fun createTree(model: DefaultTreeModel): ProjectViewTree =
        object : ProjectViewTree(model) {
            override fun isRootVisible(): Boolean = true
        }

    companion object {
        const val ID: String = "KonaProjectPane"
        const val TITLE: String = "Kona"
        const val WEIGHT: Int = 5
    }
}
