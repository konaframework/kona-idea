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

val sqliteJdbc: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
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
    sqliteJdbc("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
    systemProperty("kona.sqlite.jdbc.jar", sqliteJdbc.asFileTree.singleFile.absolutePath)
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
              <li>0.5.24 — Wire <code>&lt;wire:*&gt;</code> tags from
                  <code>app.wire</code> (<code>brew:</code> aliases);
                  grind <code>gradle:build</code> / <code>gradle:install</code></li>
              <li>0.5.23 — Cadence columns in any Kotlin/Java file (services,
                  seeders, jobs, <code>Authenticatable</code> params); Kona
                  suggestions sort at the top of the lookup list</li>
              <li>0.5.22 — Cadence columns on Kotlin safe-call
                  <code>user?.getAttribute("…")</code> (DashboardController)</li>
              <li>0.5.21 — Cadence columns from kona new migrations/models:
                  <code>this.</code> / <code>getAttribute</code> in the model,
                  Java <code>User.QUERY</code>, relative JDBC URLs, drivers from
                  <code>kona-runtime.classpath</code></li>
              <li>0.5.19 — Restore Cadence model column completion (user. / getAttribute / where)
              <li>0.5.18 — Reformat Crema views with HTML code-style rules
                  (Ctrl+Alt+L / indent-on-enter). Directives and echoes stay
                  frozen; indent size matches Settings → Editor → Code Style → HTML.</li>
              <li>0.5.17 — Complete Crema echo helpers and conventional variables
                  inside <code>{{ }}</code> / <code>{!! !!}</code> (<code>url</code>,
                  <code>route</code>, <code>asset</code>, <code>csrf_token</code>,
                  <code>old</code>, <code>env</code>, <code>config</code>,
                  <code>trans</code>, <code>lang</code>, <code>loop.index</code>,
                  …) even when the view is not a Brew component.</li>
              <li>0.5.16 — Kona Project View treats project-root <code>public/</code>
                  as the HTTP document directory (Vite <code>public/build</code>).
                  A <code>public</code> folder under <code>resources</code> is not
                  that root.</li>
              <li>0.5.15 — Grind completion includes <code>migrate:fresh</code>,
                  <code>migrate:refresh</code>, <code>migrate:install</code>,
                  <code>db:wipe</code>, <code>db:show</code>, <code>db:table</code>,
                  and <code>schema:dump</code>.</li>
              <li>0.5.14 — Inject Crema into Java/Kotlin strings: <code>@CremaTemplate</code>
                  / <code>template()</code> overrides, <code>inline(source)</code>, and
                  <code>// language=Crema</code>. Completion and navigation work inside
                  those injected fragments (host Component class for Brew members).</li>
              <li>0.5.13 — Cadence columns after <code>Auth.user() as? User</code>
                  / typed locals / Java casts; Kona completions sort first.</li>
              <li>0.5.12 — Cadence instance completion on queried locals;
                  <code>Grind.call</code> command-name completion.</li>
              <li>0.5.11 — Cadence model attribute completion on <code>user.</code>
                  (columns, accessors, relations) and in
                  <code>getAttribute</code> / <code>setAttribute</code> /
                  <code>attribute</code>; fix Kotlin completion dummy offset.</li>
              <li>0.5.10 — Env value completion for known option-like keys in
                  <code>.env</code> / <code>.env.*</code> and
                  <code>Env.getOrDefault</code> defaults (DB connections, log
                  drivers/levels, session/cache/queue/mail/hash, booleans, …).</li>
              <li>0.5.9 — Kona Project View: folders before files (including
                  <code>resources</code> as a real directory node).</li>
              <li>0.5.7 — Kona Project View: <code>public</code> is top-level again;
                  <code>resources</code> no longer nests it.</li>
              <li>0.5.6 — <code>&lt;brew:*&gt;</code> tag completion; Kona Project View
                  default for Kona apps; resources grouping.</li>
              <li>0.5.5 — Brew action/property completion from the owning component
                  class (<code>wire:submit</code>, <code>wire:model</code>,
                  <code>{{ }}</code>); Kona Project View pane for scaffold folders.</li>
              <li>0.5.4 — Known Brew <code>wire:*</code> HTML attribute descriptors;
                  Env key completion and Go to Declaration for
                  <code>Env.get</code> / <code>getOrDefault</code> / <code>require</code>.</li>
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
                    "org.konaframework.idea.CremaLanguageInjector",
                    "org.konaframework.idea.CremaLanguageInjector$*",
                    "org.konaframework.idea.CremaPsi",
                    "org.konaframework.idea.CremaPsi$*",
                    "org.konaframework.idea.CremaBraceMatcher",
                    "org.konaframework.idea.CremaFormattingModelBuilder",
                    "org.konaframework.idea.CremaFormattingModelBuilder$*",
                    "org.konaframework.idea.CremaFileIndentOptionsProvider",
                    "org.konaframework.idea.CremaFileIndentOptionsProvider$*",
                    "org.konaframework.idea.CremaTypedHandler",
                    "org.konaframework.idea.CremaSyntaxHighlighter",
                    "org.konaframework.idea.CremaSyntaxHighlighter*",
                    "org.konaframework.idea.CremaSyntaxHighlighterFactory",
                    "org.konaframework.idea.CremaColors",
                    "org.konaframework.idea.CremaCompletionContributor",
                    "org.konaframework.idea.CremaCompletionContributor$*",
                    "org.konaframework.idea.CremaEchoCompletionContributor",
                    "org.konaframework.idea.CremaEchoCompletionContributor$*",
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
                    "org.konaframework.idea.KonaCompletionWeigher",
                    "org.konaframework.idea.KonaCompletionWeigher$*",
                    "org.konaframework.idea.KonaLookupPublishKt",
                    "org.konaframework.idea.GrindCallCompletionContributor",
                    "org.konaframework.idea.GrindCallCompletionContributor$*",
                    "org.konaframework.idea.FireflyCompletionContributor",
                    "org.konaframework.idea.FireflyCompletionContributor$*",
                    "org.konaframework.idea.XComponentCompletionContributor",
                    "org.konaframework.idea.XComponentCompletionContributor$*",
                    "org.konaframework.idea.BrewTagCompletionContributor",
                    "org.konaframework.idea.BrewTagCompletionContributor$*",
                    "org.konaframework.idea.BrewTagAttributeCompletionContributor",
                    "org.konaframework.idea.BrewTagAttributeCompletionContributor$*",
                    "org.konaframework.idea.BrewTagDescriptorsProvider",
                    "org.konaframework.idea.BrewTagDescriptorsProvider$*",
                    "org.konaframework.idea.BrewTagGotoDeclarationHandler",
                    "org.konaframework.idea.BrewTagNavigation",
                    "org.konaframework.idea.BrewTagNavigation$*",
                    "org.konaframework.idea.BrewWireCompletionContributor",
                    "org.konaframework.idea.BrewWireCompletionContributor$*",
                    "org.konaframework.idea.BrewMemberCompletionContributor",
                    "org.konaframework.idea.BrewMemberCompletionContributor$*",
                    "org.konaframework.idea.BrewMemberGotoDeclarationHandler",
                    "org.konaframework.idea.BrewMemberNavigation",
                    "org.konaframework.idea.BrewMemberNavigation$*",
                    "org.konaframework.idea.BrewWireAnnotator",
                    "org.konaframework.idea.BrewWireAttributeDescriptorsProvider",
                    "org.konaframework.idea.BrewWireAttributeDescriptorsProvider*",
                    "org.konaframework.idea.KonaProjectViewPane",
                    "org.konaframework.idea.KonaProjectViewPane$*",
                    "org.konaframework.idea.KonaProjectRootNode",
                    "org.konaframework.idea.KonaProjectRootNode$*",
                    "org.konaframework.idea.KonaScaffoldDirectoryNode",
                    "org.konaframework.idea.KonaResourcesDirectoryNode",
                    "org.konaframework.idea.EnvKeyCompletionContributor",
                    "org.konaframework.idea.EnvKeyCompletionContributor*",
                    "org.konaframework.idea.EnvValueCompletionContributor",
                    "org.konaframework.idea.EnvValueCompletionContributor*",
                    "org.konaframework.idea.EnvKeyReference",
                    "org.konaframework.idea.EnvKeyReferenceKt",
                    "org.konaframework.idea.EnvKeyReferenceContributor",
                    "org.konaframework.idea.EnvKeyReferenceContributor*",
                    "org.konaframework.idea.EnvGotoDeclarationHandler",
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
