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
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    publishing {
        token.set(providers.environmentVariable("JETBRAINS_PUBLISH_TOKEN"))
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
            Kona Idea — native Crema highlighting and directive intelligence for
            IntelliJ IDEA. Install from a GitHub release zip or the JetBrains
            Marketplace when published.
            """.trimIndent(),
        )
        changeNotes.set(
            """
            <ul>
              <li>0.1.0 — Initial release: native Crema file type
                  (<code>*.crema.html</code> / <code>*.crema.kt</code>), HTML-layered
                  highlighter, directive completion, unknown-directive annotator,
                  <code>{{ }}</code> typed handler, Grind run configuration.</li>
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
                    "dev.kona.idea.CremaAnnotator",
                    "dev.kona.idea.GrindConfigurationType",
                    "dev.kona.idea.GrindConfigurationType$*",
                    "dev.kona.idea.GrindConfigurationFactory",
                    "dev.kona.idea.GrindRunConfiguration",
                    "dev.kona.idea.GrindRunConfiguration$*",
                    "dev.kona.idea.GrindSettingsEditor",
                    "dev.kona.idea.ShowLspHelpAction",
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
