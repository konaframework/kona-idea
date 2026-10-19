package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BrewInertiaSupportTest {
    @Test
    fun inertiaRenderPageNamesDetected() {
        val facade = """Inertia.render("Pages/Welcome", mapOf("m" to 1))"""
        val hit = ViewNamePatterns.viewNameAt(facade, facade.indexOf("Pages") + 2)
        assertNotNull(hit)
        assertEquals("Pages/Welcome", hit!!.name)

        val withRequest = """Inertia.render(request, "Pages/About")"""
        assertEquals(
            "Pages/About",
            ViewNamePatterns.viewNameAt(withRequest, withRequest.indexOf('A') + 1)!!.name,
        )

        val manager = """manager.render(Request.get("/"), "Pages/Home")"""
        assertEquals(
            "Pages/Home",
            ViewNamePatterns.viewNameAt(manager, manager.indexOf("Home"))!!.name,
        )

        val viaManager = """Inertia.manager().render("Pages/Via")"""
        assertEquals(
            "Pages/Via",
            ViewNamePatterns.viewNameAt(viaManager, viaManager.indexOf("Via"))!!.name,
        )

        assertEquals(
            "Pag",
            ViewNamePatterns.completionsPrefix(
                """Inertia.render("Pag""",
                """Inertia.render("Pag""".length,
            ),
        )
    }

    @Test
    fun brewAndCremaViewPatternsStillWork() {
        val brew = """Brew.view(engine, "home")"""
        assertEquals("home", ViewNamePatterns.viewNameAt(brew, brew.indexOf('h') + 1)!!.name)
        val crema = """Crema.view(crema, "layouts.app")"""
        assertEquals(
            "layouts.app",
            ViewNamePatterns.viewNameAt(crema, crema.indexOf("layouts") + 2)!!.name,
        )
    }

    @Test
    fun brewWireAttributesKnownAndComplete() {
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:model"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:model.live"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:click.async"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:loading.class"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:target.except"))
        assertTrue(BrewWireAttributes.isKnownAttribute("wire:confirm.prompt"))
        assertFalse(BrewWireAttributes.isKnownAttribute("wire:notARealThing"))
        assertTrue(BrewWireAttributes.isAllowedHtmlAttribute("wire:submit"))
        assertTrue(BrewWireAttributes.isAllowedHtmlAttribute("WIRE:CLICK"))
        assertTrue(BrewWireAttributes.isAllowedHtmlAttribute("wire:model.live"))
        assertTrue(BrewWireAttributes.isAllowedHtmlAttribute("wire:id"))
        assertFalse(BrewWireAttributes.isAllowedHtmlAttribute("wire:notARealThing"))
        assertFalse(BrewWireAttributes.isAllowedHtmlAttribute("class"))
        assertFalse(BrewWireAttributes.isAllowedHtmlAttribute("onclick"))
        assertTrue(BrewWireAttributes.completionsForPrefix("wire:mo").any { it.startsWith("wire:model") })
        assertTrue(BrewWireAttributes.completionsForPrefix("wire:").contains("wire:click"))
        assertTrue(BrewWireAttributes.completionsForPrefix("").contains("wire:submit"))
        assertEquals(BrewWireAttributes.BASE.size, BrewWireAttributes.BASE.toSet().size)
    }

    @Test
    fun brewWirePatternsDetectPrefixAndUnknown() {
        val html = """<input wire:mo"""
        assertEquals("wire:mo", BrewWirePatterns.attributePrefixAt(html, html.length))
        assertEquals("wire", BrewWirePatterns.attributePrefixAt("<button wire", "<button wire".length))
        assertEquals("w", BrewWirePatterns.attributePrefixAt("<div w", "<div w".length))
        assertNull(BrewWirePatterns.attributePrefixAt("plain wire:model", "plain wire:model".length))
        assertNull(BrewWirePatterns.attributePrefixAt("hello", 5))

        val body =
            """
            <form wire:submit="save">
              <input wire:model="draft">
              <button wire:click="go" wire:bogus="x">ok</button>
            </form>
            """.trimIndent()
        val unknown = BrewWirePatterns.findUnknown(body)
        assertEquals(1, unknown.size)
        assertEquals("wire:bogus", unknown[0].name)
        assertTrue(BrewWirePatterns.findAll(body).any { it.name == "wire:model" })
        assertTrue(BrewWirePatterns.isAttrNamePart(':'))
        assertTrue(BrewWirePatterns.isAttrNamePart('.'))
    }

    @Test
    fun brewWireValueAndEchoContexts() {
        val submit = """<form wire:submit="ad"""
        val v = BrewWirePatterns.valueContextAt(submit, submit.length)
        assertNotNull(v)
        assertEquals("wire:submit", v!!.attributeName)
        assertEquals("ad", v.prefix)

        val model = """<input wire:model.live="dra"""
        assertEquals("dra", BrewWirePatterns.valueContextAt(model, model.length)!!.prefix)
        assertEquals("wire:model.live", BrewWirePatterns.valueContextAt(model, model.length)!!.attributeName)

        val click = """<button wire:click="remove("""
        assertEquals("remove", BrewWirePatterns.valueContextAt(click, click.length)!!.prefix)

        assertNull(BrewWirePatterns.valueContextAt("""<form wire:submit=""", """<form wire:submit=""".length))

        val echo = "{{ rem"
        assertEquals("rem", BrewWirePatterns.echoContextAt(echo, echo.length)!!.prefix)
        assertEquals("count", BrewWirePatterns.echoContextAt("{{ count }}", "{{ count".length)!!.prefix)
        assertEquals("n", BrewWirePatterns.echoContextAt("{!! n", "{!! n".length)!!.prefix)
        assertNull(BrewWirePatterns.echoContextAt("plain rem", 9))
        assertNull(BrewWirePatterns.echoContextAt("{{-- comment", "{{-- comment".length))
        assertNull(BrewWirePatterns.echoContextAt("{{ done }} more", "{{ done }} more".length))
        assertNull(BrewWirePatterns.valueContextAt("<div>\nwire:model=\"x\"", 6))
        assertNull(BrewWirePatterns.valueContextAt("""<span title="x""", """<span title="x""".length))
        assertNull(BrewWirePatterns.valueContextAt("hi", 99))
        assertNull(BrewWirePatterns.valueContextAt("hi", -1))
        assertNull(BrewWirePatterns.echoContextAt("hi", 99))
        assertNull(BrewWirePatterns.valueContextAt("""<input wire:model="x" """, """<input wire:model="x""".length + 1))
        assertEquals("items.done", BrewWirePatterns.memberPrefix("items.done"))
        assertEquals("add", BrewWirePatterns.memberPrefix("add("))
        assertEquals("\$wire", BrewWirePatterns.memberPrefix("\$wire"))

        val navSubmit = """<form wire:submit="add">"""
        val addAt = navSubmit.indexOf("add") + 1
        val addRef = BrewWirePatterns.memberReferenceAt(navSubmit, addAt)
        assertNotNull(addRef)
        assertEquals("add", addRef!!.name)
        assertTrue(addRef.expectsAction)

        val navModel = """<input wire:model="draft">"""
        val draftRef = BrewWirePatterns.memberReferenceAt(navModel, navModel.indexOf("dra") + 2)
        assertNotNull(draftRef)
        assertEquals("draft", draftRef!!.name)
        assertTrue(draftRef.expectsProperty)

        val navClick = """<button wire:click="remove({{ loop.index }})">"""
        assertEquals(
            "remove",
            BrewWirePatterns.memberReferenceAt(navClick, navClick.indexOf("remove") + 2)!!.name,
        )
        val navEcho = "{{ remaining }}"
        assertEquals(
            "remaining",
            BrewWirePatterns.memberReferenceAt(navEcho, navEcho.indexOf("rem") + 2)!!.name,
        )
        assertEquals("items", BrewWirePatterns.navigationName("items.done"))
        assertEquals("add", BrewWirePatterns.navigationName("add("))
        assertNull(BrewWirePatterns.memberReferenceAt("plain text", 3))
    }

    @Test
    fun brewMemberOffsetInSource() {
        val source =
            """
            class Todos : Component() {
                var draft = ""
                fun add() {}
                @Computed
                fun remaining(): Int = 0
            }
            """.trimIndent()
        assertEquals(
            source.indexOf("draft"),
            BrewComponentIndex.offsetOfMember(source, "draft", BrewComponentIndex.MemberKind.PROPERTY),
        )
        assertEquals(
            source.indexOf("add"),
            BrewComponentIndex.offsetOfMember(source, "add", BrewComponentIndex.MemberKind.ACTION),
        )
        assertEquals(
            source.indexOf("remaining"),
            BrewComponentIndex.offsetOfMember(source, "remaining", BrewComponentIndex.MemberKind.COMPUTED),
        )
        assertNull(BrewComponentIndex.offsetOfMember(source, "missing"))
    }

    @Test
    fun brewTagPatternsAndAcceptedAttributes() {
        val html = """<h1>{{ name }}</h1><brew:counter :start="0" count="1" />"""
        assertEquals(
            "counter",
            BrewTagPatterns.componentNameAt(html, html.indexOf("counter") + 2),
        )
        assertEquals(
            "counter",
            BrewTagPatterns.componentNameAt("<brew:counter />", "<brew:".length + 2),
        )
        assertEquals(
            "counter",
            BrewTagPatterns.componentNameAt("</brew:counter>", "</brew:".length + 2),
        )
        assertNull(BrewTagPatterns.componentNameAt("plain", 2))

        val startRef = BrewTagPatterns.attributeReferenceAt(html, html.indexOf(":start") + 2)
        assertNotNull(startRef)
        assertEquals("counter", startRef!!.componentName)
        assertEquals(":start", startRef.attributeName)
        assertNull(BrewTagPatterns.attributeReferenceAt(html, html.indexOf("\"0\"") + 1))

        val attrCtx = BrewTagPatterns.attributeNameContextAt("<brew:counter st", "<brew:counter st".length)
        assertNotNull(attrCtx)
        assertEquals("counter", attrCtx!!.componentName)
        assertEquals("st", attrCtx.prefix)
        assertNull(BrewTagPatterns.attributeNameContextAt("""<brew:counter :start="0""", html.length - 1))
        assertNull(BrewTagPatterns.attributeNameContextAt("<div class", 8))

        val afterClosed = """<brew:counter /><div cl"""
        assertNull(BrewTagPatterns.attributeNameContextAt(afterClosed, afterClosed.length))

        val counter =
            """
            class Counter : Component() {
                var count = 0
                var showPosts = false
                fun mount(start: Int = 0) { count = start }
                fun increment() {}
            }
            """.trimIndent()
        val info = BrewComponentIndex.parseSource(counter).single()
        assertEquals(listOf("start"), info.mountParams)
        assertTrue(info.acceptedAttributeNames().containsAll(listOf("count", ":count", "start", ":start", "key")))
        assertTrue(info.acceptedAttributeNames().contains(":show-posts"))
        assertEquals("start", BrewComponentIndex.resolveAttributeMember(info, ":start"))
        assertEquals("count", BrewComponentIndex.resolveAttributeMember(info, "count"))
        assertEquals("showPosts", BrewComponentIndex.resolveAttributeMember(info, "show-posts"))
        assertNull(BrewComponentIndex.resolveAttributeMember(info, "wire:key"))
        assertEquals(
            counter.indexOf("class Counter") + "class ".length,
            BrewComponentIndex.offsetOfClass(counter, "Counter"),
        )
        assertEquals(
            counter.indexOf("start: Int"),
            BrewComponentIndex.offsetOfMountParam(counter, "start"),
        )
        assertEquals("counter", BrewComponentIndex.normalizeTagComponentName("Counter"))
        assertEquals("showPosts", BrewComponentIndex.kebabToCamel("show-posts"))
        assertEquals("show-posts", BrewComponentIndex.propertyKebab("showPosts"))

        val java =
            """
            public class Counter extends Component {
                public int count = 0;
                public void mount(int start) { count = start; }
            }
            """.trimIndent()
        val javaInfo = BrewComponentIndex.parseSource(java).single()
        assertEquals(listOf("start"), javaInfo.mountParams)
        assertEquals(java.indexOf("start)"), BrewComponentIndex.offsetOfMountParam(java, "start"))

        val root = Files.createTempDirectory("kona-find-comp")
        val brew = root.resolve("src/main/kotlin/app/brew")
        Files.createDirectories(brew)
        Files.writeString(brew.resolve("Counter.kt"), counter)
        assertEquals("Counter", BrewComponentIndex.findComponent(root, "Counter")!!.className)
        assertEquals("Counter", BrewComponentIndex.findComponent(root, "counter")!!.className)
    }

    @Test
    fun viewDataIndexFindsControllerMapKeys() {
        val controller =
            """
            package app.http.controllers
            class WelcomeController {
                fun home() = Crema.view(crema, "welcome", mapOf("name" to "World", "title" to "Hi"))
                fun alt() = view("home", mapOf("x" to 1))
            }
            """.trimIndent()
        val root = Files.createTempDirectory("kona-view-data")
        val dir = root.resolve("src/main/kotlin/app/http/controllers")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("WelcomeController.kt"), controller)
        val keys = ViewDataIndex.keysForView(root, "welcome")
        assertEquals(setOf("name", "title"), keys.map { it.key }.toSet())
        val name = ViewDataIndex.firstKey(root, "welcome", "name")
        assertNotNull(name)
        assertEquals(controller.indexOf("\"name\"") + 1, name!!.offset)
        assertEquals(setOf("x"), ViewDataIndex.keysForView(root, "home").map { it.key }.toSet())
        assertTrue(ViewDataIndex.keysForView(root, "missing").isEmpty())
    }

    @Test
    fun brewComponentIndexParsesMembersAndPairsViews() {
        val kt =
            """
            package app.brew
            import org.konaframework.brew.Component
            import org.konaframework.brew.Computed

            class Todos : Component() {
                var draft = ""
                var items = mutableListOf<String>()

                fun add() {}
                fun remove(index: Int) {}
                fun mount() {}
                fun getDraft(): String = draft

                @Computed
                fun remaining(): Int = 0

                override fun render() = view("brew.todos")
            }
            """.trimIndent()
        val parsed = BrewComponentIndex.parseSource(kt).single()
        assertEquals("Todos", parsed.className)
        assertEquals("todos", parsed.componentName)
        assertEquals("brew.todos", parsed.viewName)
        assertTrue(parsed.actions().containsAll(listOf("add", "remove")))
        assertFalse(parsed.actions().contains("mount"))
        assertFalse(parsed.actions().contains("render"))
        assertFalse(parsed.actions().contains("getDraft"))
        assertTrue(parsed.propertiesAndComputed().containsAll(listOf("draft", "items", "remaining")))
        assertTrue(BrewComponentIndex.isActionAttribute("wire:submit"))
        assertTrue(BrewComponentIndex.isActionAttribute("wire:click.prevent"))
        assertTrue(BrewComponentIndex.isPropertyAttribute("wire:model.live"))
        assertTrue(BrewComponentIndex.isPropertyAttribute("wire:show"))
        assertEquals("show-posts", BrewComponentIndex.kebab("ShowPosts"))

        val root = Files.createTempDirectory("kona-brew")
        val brew = root.resolve("src/main/kotlin/app/brew")
        val nested = brew.resolve("admin")
        val views = root.resolve("src/main/resources/views/brew")
        Files.createDirectories(nested)
        Files.createDirectories(views)
        Files.writeString(brew.resolve("Todos.kt"), kt)
        Files.writeString(
            nested.resolve("ShowPosts.kt"),
            """
            package app.brew.admin
            import org.konaframework.brew.Component
            import org.konaframework.brew.BrewComponent

            @BrewComponent("posts")
            class ShowPosts : Component() {
                var title = ""
                fun publish() {}
                companion object { const val X = 1 }
                data class Nested(val x: Int)
            }
            """.trimIndent(),
        )
        Files.writeString(
            brew.resolve("readme.txt"),
            "ignore me",
        )
        val viewFile = views.resolve("todos.crema.html")
        Files.writeString(viewFile, """<form wire:submit=""><input wire:model="">{{ }}</form>""")

        val owner = BrewComponentIndex.ownerForViewFile(root, viewFile)
        assertNotNull(owner)
        assertEquals("Todos", owner!!.className)
        assertEquals("add", owner.actions().first { it == "add" })

        val collected = BrewComponentIndex.collect(root)
        assertTrue(collected.any { it.className == "Todos" })
        assertTrue(BrewComponentIndex.byComponentName(root).containsKey("todos"))
        // Nested package name + annotation override
        val posts = collected.first { it.className == "ShowPosts" }
        assertEquals("posts", posts.componentName)
        assertTrue(posts.actions().contains("publish"))
        assertFalse(posts.propertiesAndComputed().contains("x")) // nested data class truncated

        // Convention view name without explicit render() pairing via prefix strip
        assertNotNull(BrewComponentIndex.ownerForViewName(root, "brew.todos"))
        assertNull(BrewComponentIndex.ownerForViewName(root, "layouts.app"))
        assertNull(BrewComponentIndex.ownerForViewFile(root, root.resolve("nope.crema.html")))
    }

    @Test
    fun brewComponentIndexParsesJavaAndNestedPackage() {
        val java =
            """
            package app.brew;

            import org.konaframework.brew.Component;
            import org.konaframework.brew.Computed;

            public class Counter extends Component {
                public int count = 0;

                public void increment() {}
                public void mount() {}
                public int getCount() { return count; }

                @Computed
                public int doubled() { return count * 2; }

                @Override
                public Object render() {
                    return view("brew.counter");
                }

                public static class Nested {
                    public String title = "";
                }
            }
            """.trimIndent()
        val parsed = BrewComponentIndex.parseSource(java, packageRelative = "").single()
        assertEquals("counter", parsed.componentName)
        assertEquals("brew.counter", parsed.viewName)
        assertTrue(parsed.actions().contains("increment"))
        assertFalse(parsed.actions().contains("mount"))
        assertFalse(parsed.actions().contains("getCount"))
        assertTrue(parsed.propertiesAndComputed().containsAll(listOf("count", "doubled")))
        assertFalse(parsed.propertiesAndComputed().contains("title"))

        val nestedPkg =
            BrewComponentIndex.parseSource(
                """
                package app.brew.admin_area
                class ShowPosts : Component() { fun go() {} }
                """.trimIndent(),
                packageRelative = "admin_area",
            ).single()
        assertEquals("admin-area.show-posts", nestedPkg.componentName)
        assertEquals("brew.admin-area.show-posts", nestedPkg.viewName)

        assertTrue(BrewComponentIndex.parseSource("class NotAComponent").isEmpty())
    }

    @Test
    fun brewTagPatternsDetectPrefixAndNames() {
        assertEquals("coun", BrewTagPatterns.tagPrefixAt("<brew:coun", 10)?.prefix)
        assertTrue(BrewTagPatterns.tagPrefixAt("<brew:coun", 10)!!.afterColon)
        assertEquals("", BrewTagPatterns.tagPrefixAt("<brew:", 6)?.prefix)
        assertTrue(BrewTagPatterns.tagPrefixAt("<brew:", 6)!!.afterColon)
        assertFalse(BrewTagPatterns.tagPrefixAt("<brew", 5)!!.afterColon)
        assertEquals("", BrewTagPatterns.tagPrefixAt("<bre", 4)?.prefix)
        assertNull(BrewTagPatterns.tagPrefixAt("<br", 3))
        assertNull(BrewTagPatterns.tagPrefixAt("<b", 2))
        assertNull(BrewTagPatterns.tagPrefixAt("hello", 5))
        assertTrue(BrewTagPatterns.isBrewTagName("brew:todos"))
        assertTrue(BrewTagPatterns.isBrewTagName("BREW:Counter"))
        assertFalse(BrewTagPatterns.isBrewTagName("x-alert"))
        assertEquals("todos", BrewTagPatterns.componentNameFromTag("brew:todos"))
        assertEquals("admin.show-posts", BrewTagPatterns.componentNameFromTag("brew:admin.show-posts"))
        assertNull(BrewTagPatterns.componentNameFromTag("div"))
        assertEquals("brew:counter", BrewTagPatterns.htmlTag("counter"))
    }

    @Test
    fun inertiaLiveAndFileTemplatesPresent() {
        assertTrue(KonaLiveTemplates.abbreviations().contains("kinertia"))
        assertTrue(KonaLiveTemplates.abbreviations().contains("kinertashare"))
        assertTrue(KonaLiveTemplates.abbreviations().contains("jinertia"))
        val kinertia = KonaLiveTemplates.ALL.first { it.abbreviation == "kinertia" }
        assertTrue(kinertia.body.contains("Inertia.render"))
        assertTrue(kinertia.body.contains("Pages") || kinertia.body.contains("\$PAGE\$"))
        val share = KonaLiveTemplates.ALL.first { it.abbreviation == "kinertashare" }
        assertTrue(share.body.contains("Inertia.share"))

        assertTrue(KonaFileTemplates.TEMPLATES.contains("Kona Inertia Page.vue"))
        assertTrue(KonaFileTemplates.TEMPLATES.contains("Kona Inertia Root.crema.html"))
        assertTrue(KonaTemplateBodies.INERTIA_PAGE.contains("defineProps"))
        assertTrue(KonaTemplateBodies.INERTIA_ROOT.contains("pageAttr"))
        assertTrue(KonaTemplateBodies.INERTIA_ROOT.contains("vite"))
    }

    @Test
    fun brewInertiaHelpMentionsMavenCoords() {
        val text = BrewInertiaHelp.text()
        assertTrue(text.contains("org.konaframework:kona-brew"))
        assertTrue(text.contains("org.konaframework:kona-inertia"))
        assertTrue(text.contains("wire:model"))
        assertTrue(text.contains("Inertia.render"))
        assertTrue(ViewNavHelp.text().contains("Inertia.render"))
        assertTrue(ViewNavHelp.text().contains("wire:"))
        assertTrue(KonaArtifacts.helpText().contains("kona-brew"))
        assertTrue(KonaArtifacts.helpText().contains("kona-inertia"))
        assertTrue(KonaArtifacts.coordinate("kona-brew").startsWith("org.konaframework:"))
    }
}
