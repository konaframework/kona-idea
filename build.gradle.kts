import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
}

kotlin {
    // Platform 2024.2+ prefers JDK 21 for runIde; CI uses Temurin 21.
    jvmToolchain(21)
}

intellijPlatform {
    // Marketplace: first upload must be manual; later versions use publishPlugin.
    // Token: PUBLISH_TOKEN (official) or JETBRAINS_PUBLISH_TOKEN. Signing via CERTIFICATE_*.
    publishing {
        token.set(
            providers.environmentVariable("PUBLISH_TOKEN")
                .orElse(providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN")),
        )
        // Unset → Stable ("default") channel. Override with PUBLISH_CHANNEL=beta|alpha|eap|…
        channels.set(
            providers.environmentVariable("PUBLISH_CHANNEL")
                .map { name -> listOf(name.trim()).filter { it.isNotEmpty() } }
                .orElse(providers.provider { listOf("default") }),
        )
    }
    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
    pluginConfiguration {
        id = "dev.kona.idea"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        description.set(
            """
            Kona Idea — native Crema + Grind support (no LSP): @directive autopopup,
            &lt;x-*&gt; components, view-name completion + Go to Declaration,
            Cadence model/DB column completion (scoped + Settings → Tools → Kona),
            right-side Kona tool window, scaffold markers, and Netty-aware
            ./grind serve (port 4000).
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.4.0 — Crema <code>@</code> autopopup + filter-as-you-type;
                  <code>&lt;x-*&gt;</code> / <code>&lt;x-slot&gt;</code> completion;
                  Cadence model attribute completion; DB columns scoped to the
                  model table; Settings → Tools → Kona column source
                  (Connection / Migrations / Both).</li>
              <li>0.3.1 — Rename Extract → Cadence.</li>
              <li>0.3.0 — Fully native IDE features (no LSP): Crema
                  <code>@</code> completion, view navigation, DB columns,
                  Kona tool window, brand icons.</li>
              <li>0.2.0 — Netty serve, scaffold markers, live/file templates.</li>
              <li>0.1.0 — Initial release.</li>
            </ul>
            """.trimIndent(),
        )
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    check {
        dependsOn("koverVerify")
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "dev.kona.idea.CremaEditorHighlighterProvider",
                    "dev.kona.idea.CremaEditorHighlighterProvider*",
                    "dev.kona.idea.CremaEditorHighlighter",
                    "dev.kona.idea.CremaEditorHighlighter*",
                    "dev.kona.idea.CremaFileTypeOverrider",
                    "dev.kona.idea.CremaFileTypeDetector",
                    "dev.kona.idea.CremaFileViewProvider*",
                    "dev.kona.idea.CremaParserDefinition",
                    "dev.kona.idea.CremaParserDefinition*",
                    "dev.kona.idea.CremaPsiParser",
                    "dev.kona.idea.CremaFile",
                    "dev.kona.idea.CremaFileType",
                    "dev.kona.idea.CremaLanguage",
                    "dev.kona.idea.CremaLanguage$*",
                    "dev.kona.idea.CremaLanguageKt",
                    "dev.kona.idea.CremaBraceMatcher",
                    "dev.kona.idea.CremaTypedHandler",
                    "dev.kona.idea.CremaSyntaxHighlighter",
                    "dev.kona.idea.CremaSyntaxHighlighter*",
                    "dev.kona.idea.CremaSyntaxHighlighterFactory",
                    "dev.kona.idea.CremaColors",
                    "dev.kona.idea.CremaCompletionContributor",
                    "dev.kona.idea.CremaCompletionContributor$*",
                    "dev.kona.idea.CremaCharFilter",
                    "dev.kona.idea.CremaAnnotator",
                    "dev.kona.idea.ViewNameCompletionContributor",
                    "dev.kona.idea.ViewNameCompletionContributor$*",
                    "dev.kona.idea.ViewNameReference",
                    "dev.kona.idea.ViewNameReferenceContributor",
                    "dev.kona.idea.ViewNameReferenceContributor$*",
                    "dev.kona.idea.ViewGotoDeclarationHandler",
                    "dev.kona.idea.DbColumnCompletionContributor",
                    "dev.kona.idea.DbColumnCompletionContributor$*",
                    "dev.kona.idea.ModelPropertyCompletionContributor",
                    "dev.kona.idea.ModelPropertyCompletionContributor$*",
                    "dev.kona.idea.XComponentCompletionContributor",
                    "dev.kona.idea.XComponentCompletionContributor$*",
                    "dev.kona.idea.KonaConfigurable",
                    "dev.kona.idea.KonaConfigurable$*",
                    "dev.kona.idea.KonaToolWindowFactory",
                    "dev.kona.idea.KonaQuickAccessPanel",
                    "dev.kona.idea.KonaQuickAccessPanel$*",
                    "dev.kona.idea.GrindQuickActions",
                    "dev.kona.idea.GrindQuickActions$*",
                    "dev.kona.idea.KonaIcons",
                    "dev.kona.idea.GrindConfigurationType",
                    "dev.kona.idea.GrindConfigurationType$*",
                    "dev.kona.idea.GrindConfigurationFactory",
                    "dev.kona.idea.GrindRunConfiguration",
                    "dev.kona.idea.GrindRunConfiguration$*",
                    "dev.kona.idea.GrindRunConfigurationOptions",
                    "dev.kona.idea.GrindRunConfigurationOptions$*",
                    "dev.kona.idea.GrindSettingsEditor",
                    "dev.kona.idea.GrindSettingsEditor$*",
                    "dev.kona.idea.KonaProjectOpenActivity",
                    "dev.kona.idea.KonaProjectOpenActivityKt",
                    "dev.kona.idea.KonaFolderIconProvider",
                    "dev.kona.idea.KonaFolderIconProvider$*",
                    "dev.kona.idea.KonaFileTemplates",
                    "dev.kona.idea.KonaFileTemplates$*",
                    "dev.kona.idea.NewCremaViewAction",
                    "dev.kona.idea.ShowLayoutHelpAction",
                    "dev.kona.idea.ShowArtifactsHelpAction",
                    "dev.kona.idea.ShowRoutingHelpAction",
                    "dev.kona.idea.ShowViewNavHelpAction",
                    "dev.kona.idea.ShowKonaHelpActionsKt",
                )
            }
        }
        verify {
            rule {
                bound {
                    minValue.set(98)
                    coverageUnits.set(CoverageUnit.LINE)
                    aggregationForGroup.set(AggregationType.COVERED_PERCENTAGE)
                }
            }
        }
    }
}
