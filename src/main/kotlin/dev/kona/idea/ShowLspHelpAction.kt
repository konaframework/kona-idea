package dev.kona.idea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/** Surfaces kona-lsp setup notes (native plugin does not start LSP by default). */
class ShowLspHelpAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            e.project,
            LspHelp.text(),
            "Kona LSP",
        )
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
