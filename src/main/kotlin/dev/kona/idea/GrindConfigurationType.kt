package dev.kona.idea

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/** Run configuration type for `./grind …` in a Kona app. */
class GrindConfigurationType :
    ConfigurationTypeBase(
        "KonaGrind",
        "Grind",
        "Run a Kona ./grind command",
        NotNullLazyValue.createValue { CremaFileType.INSTANCE.icon },
    ) {
    init {
        addFactory(GrindConfigurationFactory(this))
    }
}

class GrindConfigurationFactory(type: GrindConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "KonaGrindFactory"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        GrindRunConfiguration(project, this, "grind list")
}

class GrindRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {

    var grindArgs: String = "list"

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        GrindSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val root = project.guessProjectDir()?.toNioPath()
                val args = grindArgs.split(" ").filter { it.isNotBlank() }
                val cmd = buildGrindCommand(root, args)
                if (root != null) {
                    cmd.withWorkDirectory(root.toFile())
                }
                val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }
}

class GrindSettingsEditor : SettingsEditor<GrindRunConfiguration>() {
    private val argsField = JBTextField("list")

    override fun resetEditorFrom(configuration: GrindRunConfiguration) {
        argsField.text = configuration.grindArgs
    }

    override fun applyEditorTo(configuration: GrindRunConfiguration) {
        configuration.grindArgs = argsField.text.trim()
    }

    override fun createEditor(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Grind arguments", argsField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
}

/** Resolves `./grind` under [root], else `grind` on PATH. */
internal fun buildGrindCommand(root: Path?, args: List<String>): GeneralCommandLine {
    if (root != null) {
        val local = root.resolve("grind")
        if (Files.isRegularFile(local)) {
            return GeneralCommandLine(local.toAbsolutePath().toString()).withParameters(args)
        }
        val grindBat = root.resolve("grind.bat")
        if (Files.isRegularFile(grindBat)) {
            return GeneralCommandLine(grindBat.toAbsolutePath().toString()).withParameters(args)
        }
    }
    return GeneralCommandLine("grind").withParameters(args)
}
