package dev.kona.idea

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Settings → Tools → Kona — column suggestion source for Cadence / QueryBuilder.
 */
class KonaConfigurable(private val project: Project) : Configurable {
    private val both =
        JRadioButton("Both (migrations + connection)").apply {
            toolTipText = "Default. Merges Cadence migrations with live JDBC from .env."
        }
    private val migrations =
        JRadioButton("Migrations only").apply {
            toolTipText = "Parse schema.create / schema.table under database/migrations."
        }
    private val connection =
        JRadioButton("Connection only").apply {
            toolTipText = "Query DatabaseMetaData via DB_URL in the project .env."
        }
    private val group = ButtonGroup().apply {
        add(both)
        add(migrations)
        add(connection)
    }

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Kona"

    override fun createComponent(): JComponent {
        reset()
        val form =
            FormBuilder.createFormBuilder()
                .addComponent(JBLabel("Database column completion"))
                .addComponent(both)
                .addComponent(migrations)
                .addComponent(connection)
                .addComponentFillVertically(JPanel(), 0)
                .panel
                .apply { border = JBUI.Borders.empty(10) }
        panel =
            JPanel(BorderLayout()).apply {
                add(form, BorderLayout.NORTH)
            }
        return panel!!
    }

    private fun selectedMode(): ColumnSourceMode =
        when {
            migrations.isSelected -> ColumnSourceMode.MIGRATIONS_ONLY
            connection.isSelected -> ColumnSourceMode.CONNECTION_ONLY
            else -> ColumnSourceMode.BOTH
        }

    private fun select(mode: ColumnSourceMode) {
        when (mode) {
            ColumnSourceMode.MIGRATIONS_ONLY -> migrations.isSelected = true
            ColumnSourceMode.CONNECTION_ONLY -> connection.isSelected = true
            ColumnSourceMode.BOTH -> both.isSelected = true
        }
        // Keep ButtonGroup happy if nothing was selected yet.
        if (group.selection == null) both.isSelected = true
    }

    override fun isModified(): Boolean =
        selectedMode() != KonaIdeaSettings.getInstance(project).columnSourceMode

    override fun apply() {
        KonaIdeaSettings.getInstance(project).columnSourceMode = selectedMode()
    }

    override fun reset() {
        select(KonaIdeaSettings.getInstance(project).columnSourceMode)
    }
}
