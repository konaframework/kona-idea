package dev.kona.idea

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory

/** Registers New File templates for Kona controllers, views, and routes snippets. */
class KonaFileTemplates : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group =
            FileTemplateGroupDescriptor("Kona", CremaFileType.INSTANCE.icon)
        TEMPLATES.forEach { name ->
            group.addTemplate(FileTemplateDescriptor(name, CremaFileType.INSTANCE.icon))
        }
        return group
    }

    companion object {
        val TEMPLATES: List<String> =
            listOf(
                "Kona Controller.kt",
                "Kona Controller.java",
                "Kona Crema View.crema.html",
                "Kona WebRoutes.kt",
                "Kona WebRoutes.java",
            )
    }
}

/**
 * Bodies mirrored in `fileTemplates/` — kept here so unit tests lock scaffold shape
 * (typed handlers, no Class@method).
 */
object KonaTemplateBodies {
    val CONTROLLER_KT: String =
        """
        package app.http.controllers

        import dev.kona.http.Request
        import dev.kona.http.Response

        class {{NAME}} {
            fun index(request: Request): Response {
                return Response.text("ok")
            }
        }
        """.trimIndent()

    val CONTROLLER_JAVA: String =
        """
        package app.http.controllers;

        import dev.kona.http.Request;
        import dev.kona.http.Response;

        public class {{NAME}} {
            public Response index(Request request) {
                return Response.text("ok");
            }
        }
        """.trimIndent()

    val CREMA_VIEW: String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8"/>
          <title>{{NAME}}</title>
        </head>
        <body>
          <h1>{{NAME}}</h1>
          {{-- Crema view — use @csrf forms, {{ }} for escaped echo --}}
        </body>
        </html>
        """.trimIndent()

    val WEB_ROUTES_KT: String =
        """
        package routes

        import app.http.controllers.WelcomeController
        import dev.kona.http.Router

        class WebRoutes {
            fun register(router: Router) {
                val welcome = WelcomeController()
                router.get("/") { welcome.index(it) }.name("welcome")
            }
        }
        """.trimIndent()

    val WEB_ROUTES_JAVA: String =
        """
        package routes;

        import app.http.controllers.WelcomeController;
        import dev.kona.http.Router;

        public class WebRoutes {
            public void register(Router router) {
                WelcomeController welcome = new WelcomeController();
                router.get("/", welcome::index).name("welcome");
            }
        }
        """.trimIndent()

    fun usesTypedHandlers(source: String): Boolean =
        source.contains("{ welcome.index(it)") ||
            source.contains("welcome::index") ||
            source.contains("::index")

    fun mentionsClassAtMethod(source: String): Boolean =
        Regex("""["'][\w.]+@\w+["']""").containsMatchIn(source) ||
            source.contains("Controllers.action")
}
