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
    // Token: PUBLISH_TOKEN (official) or JETBRAINS_PUBLISH_TOKEN.
    // Signing is optional. Empty CERTIFICATE_CHAIN / PRIVATE_KEY skips signPlugin.
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
        id = "org.konaframework.idea"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
        // Description stays in plugin.xml so the Marketplace card keeps the HTML,
        // including the Getting Started steps. Do not set description here.
        changeNotes.set(
            """
            <ul>
              <li>0.5.3 — Marketplace description: Kona Framework IDE support,
                  with Getting Started install and use steps.</li>
              <li>0.5.2 — Supported IDEs limited to IntelliJ IDEA (Community and
                  Ultimate) via <code>com.intellij.modules.idea</code>.</li>
              <li>0.5.1 — Marketplace <code>pluginIcon</code> (+ dark) uses the caramel
                  mug mark from monorepo <code>kona-logo.svg</code>.</li>
              <li>0.5.0 — Plugin id/package <code>org.konaframework.idea</code>;
                  Brew <code>wire:*</code> completion + annotator;
                  <code>Inertia.render</code> page-name patterns; Inertia live/file
                  templates; Brew+Inertia help / tool window; Maven group
                  <code>org.konaframework</code> (kona-brew / kona-inertia).</li>
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
                    "org.konaframework.idea.CremaEditorHighlighterProvider",
                    "org.konaframework.idea.CremaEditorHighlighterProvider*",
                    "org.konaframework.idea.CremaEditorHighlighter",
                    "org.konaframework.idea.CremaEditorHighlighter*",
                    "org.konaframework.idea.CremaFileTypeOverrider",
                    "org.konaframework.idea.CremaFileTypeDetector",
                    "org.konaframework.idea.CremaFileViewProvider*",
                    "org.konaframework.idea.CremaParserDefinition",
                    "org.konaframework.idea.CremaParserDefinition*",
                    "org.konaframework.idea.CremaPsiParser",
                    "org.konaframework.idea.CremaFile",
                    "org.konaframework.idea.CremaFileType",
                    "org.konaframework.idea.CremaLanguage",
                    "org.konaframework.idea.CremaLanguage$*",
                    "org.konaframework.idea.CremaLanguageKt",
                    "org.konaframework.idea.CremaBraceMatcher",
                    "org.konaframework.idea.CremaTypedHandler",
                    "org.konaframework.idea.CremaSyntaxHighlighter",
                    "org.konaframework.idea.CremaSyntaxHighlighter*",
                    "org.konaframework.idea.CremaSyntaxHighlighterFactory",
                    "org.konaframework.idea.CremaColors",
                    "org.konaframework.idea.CremaCompletionContributor",
                    "org.konaframework.idea.CremaCompletionContributor$*",
                    "org.konaframework.idea.CremaCharFilter",
                    "org.konaframework.idea.CremaAnnotator",
                    "org.konaframework.idea.ViewNameCompletionContributor",
                    "org.konaframework.idea.ViewNameCompletionContributor$*",
                    "org.konaframework.idea.ViewNameReference",
                    "org.konaframework.idea.ViewNameReferenceContributor",
                    "org.konaframework.idea.ViewNameReferenceContributor$*",
                    "org.konaframework.idea.ViewGotoDeclarationHandler",
                    "org.konaframework.idea.DbColumnCompletionContributor",
                    "org.konaframework.idea.DbColumnCompletionContributor$*",
                    "org.konaframework.idea.ModelPropertyCompletionContributor",
                    "org.konaframework.idea.ModelPropertyCompletionContributor$*",
                    "org.konaframework.idea.XComponentCompletionContributor",
                    "org.konaframework.idea.XComponentCompletionContributor$*",
                    "org.konaframework.idea.BrewWireCompletionContributor",
                    "org.konaframework.idea.BrewWireCompletionContributor$*",
                    "org.konaframework.idea.BrewWireAnnotator",
                    "org.konaframework.idea.KonaConfigurable",
                    "org.konaframework.idea.KonaConfigurable$*",
                    "org.konaframework.idea.KonaToolWindowFactory",
                    "org.konaframework.idea.KonaQuickAccessPanel",
                    "org.konaframework.idea.KonaQuickAccessPanel$*",
                    "org.konaframework.idea.GrindQuickActions",
                    "org.konaframework.idea.GrindQuickActions$*",
                    "org.konaframework.idea.KonaIcons",
                    "org.konaframework.idea.GrindConfigurationType",
                    "org.konaframework.idea.GrindConfigurationType$*",
                    "org.konaframework.idea.GrindConfigurationFactory",
                    "org.konaframework.idea.GrindRunConfiguration",
                    "org.konaframework.idea.GrindRunConfiguration$*",
                    "org.konaframework.idea.GrindRunConfigurationOptions",
                    "org.konaframework.idea.GrindRunConfigurationOptions$*",
                    "org.konaframework.idea.GrindSettingsEditor",
                    "org.konaframework.idea.GrindSettingsEditor$*",
                    "org.konaframework.idea.KonaProjectOpenActivity",
                    "org.konaframework.idea.KonaProjectOpenActivityKt",
                    "org.konaframework.idea.KonaFolderIconProvider",
                    "org.konaframework.idea.KonaFolderIconProvider$*",
                    "org.konaframework.idea.KonaFileTemplates",
                    "org.konaframework.idea.KonaFileTemplates$*",
                    "org.konaframework.idea.NewCremaViewAction",
                    "org.konaframework.idea.ShowLayoutHelpAction",
                    "org.konaframework.idea.ShowArtifactsHelpAction",
                    "org.konaframework.idea.ShowRoutingHelpAction",
                    "org.konaframework.idea.ShowViewNavHelpAction",
                    "org.konaframework.idea.ShowBrewInertiaHelpAction",
                    "org.konaframework.idea.ShowKonaHelpActionsKt",
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
