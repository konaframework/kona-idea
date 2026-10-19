package org.konaframework.idea

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/** Right-side quick access panel for common Kona / Grind actions. */
class KonaToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KonaQuickAccessPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

class KonaQuickAccessPanel(private val project: Project) : JPanel(BorderLayout()) {
    init {
        border = JBUI.Borders.empty(8)
        val header = JBLabel("Kona").apply {
            icon = KonaIcons.KONA
            border = JBUI.Borders.emptyBottom(8)
        }
        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
        }

        fun section(title: String) {
            buttons.add(JBLabel(title).apply { border = JBUI.Borders.empty(10, 0, 4, 0) })
        }

        fun addAction(text: String, actionId: String) {
            buttons.add(
                JButton(text).apply {
                    alignmentX = LEFT_ALIGNMENT
                    maximumSize = preferredSize.let {
                        java.awt.Dimension(Int.MAX_VALUE, it.height)
                    }
                    addActionListener {
                        val action =
                            com.intellij.openapi.actionSystem.ActionManager.getInstance()
                                .getAction(actionId)
                        if (action != null) {
                            com.intellij.openapi.actionSystem.ActionManager.getInstance()
                                .tryToExecute(action, null, this, "KonaToolWindow", true)
                        }
                    }
                },
            )
            buttons.add(Box.createVerticalStrut(4))
        }

        fun addRunnable(text: String, block: () -> Unit) {
            buttons.add(
                JButton(text).apply {
                    alignmentX = LEFT_ALIGNMENT
                    maximumSize = preferredSize.let {
                        java.awt.Dimension(Int.MAX_VALUE, it.height)
                    }
                    addActionListener { block() }
                },
            )
            buttons.add(Box.createVerticalStrut(4))
        }

        section("Grind")
        addRunnable("Serve (Netty :4000)") { GrindQuickActions.runServe(project) }
        addRunnable("List commands") { GrindQuickActions.runGrind(project, "list") }
        addRunnable("Migrate") { GrindQuickActions.runGrind(project, "migrate") }
        addRunnable("Queue work") { GrindQuickActions.runGrind(project, "queue:work") }
        addRunnable("Schedule work") { GrindQuickActions.runGrind(project, "schedule:work") }

        section("Create")
        addAction("New Crema View", "Kona.NewCremaView")

        section("Help")
        addAction("Project layout", "Kona.ShowLayoutHelp")
        addAction("Routing (typed handlers)", "Kona.ShowRoutingHelp")
        addAction("Maven modules", "Kona.ShowArtifactsHelp")
        addAction("View navigation shortcuts", "Kona.ShowViewNavHelp")
        addAction("Brew + Inertia", "Kona.ShowBrewInertiaHelp")

        section("Docs")
        addRunnable("Kona docs") { BrowserUtil.browse("https://docs.konaframework.org/") }
        addRunnable("IntelliJ guide") {
            BrowserUtil.browse("https://docs.konaframework.org/docs/language/intellij/")
        }
        addRunnable("Crema views") {
            BrowserUtil.browse("https://docs.konaframework.org/docs/the-basics/views/")
        }
        addRunnable("Brew") { BrowserUtil.browse("https://docs.konaframework.org/docs/brew/") }
        addRunnable("Inertia") {
            BrowserUtil.browse("https://docs.konaframework.org/docs/inertia/")
        }

        val hint =
            JBLabel(
                "<html><body style='width:180px;color:gray'>" +
                    "Go to view: <b>Ctrl+B</b> / Ctrl+Click (⌘B on macOS).<br/>" +
                    "Crema: type <b>@</b> for directives.<br/>" +
                    "Brew: type <b>wire:</b> on tags.<br/>" +
                    "Maven: <b>org.konaframework:kona-brew</b> / <b>kona-inertia</b>." +
                    "</body></html>",
            ).apply { border = JBUI.Borders.emptyTop(12) }

        val north = JPanel(GridLayout(0, 1)).apply {
            add(header)
        }
        add(north, BorderLayout.NORTH)
        add(JBScrollPane(buttons), BorderLayout.CENTER)
        add(hint, BorderLayout.SOUTH)
    }
}
