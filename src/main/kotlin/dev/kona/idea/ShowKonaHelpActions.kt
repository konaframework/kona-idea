package dev.kona.idea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages

/** Surfaces kona-lsp setup notes (native plugin does not start LSP by default). */
class ShowLspHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(e.project, LspHelp.text(), "Kona LSP")
    }
}

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

object LspHelp {
    fun text(): String =
        """
        Kona Idea highlights Crema natively and does not start kona-lsp.

        Optional language server (stdio JSON-RPC), from a Kona checkout:

          ./gradlew :kona-lsp:jar
          java -jar modules/lsp/build/libs/kona-lsp-*.jar

        Or from an app: ./grind ide:lsp

        See https://github.com/konaframework/kona — docs under Language / Editor.
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
