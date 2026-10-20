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
        assertTrue(
            ModelAttributeIndex.attributesFor(
                root,
                "",
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            ).isEmpty(),
        )
        assertTrue(
            ModelAttributeIndex.attributesFor(
                root,
                "user",
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            ).isEmpty(),
        )
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
        assertEquals(
            "User",
            ModelPropertyCompletionContributor.resolveModelClass("fun show(user: User) { user. }", "user"),
        )
    }

    @Test
    fun dummyIdentifierWithSpaceAndPartialPrefix() {
        val spaced = "user." + CompletionText.DUMMY_WITH_SPACE
        val off = CompletionText.offsetBeforeDummy(spaced, spaced.length)
        val prop = ModelPropertyPatterns.propertyAt(spaced, off)!!
        assertEquals("user", prop.receiver)
        assertEquals("", prop.prefix)

        val dummyStart = CompletionText.indexOfDummy("emIntell")
        assertTrue(dummyStart > 0)
        assertEquals("em", CompletionText.stripDummy("emIntell"))
        assertEquals(-1, CompletionText.indexOfDummy("email"))
        assertTrue(CompletionText.DUMMY_WITH_SPACE.endsWith(' '))
        val trailingSpace = "user.em "
        assertEquals(trailingSpace.length, CompletionText.offsetBeforeDummy(trailingSpace, trailingSpace.length))
    }

    @Test
    fun getAttributeUnquotedAndGetAndIndex() {
        val bare = "user.getAttribute("
        val ctx = AttributeKeyPatterns.keyContextAt(bare, bare.length)!!
        assertEquals("", ctx.prefix)
        assertEquals("user", ctx.receiver)
        assertFalse(ctx.quoted)

        val get = """user.get("em"""
        val g = AttributeKeyPatterns.keyContextAt(get, get.length)!!
        assertEquals("em", g.prefix)
        assertEquals("user", g.receiver)
        assertTrue(g.quoted)

        val idx = """user["na"""
        val i = AttributeKeyPatterns.keyContextAt(idx, idx.length)!!
        assertEquals("na", i.prefix)
        assertEquals("user", i.receiver)

        val safeIdx = """user?.["id"""
        assertEquals("user", AttributeKeyPatterns.keyContextAt(safeIdx, safeIdx.length)!!.receiver)

        val staticGet = "User.get("
        assertNull(AttributeKeyPatterns.unquotedCallContext(staticGet, staticGet.length))

        val setBare = "user.set("
        val setCtx = AttributeKeyPatterns.unquotedCallContext(setBare, setBare.length)!!
        assertEquals("user", setCtx.receiver)
        assertFalse(setCtx.quoted)

        val indexQuote = "user[\""
        assertTrue(AttributeKeyPatterns.isAttributeKeyContext(indexQuote, indexQuote.length - 1))
        assertEquals("user", AttributeKeyPatterns.receiverBeforeQuote(indexQuote, indexQuote.length - 1))

        val dirty = """user.isDirty("em"""
        assertEquals("user", AttributeKeyPatterns.keyContextAt(dirty, dirty.length)!!.receiver)
    }

    @Test
    fun kotlinParamAndJavaParamResolveModel() {
        val kt = "fun show(user: User?) {\n  user.em"
        assertEquals("User", ModelPropertyPatterns.resolveModelClass(kt, "user"))
        val java = "void show(User user) {\n  user.em"
        assertEquals("User", ModelPropertyPatterns.resolveModelClass(java, "user"))
        val javaGeneric = "void show(User<String> user) { user."
        assertEquals("User", ModelPropertyPatterns.resolveModelClass(javaGeneric, "user"))
    }

    @Test
    fun safeCallPropertyAccess() {
        val src = "user?.em"
        val prop = ModelPropertyPatterns.propertyAt(src, src.length)!!
        assertEquals("user", prop.receiver)
        assertEquals("em", prop.prefix)
    }

    @Test
    fun fillableCastsAndTypicalColumns() {
        val source =
            """
            class User : Cadence<User>(), SoftDeletes {
              override val fillable = setOf("name", "email")
              override val hidden = setOf("password")
              override val casts = mapOf("admin" to CastType.BOOLEAN)
            }
            """.trimIndent()
        val parsed = ModelAttributeIndex.parseSource(source)
        assertTrue(parsed.any { it.name == "name" && it.kind == ModelAttributeIndex.Kind.FILLABLE })
        assertTrue(parsed.any { it.name == "admin" && it.kind == ModelAttributeIndex.Kind.CAST })
        assertTrue(parsed.any { it.name == "password" })
        val typical = ModelAttributeIndex.typicalCadenceColumns(source)
        assertTrue(typical.containsAll(listOf("id", "created_at", "updated_at", "deleted_at")))
        val noTs = ModelAttributeIndex.typicalCadenceColumns("override val timestamps = false")
        assertTrue(noTs.contains("id"))
        assertFalse(noTs.contains("created_at"))
        val javaTs = ModelAttributeIndex.typicalCadenceColumns("boolean getTimestamps() { return false; }")
        assertFalse(javaTs.contains("updated_at"))
    }

    @Test
    fun javaFillableAndCasts() {
        val java =
            """
            public class User extends Cadence<User> {
              private final Set<String> fillable = Set.of("name");
              public Set<String> getFillable() { return Set.of("email"); }
              public Set<String> getHidden() { return Set.of("password"); }
              public Map<String, Object> getCasts() { return Map.of("admin", CastType.BOOLEAN); }
            }
            """.trimIndent()
        val parsed = ModelAttributeIndex.parseSource(java)
        assertTrue(parsed.any { it.name == "name" })
        assertTrue(parsed.any { it.name == "password" })
        assertTrue(parsed.any { it.name == "admin" && it.kind == ModelAttributeIndex.Kind.CAST })
    }

    @Test
    fun attributesForUsesMigrationsWithoutModelFile() {
        val root = Files.createTempDirectory("kona-nofil")
        val mig = root.resolve("src/main/kotlin/database/migrations")
        Files.createDirectories(mig)
        Files.writeString(
            mig.resolve("CreateUsersTable.kt"),
            """schema.create("users") { id(); string("email"); timestamps() }""",
        )
        val attrs =
            ModelAttributeIndex.attributesFor(
                root,
                "User",
                mode = ColumnSourceMode.MIGRATIONS_ONLY,
                probeJdbc = false,
            )
        val names = attrs.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("id", "email", "created_at", "updated_at")))
        assertTrue(ModelIndex.modelSearchRoots(root).isNotEmpty())

        val domain = root.resolve("src/main/kotlin/app/domain")
        Files.createDirectories(domain)
        Files.writeString(domain.resolve("Account.kt"), "class Account : Cadence<Account>()")
        assertNotNull(ModelIndex.find(root, "Account"))
        assertEquals("accounts", ModelIndex.find(root, "Account")!!.table)
    }

    @Test
    fun modelCompletionRequestAtPropertyAndKey() {
        val propSrc = "val user = User.query().first()\nuser."
        val prop = ModelCompletion.requestAt(propSrc, propSrc.length)!!
        assertEquals("User", prop.modelClass)
        assertEquals("", prop.prefix)
        assertFalse(prop.isKey)

        val keySrc = """user.getAttribute("""
        val key = ModelCompletion.requestAt(keySrc, keySrc.length)!!
        assertEquals("User", key.modelClass)
        assertTrue(key.isKey)
        assertFalse(key.quoted)

        assertNull(ModelCompletion.requestAt("println(", "println(".length))
    }

    @Test
    fun appRootNearWalksToKonaApp() {
        val root = Files.createTempDirectory("kona-approot")
        Files.writeString(root.resolve("build.gradle.kts"), "implementation(\"org.konaframework:kona-http:1\")\n")
        Files.writeString(root.resolve("grind"), "#!/bin/sh\n")
        Files.createDirectories(root.resolve("src/main/kotlin/bootstrap"))
        Files.createDirectories(root.resolve("src/main/kotlin/routes"))
        val nested = root.resolve("src/main/kotlin/app/http/controllers")
        Files.createDirectories(nested)
        assertEquals(
            root.toAbsolutePath().normalize(),
            KonaLayout.appRootNear(nested),
        )
        assertNull(KonaLayout.appRootNear(null))
        assertNull(KonaLayout.appRootNear(Files.createTempDirectory("not-kona").resolve("src")))
    }
}
