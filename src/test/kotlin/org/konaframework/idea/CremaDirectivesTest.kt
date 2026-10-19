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
}
