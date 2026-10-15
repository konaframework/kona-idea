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
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/** Run configuration type for `./grind …` in a Kona app (Netty serve included). */
class GrindConfigurationType :
    ConfigurationTypeBase(
        ID,
        "Grind",
        "Run a Kona ./grind command (serve uses Netty + virtual threads)",
        NotNullLazyValue.createValue { KonaIcons.KONA },
    ) {
    init {
        addFactory(GrindConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "KonaGrind"
    }
}

class GrindConfigurationFactory(type: GrindConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "KonaGrindFactory"

    override fun getOptionsClass(): Class<out BaseState> = GrindRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        GrindRunConfiguration(project, this, "grind")
}

class GrindRunConfigurationOptions : RunConfigurationOptions() {
    var grindArgs by string("list")
    var host by string(GrindArgs.DEFAULT_HOST)
    var port by string(GrindArgs.DEFAULT_PORT)
}

class GrindRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<GrindRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): GrindRunConfigurationOptions =
        super.getOptions() as GrindRunConfigurationOptions

    var grindArgs: String
        get() = options.grindArgs ?: "list"
        set(value) {
            options.grindArgs = value
        }

    var host: String
        get() = options.host ?: GrindArgs.DEFAULT_HOST
        set(value) {
            options.host = value
        }

    var port: String
        get() = options.port ?: GrindArgs.DEFAULT_PORT
        set(value) {
            options.port = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        GrindSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val root = project.guessProjectDir()?.toNioPath()
                val args = GrindArgs.compose(grindArgs, host, port)
                val cmd = buildGrindCommand(root, args)
                if (root != null) {
                    cmd.withWorkDirectory(root.toFile())
                }
                // grind serve sets HOST/PORT for the child JVM; also export for clarity.
                if (args.firstOrNull() == "serve") {
                    cmd.withEnvironment("HOST", host.ifBlank { GrindArgs.DEFAULT_HOST })
                    cmd.withEnvironment("PORT", port.ifBlank { GrindArgs.DEFAULT_PORT })
                }
                val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }

    companion object {
        fun createServe(project: Project, factory: ConfigurationFactory): GrindRunConfiguration {
            val cfg = GrindRunConfiguration(project, factory, "Grind Serve")
            cfg.grindArgs = "serve"
            cfg.host = GrindArgs.DEFAULT_HOST
            cfg.port = GrindArgs.DEFAULT_PORT
            return cfg
        }
    }
}

class GrindSettingsEditor : SettingsEditor<GrindRunConfiguration>() {
    private val argsField = JBTextField("list")
    private val hostField = JBTextField(GrindArgs.DEFAULT_HOST)
    private val portField = JBTextField(GrindArgs.DEFAULT_PORT)
    private val hint =
        JBLabel(
            "<html>For <b>serve</b>: Netty HTTP adapter + virtual threads. " +
                "Default bind <code>127.0.0.1:4000</code>. Host/port become " +
                "<code>--host</code>/<code>--port</code> when missing from arguments.</html>",
        )

    override fun resetEditorFrom(configuration: GrindRunConfiguration) {
        argsField.text = configuration.grindArgs
        hostField.text = configuration.host
        portField.text = configuration.port
    }

    override fun applyEditorTo(configuration: GrindRunConfiguration) {
        configuration.grindArgs = argsField.text.trim()
        configuration.host = hostField.text.trim().ifBlank { GrindArgs.DEFAULT_HOST }
        configuration.port = portField.text.trim().ifBlank { GrindArgs.DEFAULT_PORT }
    }

    override fun createEditor(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Grind arguments", argsField)
            .addLabeledComponent("Host (serve)", hostField)
            .addLabeledComponent("Port (serve)", portField)
            .addComponent(hint)
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
