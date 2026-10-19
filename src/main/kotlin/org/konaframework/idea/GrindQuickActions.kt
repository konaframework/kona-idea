package org.konaframework.idea

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages

object GrindQuickActions {
    fun runServe(project: Project) {
        ensureAndRun(project, "Grind Serve") { cfg ->
            cfg.grindArgs = "serve"
            cfg.host = GrindArgs.DEFAULT_HOST
            cfg.port = GrindArgs.DEFAULT_PORT
        }
    }

    fun runGrind(project: Project, args: String) {
        val name = "Grind $args"
        ensureAndRun(project, name) { cfg ->
            cfg.grindArgs = args
        }
    }

    private fun ensureAndRun(
        project: Project,
        name: String,
        configure: (GrindRunConfiguration) -> Unit,
    ) {
        val root = project.guessProjectDir()
        if (root == null) {
            Messages.showErrorDialog(project, "No project directory.", "Kona")
            return
        }
        val runManager = RunManager.getInstance(project)
        val existing = runManager.allSettings.find { it.name == name && it.type is GrindConfigurationType }
        val settings: RunnerAndConfigurationSettings =
            existing ?: run {
                val type =
                    com.intellij.execution.configurations.ConfigurationTypeUtil.findConfigurationType(
                        GrindConfigurationType::class.java,
                    )
                val factory = type.configurationFactories.first()
                val created = runManager.createConfiguration(name, factory)
                val cfg = created.configuration as GrindRunConfiguration
                configure(cfg)
                runManager.addConfiguration(created)
                created
            }
        (settings.configuration as? GrindRunConfiguration)?.let(configure)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
