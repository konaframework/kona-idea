package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CremaDirectivesTest {
    @Test
    fun knownNamesCoverCoreSurface() {
        assertTrue(CremaDirectives.isKnown("csrf"))
        assertTrue(CremaDirectives.isKnown("asset"))
        assertTrue(CremaDirectives.isKnown("includeUnless"))
        assertFalse(CremaDirectives.isKnown("notARealDirective"))
        assertEquals(CremaDirectives.NAMES.size, CremaDirectives.COMPLETION_LABELS.size)
        assertTrue(CremaDirectives.COMPLETION_LABELS.all { it.startsWith("@") })
    }

    @Test
    fun directiveNameAt() {
        assertEquals("if", CremaDirectives.directiveNameAt("@if(x)", 0))
        assertNull(CremaDirectives.directiveNameAt("@@if", 1))
        assertEquals("example", CremaDirectives.directiveNameAt("hi@example.com", 2))
        assertFalse(
            CremaDirectiveDiagnostics.looksLikeDirectiveCall("hi@example.com", 2, "example"),
        )
        assertNull(CremaDirectives.directiveNameAt("@", 0))
    }

    @Test
    fun echoCatalogCompletesHelpersAndVariables() {
        assertTrue(CremaEchoCatalog.completions("").any { it.name == "url" })
        assertTrue(CremaEchoCatalog.completions("ur").map { it.name } == listOf("url"))
        assertEquals("url('')", CremaEchoCatalog.completions("ur").first().insertText)
        assertTrue(CremaEchoCatalog.completions("loop.").any { it.name == "loop.index" })
        assertTrue(CremaEchoCatalog.completions("loop.").any { it.name == "loop.remaining" })
        assertTrue(CremaEchoCatalog.completions("csrf").any { it.name == "csrf_token" })
        assertTrue(CremaEchoCatalog.completions("").any { it.name == "errors" })
        assertTrue(CremaEchoCatalog.HELPERS.all { it.helper })
        assertTrue(CremaEchoCatalog.VARIABLES.none { it.helper })
        assertTrue(CremaEchoCatalog.completions("zzz").isEmpty())
        assertEquals(5, CremaEchoCompletion.caretOffsetInInsert("url('')"))
        assertEquals(11, CremaEchoCompletion.caretOffsetInInsert("csrf_token()"))
        assertEquals(6, CremaEchoCompletion.caretOffsetInInsert("errors"))
        val echo = BrewWirePatterns.echoContextAt("{{ ur", 5)
        assertEquals("ur", echo!!.prefix)
        assertTrue(CremaEchoCatalog.completions(echo.prefix).any { it.name == "url" })
    }

    @Test
    fun completionsForPrefix() {
        assertTrue(CremaDirectives.completionsForPrefix("@cs").contains("@csrf"))
        assertTrue(CremaDirectives.completionsForPrefix("inc").any { it.startsWith("@include") })
        assertTrue(CremaDirectives.completionsForPrefix("zzz").isEmpty())
    }

    @Test
    fun completionPrefixBefore() {
        assertEquals("@cs", CremaCompletion.prefixBefore("  @cs", 5))
        assertEquals("", CremaCompletion.prefixBefore("hello", 5))
        assertEquals("@if", CremaCompletion.prefixBefore("@if(", 3))
    }

    @Test
    fun unknownDirectiveDiagnostics() {
        val hits = CremaDirectiveDiagnostics.findUnknown("@csrf @nope @if(true)")
        assertEquals(1, hits.size)
        assertEquals("nope", hits[0].name)
        assertTrue(CremaDirectiveDiagnostics.findUnknown("hi@example.com @@csrf").isEmpty())
    }

    @Test
    fun fileNameDetection() {
        assertTrue(CremaFileType.isCremaFileName("home.crema.html"))
        assertTrue(CremaFileType.isCremaFileName("partial.crema.kt"))
        assertFalse(CremaFileType.isCremaFileName("home.html"))
        assertFalse(CremaFileType.isCremaFileName("App.kt"))
    }

    @Test
    fun grindCommandPrefersLocalScript() {
        val dir = java.nio.file.Files.createTempDirectory("kona-grind")
        val grind = dir.resolve("grind")
        java.nio.file.Files.writeString(grind, "#!/bin/sh\n")
        grind.toFile().setExecutable(true)
        val cmd = buildGrindCommand(dir, listOf("list"))
        assertTrue(cmd.exePath.contains("grind"))
        assertEquals(listOf("list"), cmd.parametersList.parameters)
        val fallback = buildGrindCommand(null, listOf("migrate"))
        assertEquals("grind", fallback.exePath)
        assertEquals(listOf("migrate"), fallback.parametersList.parameters)

        val win = java.nio.file.Files.createTempDirectory("kona-grind-win")
        java.nio.file.Files.writeString(win.resolve("grind.bat"), "@echo off\n")
        val bat = buildGrindCommand(win, listOf("list"))
        assertTrue(bat.exePath.endsWith("grind.bat"))
    }

    @Test
    fun completionPrefixAtEdges() {
        assertEquals("", CremaCompletion.prefixBefore("", 0))
        assertEquals("@", CremaCompletion.prefixBefore("@", 1))
        assertEquals(
            listOf(UnknownDirective("zzz", 0, 4)),
            CremaDirectiveDiagnostics.findUnknown("@zzz "),
        )
        assertTrue(CremaDirectiveDiagnostics.looksLikeDirectiveCall("@zzz(", 0, "zzz"))
        assertTrue(CremaDirectiveDiagnostics.looksLikeDirectiveCall("@zzz", 0, "zzz"))
    }

    @Test
    fun lexerStateAndBufferAccessors() {
        val lexer = CremaLexer()
        lexer.start("{{ x }}", 0, 7, 0)
        assertEquals(0, lexer.state)
        assertEquals("{{ x }}", lexer.bufferSequence.toString())
        assertEquals(CremaTokens.ECHO, lexer.tokenType)
        assertEquals(0, lexer.tokenStart)
        assertEquals(7, lexer.tokenEnd)
        lexer.advance()
        assertEquals(null, lexer.tokenType)
    }

    @Test
    fun cremaFormattingFollowsHtmlHostAndFreezesCremaSpans() {
        assertTrue(CremaFormatting.appliesToFileName("welcome.crema.html"))
        assertTrue(CremaFormatting.appliesToFileName("partial.crema.kt"))
        assertFalse(CremaFormatting.appliesToFileName("welcome.html"))
        assertFalse(CremaFormatting.appliesToFileName("App.kt"))
        assertFalse(CremaFormatting.isFrozenToken(null))
        assertFalse(CremaFormatting.isFrozenToken(CremaTokens.TEMPLATE_DATA))
        assertTrue(CremaFormatting.isFrozenToken(CremaTokens.ECHO))
        assertTrue(CremaFormatting.isFrozenToken(CremaTokens.DIRECTIVE))
        assertTrue(CremaFormatting.frozenRanges("").isEmpty())
        assertTrue(CremaFormatting.frozenRanges("<div class=\"card\"></div>").isEmpty())
        val echo = "{{ url('/x') }}"
        assertEquals(listOf(0 until echo.length), CremaFormatting.frozenRanges(echo))
        val raw = "{!! html !!}"
        assertEquals(listOf(0 until raw.length), CremaFormatting.frozenRanges(raw))
        val comment = "{{-- note --}}"
        assertEquals(listOf(0 until comment.length), CremaFormatting.frozenRanges(comment))
        val mixed = "<p>{{ name }}</p>@csrf"
        val ranges = CremaFormatting.frozenRanges(mixed)
        assertEquals(2, ranges.size)
        assertEquals("{{ name }}", mixed.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("@csrf", mixed.substring(ranges[1].first, ranges[1].last + 1))
        val block = "@if(true)<li>{{ item }}</li>@endif"
        val frozen = CremaFormatting.frozenRanges(block)
        assertEquals(3, frozen.size)
        assertEquals("@if(true)", block.substring(frozen[0].first, frozen[0].last + 1))
        assertEquals("{{ item }}", block.substring(frozen[1].first, frozen[1].last + 1))
        assertEquals("@endif", block.substring(frozen[2].first, frozen[2].last + 1))
    }
}
