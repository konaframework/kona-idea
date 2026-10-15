package dev.kona.idea

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity

/**
 * When a Kona app is opened, ensure a **Grind Serve** run configuration exists
 * (Netty + virtual threads, port 4000).
 */
class KonaProjectOpenActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val root = project.guessProjectDir()?.toNioPath() ?: return
        if (!KonaLayout.isKonaApp(root)) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ensureGrindServeConfiguration(project)
        }
    }
}

internal fun ensureGrindServeConfiguration(project: Project): Boolean {
    val runManager = RunManager.getInstance(project)
    val type = ConfigurationTypeUtil.findConfigurationType(GrindConfigurationType::class.java)
    val factory: ConfigurationFactory = type.configurationFactories.first()
    val existing =
        runManager.allSettings.any {
            it.type == type &&
                it.configuration is GrindRunConfiguration &&
                (it.configuration as GrindRunConfiguration).grindArgs.trim().startsWith("serve")
        }
    if (existing) return false
    val config = GrindRunConfiguration.createServe(project, factory)
    val settings = runManager.createConfiguration(config, factory)
    runManager.addConfiguration(settings)
    if (runManager.selectedConfiguration == null) {
        runManager.selectedConfiguration = settings
    }
    return true
}
