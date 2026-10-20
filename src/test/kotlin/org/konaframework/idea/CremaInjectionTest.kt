package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CremaInjectionTest {
    @Test
    fun javaTextBlockOnTemplateOverride() {
        val source =
            """
            package app.brew;
            import org.konaframework.brew.Component;
            public class Counter extends Component {
                public int count = 0;
                public void increment() { count++; }
                @Override
                public String template() {
                    return ${"\"\"\""}
                        <div class="card">
                            <button wire:click="increment">+</button>
                            <output>{{ count }}</output>
                        </div>
                        ${"\"\"\""};
                }
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.TEMPLATE_OVERRIDE, sites[0].reason)
        assertTrue(sites[0].textBlock)
        val markup = source.substring(sites[0].contentStart, sites[0].contentEnd)
        assertTrue(markup.contains("wire:click"))
        assertTrue(CremaInjectionSites.shouldInject(source, sites[0].quoteStart))
        assertEquals(sites[0], CremaInjectionSites.siteAt(source, sites[0].quoteStart + 4))
    }

    @Test
    fun javaCremaTemplateAnnotation() {
        val source =
            """
            class Other {
                @CremaTemplate
                public String markup() {
                    return ${"\"\"\""}<p>Hi</p>${"\"\"\""};
                }
                public String name() {
                    return "nope";
                }
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.ANNOTATED_METHOD, sites[0].reason)
        assertFalse(source.substring(sites[0].contentStart, sites[0].contentEnd).contains("nope"))
    }

    @Test
    fun kotlinOverrideTemplateTrimIndent() {
        val source =
            """
            class Counter : Component() {
                var count = 0
                override fun template(): String = ${"\"\"\""}
                    <div wire:click="increment">{{ count }}</div>
                ${"\"\"\""}.trimIndent()
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.TEMPLATE_OVERRIDE, sites[0].reason)
        assertTrue(sites[0].textBlock)
        assertTrue(source.substring(sites[0].quoteEnd).trimStart().startsWith(".trimIndent()"))
    }

    @Test
    fun kotlinCremaTemplateAndTrimMargin() {
        val source =
            """
            class Box : Component() {
                @CremaTemplate
                override fun template() = ${"\"\"\""}
                    |<section>{{ slot }}</section>
                ${"\"\"\""}.trimMargin()
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.ANNOTATED_METHOD, sites[0].reason)
        assertTrue(source.substring(sites[0].quoteEnd).contains("trimMargin()"))
    }

    @Test
    fun languageCremaLineComment() {
        val kt =
            """
            fun render(): Any {
                // language=Crema
                val html = ${"\"\"\""}<p>{{ name }}</p>${"\"\"\""}
                return html
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(kt)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.LANGUAGE_COMMENT, sites[0].reason)

        val java =
            """
            void go() {
                // language=Crema keep
                String s = "<div></div>";
            }
            """.trimIndent()
        assertEquals(
            CremaInjectionSites.Reason.LANGUAGE_COMMENT,
            CremaInjectionSites.findAll(java).single().reason,
        )
    }

    @Test
    fun languageCremaBlockCommentAndCase() {
        val source =
            """
            val html = /* language=crema */ "<span></span>"
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.LANGUAGE_COMMENT, sites[0].reason)
    }

    @Test
    fun languageJsonIsIgnored() {
        val source =
            """
            // language=JSON
            val payload = "{\"a\":1}"
            val other = "plain"
            """.trimIndent()
        assertTrue(CremaInjectionSites.findAll(source).isEmpty())
    }

    @Test
    fun inlineAndBrewViewInline() {
        val kt =
            """
            class Hello : Component() {
                override fun render(): Any = inline(${"\"\"\""}<p>Hello {{ name }}</p>${"\"\"\""})
            }
            """.trimIndent()
        val ktSites = CremaInjectionSites.findAll(kt)
        assertEquals(1, ktSites.size)
        assertEquals(CremaInjectionSites.Reason.INLINE_CALL, ktSites[0].reason)

        val java =
            """
            Object render() {
                return BrewView.inline("<div></div>");
            }
            """.trimIndent()
        assertEquals(
            CremaInjectionSites.Reason.INLINE_CALL,
            CremaInjectionSites.findAll(java).single().reason,
        )
    }

    @Test
    fun inlineFunModifierIsNotACall() {
        val source =
            """
            inline fun <T> id(x: T): T = x
            val s = "not crema"
            """.trimIndent()
        assertTrue(CremaInjectionSites.findAll(source).isEmpty())
    }

    @Test
    fun annotatedParameterSameFile() {
        val source =
            """
            fun paint(@CremaTemplate source: String, data: Map<String, Any?> = emptyMap()) {}
            fun go() {
                paint(${"\"\"\""}<h1>{{ title }}</h1>${"\"\"\""})
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.ANNOTATED_PARAMETER, sites[0].reason)
    }

    @Test
    fun javaAnnotatedParameter() {
        val source =
            """
            void paint(@CremaTemplate String source) {}
            void go() {
                paint("<p/>");
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.ANNOTATED_PARAMETER, sites[0].reason)
    }

    @Test
    fun annotatedExpression() {
        val source =
            """
            val html = @CremaTemplate ${"\"\"\""}<em>{{ x }}</em>${"\"\"\""}
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.ANNOTATED_EXPRESSION, sites[0].reason)
    }

    @Test
    fun templateWithoutOverrideOutsideComponentIsIgnored() {
        val source =
            """
            class NotBrew {
                fun template(): String = "<div></div>"
            }
            """.trimIndent()
        assertTrue(CremaInjectionSites.findAll(source).isEmpty())
    }

    @Test
    fun componentTemplateWithoutOverrideStillInjects() {
        val source =
            """
            public class Counter extends Component {
                public String template() {
                    return "<b>{{ count }}</b>";
                }
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        assertEquals(CremaInjectionSites.Reason.TEMPLATE_OVERRIDE, sites[0].reason)
    }

    @Test
    fun extraStringsInTemplateMethodAreNotInjected() {
        val source =
            """
            class Counter : Component() {
                override fun template(): String {
                    val unused = "not crema"
                    return ${"\"\"\""}<div></div>${"\"\"\""}
                }
            }
            """.trimIndent()
        val sites = CremaInjectionSites.findAll(source)
        assertEquals(1, sites.size)
        val body = source.substring(sites[0].contentStart, sites[0].contentEnd)
        assertTrue(body.contains("<div>"))
        assertFalse(body.contains("not crema"))
    }

    @Test
    fun unclosedAndEscapedStrings() {
        assertNotNull(CremaInjectionSites.parseString("\"abc", 0))
        val escaped = CremaInjectionSites.parseString("\"a\\\"b\"", 0)
        assertNotNull(escaped)
        assertEquals("a\\\"b", "\"a\\\"b\"".substring(escaped!!.contentStart, escaped.contentEnd))
        val unclosedBlock = CremaInjectionSites.parseString("${"\"\"\""}no close", 0)
        assertNotNull(unclosedBlock)
        assertTrue(unclosedBlock!!.textBlock)
        assertNull(CremaInjectionSites.parseString("abc", 0))
        assertNull(CremaInjectionSites.parseString("\"", 1))
    }

    @Test
    fun commentsAndCharLiteralsAreSkipped() {
        val source =
            """
            // "not a string"
            /* "also not" */
            val c = '"'
            val s = "plain"
            """.trimIndent()
        assertTrue(CremaInjectionSites.findAll(source).isEmpty())
        assertTrue(CremaInjectionSites.scanLiterals(source).any { !it.textBlock })
    }

    @Test
    fun shouldInjectEdges() {
        assertFalse(CremaInjectionSites.shouldInject("", 0))
        assertFalse(CremaInjectionSites.shouldInject("x", -1))
        assertNull(CremaInjectionSites.siteAt("hi", 99))
        assertNull(CremaInjectionSites.siteAt("hi", -1))
        assertFalse(CremaInjectionSites.isJvmHostLanguage(null))
        assertFalse(CremaInjectionSites.isJvmHostLanguage(""))
        assertTrue(CremaInjectionSites.isJvmHostLanguage("JAVA"))
        assertTrue(CremaInjectionSites.isJvmHostLanguage("kotlin"))
        assertFalse(CremaInjectionSites.isJvmHostLanguage("XML"))
        assertFalse(CremaInjectionSites.isLanguageCrema(null))
        assertFalse(CremaInjectionSites.isLanguageCrema("   "))
        assertTrue(CremaInjectionSites.isLanguageCrema("// language=Crema"))
        assertTrue(CremaInjectionSites.isLanguageCrema("/* language=Crema */"))
        assertFalse(CremaInjectionSites.isLanguageCrema("// language=JSON"))
        assertFalse(CremaInjectionSites.isLanguageCrema("// language=CremaHTML"))
    }

    @Test
    fun cremaContextsTreatInjectedFragmentsAsCrema() {
        assertTrue(CremaContexts.isCremaLanguageId("Crema"))
        assertTrue(CremaContexts.isCremaLanguageId("crema"))
        assertFalse(CremaContexts.isCremaLanguageId(null))
        assertFalse(CremaContexts.isCremaLanguageId(""))
        assertTrue(CremaContexts.looksLikeCrema("home.crema.html"))
        assertTrue(CremaContexts.looksLikeCrema("App.java", languageId = "Crema"))
        assertTrue(CremaContexts.looksLikeCrema("App.java", baseLanguageId = "Crema"))
        assertTrue(
            CremaContexts.looksLikeCrema(
                "Counter.java",
                languageId = "JAVA",
                injected = true,
                injectedLanguageId = "Crema",
            ),
        )
        assertFalse(
            CremaContexts.looksLikeCrema(
                "Counter.java",
                languageId = "JAVA",
                baseLanguageId = "JAVA",
            ),
        )
        assertFalse(
            CremaContexts.looksLikeCrema(
                "Counter.java",
                languageId = "JAVA",
                injected = true,
                injectedLanguageId = "JSON",
            ),
        )
        assertEquals("Crema", CremaContexts.LANGUAGE_ID)
    }

    @Test
    fun brewOwnerPrefersHostClassForInjectedTemplate() {
        val root = Files.createTempDirectory("kona-inject")
        val brew = root.resolve("src/main/java/app/brew")
        val views = root.resolve("src/main/resources/views/brew")
        Files.createDirectories(brew)
        Files.createDirectories(views)
        val java =
            """
            package app.brew;
            import org.konaframework.brew.Component;
            public class Counter extends Component {
                public int count = 0;
                public void increment() {}
                @Override
                public String template() {
                    return ${"\"\"\""}<button wire:click="increment">{{ count }}</button>${"\"\"\""};
                }
            }
            """.trimIndent()
        val host = brew.resolve("Counter.java")
        Files.writeString(host, java)
        Files.writeString(views.resolve("other.crema.html"), "<div></div>")

        val fromHost = BrewInjectionOwners.ownerFor(root, host, java)
        assertNotNull(fromHost)
        assertEquals("Counter", fromHost!!.className)
        assertTrue(fromHost.actions().contains("increment"))
        assertTrue(fromHost.propertiesAndComputed().contains("count"))

        val viewFile = views.resolve("other.crema.html")
        assertNull(BrewInjectionOwners.ownerFor(root, viewFile, "<div></div>"))
        assertNull(BrewInjectionOwners.ownerFor(root, null, null))
        assertNull(BrewInjectionOwners.ownerFor(root, root.resolve("missing.kt"), null))
    }

    @Test
    fun brewOwnerPicksClassAtOffsetWhenFileHasTwoComponents() {
        val source =
            """
            class Alpha : Component() { var a = 1 }
            class Beta : Component() { var b = 2 }
            """.trimIndent()
        val betaAt = source.indexOf("var b")
        val owner = BrewInjectionOwners.ownerFor(
            Files.createTempDirectory("kona-two"),
            null,
            source,
            betaAt,
        )
        assertEquals("Beta", owner!!.className)
        val first = BrewInjectionOwners.ownerFor(
            Files.createTempDirectory("kona-one"),
            null,
            source,
            0,
        )
        assertEquals("Alpha", first!!.className)
    }

    @Test
    fun componentRangesDetectKotlinAndJava() {
        val kt = "class Counter : Component() { override fun template() = \"x\" }"
        assertEquals(1, CremaInjectionSites.componentClassRanges(kt).size)
        val java = "public class Counter extends Component { }"
        assertEquals(1, CremaInjectionSites.componentClassRanges(java).size)
        val other = "class Foo : Bar() { }"
        assertTrue(CremaInjectionSites.componentClassRanges(other).isEmpty())
    }

    @Test
    fun scannerCoverageEdges() {
        val generic =
            """
            class C : Component() {
                override fun template<T>(): String = ${"\"\"\""}<p></p>${"\"\"\""}
            }
            """.trimIndent()
        assertEquals(1, CremaInjectionSites.findAll(generic).size)

        val abs = "abstract fun template(): String"
        assertTrue(CremaInjectionSites.findMethods(abs).any { it.name == "template" })
        assertTrue(CremaInjectionSites.findMethods("fun (").isEmpty())
        assertTrue(CremaInjectionSites.findMethods("public String template();").none { it.name == "template" })
        assertTrue(CremaInjectionSites.findMethods("(").isEmpty())

        val chars =
            """
            fun paint(flag: Char, @CremaTemplate source: String) {}
            fun go() { paint('a', "<p/>") }
            """.trimIndent()
        assertEquals(1, CremaInjectionSites.findAll(chars).size)

        val nested =
            """
            class C : Component() {
                override fun template(): String {
                    /* ignore { } */
                    if (true) { val x = 1 }
                    return ${"\"\"\""}<div></div>${"\"\"\""}
                }
            }
            """.trimIndent()
        assertEquals(1, CremaInjectionSites.findAll(nested).size)

        val unclosed =
            """
            class C : Component() {
                override fun template(): String = "<x/>"
            """.trimIndent()
        assertTrue(CremaInjectionSites.findAll(unclosed).isNotEmpty())

        val root = Files.createTempDirectory("kona-read")
        val brew = root.resolve("src/main/kotlin/app/brew")
        Files.createDirectories(brew)
        val file = brew.resolve("Solo.kt")
        Files.writeString(file, "class Solo : Component() { var n = 1 }")
        assertEquals("Solo", BrewInjectionOwners.ownerFor(root, file, null)!!.className)

        val packed =
            "package app.brew\nclass Alpha : Component() { var a = 1 }\nclass Beta : Component() { var b = 2 }"
        assertEquals("Alpha", BrewInjectionOwners.ownerFor(root, null, packed, 0)!!.className)

        val parenExpr =
            """
            class C : Component() {
                override fun template(): String = (${"\"\"\""}<i></i>${"\"\"\""})
            }
            """.trimIndent()
        assertEquals(1, CremaInjectionSites.findAll(parenExpr).size)
    }

    @Test
    fun findMethodsCoversJavaAndKotlin() {
        val source =
            """
            class C : Component() {
                override fun template(): String = "a"
                fun unused() {
                    val x = "b"
                }
            }
            class J {
                public String template() {
                    return "c";
                }
            }
            """.trimIndent()
        val methods = CremaInjectionSites.findMethods(source)
        assertTrue(methods.any { it.name == "template" && it.expressionBody })
        assertTrue(methods.any { it.name == "unused" })
        assertTrue(methods.any { it.name == "template" && !it.expressionBody })
    }
}
