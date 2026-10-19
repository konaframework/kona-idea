package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
