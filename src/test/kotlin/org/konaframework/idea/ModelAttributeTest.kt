package org.konaframework.idea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelAttributeTest {
    @Test
    fun completionTextRewindsDummyIdentifier() {
        val typed = "user.em"
        val withDummy = typed + CompletionText.DUMMY
        assertEquals(typed.length, CompletionText.offsetBeforeDummy(withDummy, withDummy.length))
        assertEquals(
            "user.".length,
            CompletionText.offsetBeforeDummy("user." + CompletionText.DUMMY, ("user." + CompletionText.DUMMY).length),
        )
        assertEquals("em", CompletionText.stripDummy("em" + CompletionText.DUMMY))
        assertEquals("", CompletionText.stripDummy(CompletionText.DUMMY))
        assertEquals("email", CompletionText.stripDummy("email"))
        assertEquals(0, CompletionText.offsetBeforeDummy("", 0))
        assertEquals(0, CompletionText.offsetBeforeDummy("x", 0))
        assertEquals(3, CompletionText.offsetBeforeDummy("abc", 3))
        assertEquals(2, CompletionText.offsetBeforeDummy("a.", 2))
        assertTrue(CompletionText.isIdentPart('_'))
        assertFalse(CompletionText.isIdentPart('.'))
        val snap = CompletionText.fromContents(withDummy, withDummy.length + 99)
        assertEquals(typed.length, snap.offset)
        assertEquals(withDummy, snap.text.toString())
    }

    @Test
    fun completionTextDoesNotClampAgainstWrongFile() {
        // Simulates the bug: offset into a longer copy must not be coerced to a shorter original.
        val original = "val user = User.find(1)\nuser."
        val copy = original + CompletionText.DUMMY
        val offsetInCopy = copy.length
        assertTrue(offsetInCopy > original.length)
        val fixed = CompletionText.offsetBeforeDummy(copy, offsetInCopy)
        val prop = ModelPropertyPatterns.propertyAt(copy, fixed)!!
        assertEquals("user", prop.receiver)
        assertEquals("", prop.prefix)
        assertEquals("User", prop.modelClass)
    }

    @Test
    fun modelIndexMatchesScaffoldUserWithInterfaces() {
        val kt =
            """
            class User : Cadence<User>(), Authenticatable, HasCredentials, HasEmail {
              override val table = "users"
            }
            """.trimIndent()
        val parsed = ModelIndex.parseSource(kt)
        assertEquals(1, parsed.size)
        assertEquals("User", parsed.first().className)
        assertEquals("users", parsed.first().table)

        val java =
            """
            public class User extends Cadence<User> implements Authenticatable, HasEmail {
              public String getTable() { return "accounts"; }
            }
            """.trimIndent()
        assertEquals("accounts", ModelIndex.parseSource(java).first().table)
    }

    @Test
    fun accessorStudlyMapsToSnakeAndCamel() {
        assertEquals("full_name", ModelAttributeIndex.studlyToSnake("FullName"))
        assertEquals("fullName", ModelAttributeIndex.studlyToCamel("FullName"))
        assertEquals("a", ModelAttributeIndex.studlyToSnake("A"))
        assertEquals("display_name", ModelAttributeIndex.studlyToSnake("DisplayName"))
    }

    @Test
    fun parseSourceCollectsAccessorsMutatorsAppendsRelations() {
        val source =
            """
            class User : Cadence<User>() {
              override val appends = setOf("label", "full_name")
              fun getFullNameAttribute(): String = "x"
              fun setPasswordAttribute(value: Any?) {}
              fun posts() = hasMany(Post::class.java, "user_id", "id")
              fun profile(): HasOne<Profile> = hasOne(Profile::class.java)
              fun team() = belongsTo(Team::class.java)
            }
            """.trimIndent()
        val attrs = ModelAttributeIndex.parseSource(source)
        val byName = attrs.associateBy { it.name }
        assertEquals(ModelAttributeIndex.Kind.ACCESSOR, byName["full_name"]!!.kind)
        assertEquals("accessor", byName["full_name"]!!.typeText)
        assertNotNull(byName["fullName"])
        assertEquals(ModelAttributeIndex.Kind.ACCESSOR, byName["password"]!!.kind)
        assertEquals(ModelAttributeIndex.Kind.APPEND, byName["label"]!!.kind)
        assertEquals(ModelAttributeIndex.Kind.RELATION, byName["posts"]!!.kind)
        assertTrue(byName["posts"]!!.typeText.contains("HasMany"))
        assertTrue(byName["profile"]!!.typeText.contains("HasOne"))
        assertTrue(byName["team"]!!.typeText.contains("BelongsTo"))
        assertFalse(attrs.any { it.name == "appended" })
    }

    @Test
    fun javaRelationsAndAccessors() {
        val java =
            """
            public class User extends Cadence<User> {
              public String getDisplayNameAttribute() { return "x"; }
              public HasMany<Post> posts() { return hasMany(Post.class, "user_id"); }
            }
            """.trimIndent()
        val attrs = ModelAttributeIndex.parseSource(java)
        assertTrue(attrs.any { it.name == "display_name" })
        assertTrue(attrs.any { it.name == "posts" && it.typeText.contains("HasMany") })
    }

    @Test
    fun attributesForMergesColumnsAndSource() {
        val root = Files.createTempDirectory("kona-attrs")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateUsersTable.kt"),
            """schema.create("users") { id(); string("email"); string("name") }""",
        )
        val models = root.resolve("src/main/kotlin/app/Models")
        Files.createDirectories(models)
        Files.writeString(
            models.resolve("User.kt"),
            """
            package app.Models
            class User : Cadence<User>(), Authenticatable {
              fun getFullNameAttribute() = "x"
              fun posts() = hasMany(Post::class.java, "user_id", "id")
            }
            """.trimIndent(),
        )
        val attrs =
            ModelAttributeIndex.attributesFor(
                root,
                "User",
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        val names = attrs.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("email", "name", "full_name", "fullName", "posts")))
        assertEquals("column · users", attrs.first { it.name == "email" }.typeText)
        assertNull(ModelIndex.find(root, "NotAModel"))
        assertNotNull(ModelIndex.find(root, "User"))
    }

    @Test
    fun getAttributeKeyContext() {
        val src = """user.getAttribute("em"""
        val ctx = AttributeKeyPatterns.keyContextAt(src, src.length)!!
        assertEquals("em", ctx.prefix)
        assertEquals("user", ctx.receiver)

        val set = """user.setAttribute("na"""
        assertEquals("user", AttributeKeyPatterns.keyContextAt(set, set.length)!!.receiver)

        val raw = """user.getRawAttribute("id"""
        assertEquals("id", AttributeKeyPatterns.keyContextAt(raw, raw.length)!!.prefix)

        val has = """user.hasAttribute("full"""
        assertEquals("full", AttributeKeyPatterns.keyContextAt(has, has.length)!!.prefix)

        val attr = """user.attribute("x"""
        assertEquals("x", AttributeKeyPatterns.keyContextAt(attr, attr.length)!!.prefix)

        assertNull(AttributeKeyPatterns.keyContextAt("""println("em""", """println("em""".length))
    }

    @Test
    fun propertyAtWithDummyPrefix() {
        val src = "user." + CompletionText.DUMMY
        val offset = CompletionText.offsetBeforeDummy(src, src.length)
        val prop = ModelPropertyPatterns.propertyAt(src, offset)!!
        assertEquals("", prop.prefix)
        assertEquals("user", prop.receiver)

        val partial = "user.em" + CompletionText.DUMMY
        val off2 = CompletionText.offsetBeforeDummy(partial, partial.length)
        val prop2 = ModelPropertyPatterns.propertyAt(partial, off2)!!
        assertEquals("em", prop2.prefix)
    }

    @Test
    fun javaAssignmentResolvesModelClass() {
        val src =
            """
            User user = User.find(1);
            user.em
            """.trimIndent()
        val offset = src.indexOf("em") + 2
        val prop = ModelPropertyPatterns.propertyAt(src, offset)!!
        assertEquals("user", prop.receiver)
        assertEquals("User", prop.modelClass)

        val loose =
            """
            var user = User.find(1);
            user.na
            """.trimIndent()
        // Java-style `var user = User.` — type token is `var`, prefer RHS class.
        assertEquals(
            "User",
            ModelPropertyPatterns.resolveModelClass(loose, "user"),
        )
    }

    @Test
    fun parseSourceEdgeCasesAndJavaAppends() {
        assertTrue(ModelAttributeIndex.parseSource("").isEmpty())
        assertEquals("", ModelAttributeIndex.studlyToSnake(""))
        assertEquals("", ModelAttributeIndex.studlyToCamel(""))
        val merged = ModelAttributeIndex.merge(setOf("id"), "", emptyList())
        assertEquals("column", merged.first().typeText)

        val javaAppends =
            """
            public class User extends Cadence<User> {
              private final Set<String> appends = Set.of("label", "");
              public void setDisplayNameAttribute(Object value) {}
              public Set<String> getAppends() { return Set.of("extra"); }
            }
            """.trimIndent()
        val attrs = ModelAttributeIndex.parseSource(javaAppends)
        assertTrue(attrs.any { it.name == "label" })
        assertTrue(attrs.any { it.name == "display_name" })
        // Field wins over getter when both match; empty string literal skipped.
        assertFalse(attrs.any { it.name.isEmpty() })
    }

    @Test
    fun attributesForUnknownModelIsEmpty() {
        val root = Files.createTempDirectory("kona-nomodel")
        assertTrue(
            ModelAttributeIndex.attributesFor(
                root,
                "Ghost",
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            ).isEmpty(),
        )
        assertNull(ModelIndex.tableForClass(root, "notAClass"))
        assertEquals("ghosts", ModelIndex.tableForClass(root, "Ghost"))
    }

    @Test
    fun attributeKeyEdgesAndRawSetter() {
        assertNull(AttributeKeyPatterns.keyContextAt("x", 0))
        assertFalse(AttributeKeyPatterns.isAttributeKeyContext("x", 0))
        assertFalse(AttributeKeyPatterns.isAttributeKeyContext("\"", 0))
        val rawSet = """user.setRawAttribute("tok"""
        val ctx = AttributeKeyPatterns.keyContextAt(rawSet, rawSet.length)!!
        assertEquals("tok", ctx.prefix)
        assertEquals("user", ctx.receiver)
        assertNull(AttributeKeyPatterns.receiverBeforeQuote("""getAttribute(""", """getAttribute(""".length))
    }

    @Test
    fun resolveModelClassFromContributorHelper() {
        assertNull(ModelPropertyCompletionContributor.resolveModelClass("x", null))
        assertNull(ModelPropertyCompletionContributor.resolveModelClass("x", ""))
        assertEquals("User", ModelPropertyCompletionContributor.resolveModelClass("x", "User"))
        assertEquals(
            "User",
            ModelPropertyCompletionContributor.resolveModelClass("val user = User.find(1)", "user"),
        )
    }
}
