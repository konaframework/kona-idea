package org.konaframework.idea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages

class ShowLayoutHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val root = e.project?.guessProjectDir()?.toNioPath()
        val body =
            if (root != null && KonaLayout.isKonaApp(root)) {
                val d = KonaLayout.detect(root)
                KonaLayout.summarize(d) + "\n\n" + layoutDoc(d.language)
            } else {
                layoutDoc(KonaLayout.AppLanguage.KOTLIN)
            }
        Messages.showInfoMessage(e.project, body, "Kona project layout")
    }
}

class ShowArtifactsHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(e.project, KonaArtifacts.helpText(), "Kona Maven modules")
    }
}

class ShowRoutingHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(e.project, KonaRoutingGuidance.helpText(), "Kona routing")
    }
}

class ShowViewNavHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(e.project, ViewNavHelp.text(), "Kona view navigation")
    }
}

object ViewNavHelp {
    fun text(): String =
        """
        View / page names (Crema.view, Brew.view, Inertia.render, engine.make / render,
        @extends, @include, …) support native IntelliJ navigation — no language server.

        Go to Declaration (open the .crema.html file):
          • Windows / Linux: Ctrl+B  or  Ctrl+Click
          • macOS: ⌘B  or  ⌘Click

        Also: Navigate → Declaration or Related Symbol when the caret is on the name.

        Autocomplete: Ctrl+Space inside the view / page name string.
        Crema directives: type @ in a *.crema.html view (native completion).
        Brew: type wire: on HTML tags for Brew attribute completion.
        Inside wire:submit / wire:click / wire:model (and {{ }} echoes), Ctrl+Space
        completes actions and properties from the owning app.brew component class.
        Ctrl+B / Ctrl+Click on those names opens the property or method in the Kotlin/Java class.
        On <brew:counter>, Ctrl+B opens the component class; tag attributes complete from
        properties / mount params. On page views, {{ name }} jumps to the controller mapOf key.
        Known wire:* names are legal HTML attributes in Crema views.
        Project tool window: switch the view dropdown to “Kona” for the scaffold tree.

        Env keys and values (Env.get / getOrDefault / require in config):
          • Ctrl+Space completes keys from .env, .env.example, and other Env lookups.
          • Ctrl+B or Ctrl+Click opens that key in the project .env file.
          • In .env, Ctrl+Space completes keys already referenced from source.
          • Value completion for known option-like keys (DB_CONNECTION, LOG_CHANNEL /
            LOG_STACK / LOG_LEVEL, SESSION_DRIVER, CACHE_*, QUEUE_CONNECTION, MAIL_MAILER,
            HASH_DRIVER, FILESYSTEM_DISK, APP_ENV, booleans, SESSION_SAME_SITE, …) in
            .env / .env.* and in getOrDefault("KEY", "…") defaults. Project connection
            and disk names from config/Database and config/Filesystems are merged in.

        Cadence models (app/Models):
          • On a model instance (user. / user.em), Ctrl+Space completes columns,
            accessors (getFullNameAttribute → full_name), and relations (posts).
          • Inside getAttribute / setAttribute / attribute / hasAttribute (and raw
            variants), Ctrl+Space completes the same attribute keys.
          • User.where("…") still completes that model’s table columns.
        """.trimIndent()
}

class ShowBrewInertiaHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(e.project, BrewInertiaHelp.text(), "Kona Brew + Inertia")
    }
}

object BrewInertiaHelp {
    fun text(): String =
        """
        Brew (org.konaframework:kona-brew)
          • Crema HTML attributes: wire:model, wire:click, wire:submit, wire:loading,
            wire:target, wire:ignore, wire:key, wire:dirty, wire:offline, wire:confirm,
            wire:poll, wire:init, wire:show, wire:transition (+ common modifiers).
          • Autocomplete + weak warnings for unknown wire:* names in *.crema.html.
          • wire:submit / wire:click / wire:init / wire:target values complete actions
            from the paired app.brew component (views/brew/todos ↔ Todos).
          • wire:model / wire:show / wire:text and {{ }} echoes complete properties
            and @Computed members from that class.
          • Ctrl+B / Ctrl+Click on <brew:name> opens the class; on attributes / wire
            values / {{ }} jumps to the prop, mount param, method, or controller mapOf key.

        Inertia (org.konaframework:kona-inertia)
          • Page names in Inertia.render(…) / manager.render(…) get the same completion
            and Go to Declaration patterns as Crema views.
          • Live templates: kinertia, kinertashare (Kotlin), jinertia (Java).
          • File templates: Kona Inertia Page.vue, Kona Inertia Root.crema.html.

        Docs: https://docs.konaframework.org/docs/brew/
              https://docs.konaframework.org/docs/inertia/
        """.trimIndent()
}

internal fun layoutDoc(language: KonaLayout.AppLanguage): String {
    val src =
        when (language) {
            KonaLayout.AppLanguage.JAVA -> "src/main/java"
            else -> "src/main/kotlin"
        }
    return """
        Canonical app tree (kona new / grind scaffolds):

          $src/bootstrap     — App + Middleware
          $src/app           — providers, HTTP, models
          $src/routes        — WebRoutes + ApiRoutes
          $src/config        — executable ConfigFile objects
          $src/database      — migrations, factories, seeders
          src/main/resources/views   — Crema (*.crema.html)
          src/main/resources/public  — static assets
          storage/           — writable runtime data
          ./grind            — app-local CLI

        Serve (Netty): ./grind serve → http://127.0.0.1:4000
        Create apps:   kona new <name> [--lang=kotlin|java]
        """.trimIndent()
}
