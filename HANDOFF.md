# Agent handoff — Kona Idea

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

**Framework monorepo:** [`konaframework/kona`](https://github.com/konaframework/kona) — see [`docs/governance/HANDOFF.md`](https://github.com/konaframework/kona/blob/main/docs/governance/HANDOFF.md).

## Read first

1. [README.md](README.md) — install, features, develop commands.
2. [CHANGELOG.md](CHANGELOG.md) — release notes.
3. Almasix reference (native Prism pattern this repo follows): local clone often at `/home/smaosa/Projects/almasix-idea` (`almasix-dev/almasix-idea`).
4. Keep directive names in sync with kona `modules/lsp` → `dev.kona.lsp.CremaDirectives`.

## Where we are

| Item | Value |
|------|--------|
| Repo | https://github.com/konaframework/kona-idea |
| Plugin id | `dev.kona.idea` |
| Version | **0.1.0** (tag `v0.1.0`) |
| Latest zip | [kona-idea-0.1.0.zip](https://github.com/konaframework/kona-idea/releases/tag/v0.1.0) |
| Platform | IntelliJ IDEA Community/Ultimate — `platformType=IC`, `platformVersion=2024.2.5`, `sinceBuild=242` |
| Package | `dev.kona.idea` |
| Coverage | Kover ≥ **98%** line on verified set (Platform wiring excluded in `build.gradle.kts`) |
| Models | **Auto** unless the human names a model |
| CI | `.github/workflows/ci.yml` — `test` + `koverVerify` + `buildPlugin` |
| Release | Tag `v*` → `.github/workflows/publish.yml` attaches zip to GitHub Release |

## What 0.1.0 ships

- Crema file type for `*.crema.html` / `*.crema.kt` (overrider + detector beat HTML)
- Native `CremaLexer` + layered HTML `EditorHighlighter`
- `@directive` completion + unknown-directive annotator (skips email-like `user@host`)
- `{{` typed handler → `{{  }}`
- Grind run configuration (`./grind …`)
- Menu **Kona → Show kona-lsp Setup Help** (does **not** start LSP)

## Layout

```text
src/main/kotlin/dev/kona/idea/
  CremaLanguage.kt          # language + tokens + lexer
  CremaDirectives.kt        # known @names (sync with kona-lsp)
  CremaFileType*.kt         # filetype / overrider / detector
  CremaParserDefinition.kt / CremaFileViewProvider.kt
  CremaSyntaxHighlighter.kt / CremaEditorHighlighterProvider.kt
  CremaTypedHandler.kt / CremaBraceMatcher.kt
  CremaCompletionContributor.kt / CremaAnnotator.kt
  GrindConfigurationType.kt
  ShowLspHelpAction.kt
src/main/resources/META-INF/plugin.xml
src/test/kotlin/dev/kona/idea/
  CremaLexerTest.kt
  CremaDirectivesTest.kt
```

## How to resume

```text
Clone/pull konaframework/kona-idea.
Read HANDOFF.md + README.md.
Develop on a feature branch; PR → green CI → squash merge (or tag release).
When kona adds Crema directives: update CremaDirectives.kt, tests, CHANGELOG, bump pluginVersion, tag vX.Y.Z.
Never edit GitHub rulesets. Use Auto models unless human overrides.
```

## Local verify

```bash
# JDK 17+ (toolchain 17; platform may warn preferring 21)
./gradlew test koverVerify
./gradlew buildPlugin   # → build/distributions/kona-idea-*.zip
./gradlew runIde        # sandboxed IntelliJ
```

## Likely next work (not started)

Only if a human asks:

1. Sync any new Crema directives from kona-lsp after framework changes.
2. JetBrains Marketplace publish (`JETBRAINS_PUBLISH_TOKEN` + `publishPlugin`).
3. Deeper intelligence (optional `grind`/index dump like Almasix `smith ide:index`) — large; not required for 0.1.x.
4. Bump `platformVersion` / `sinceBuild` as JetBrains ships new baselines.
5. File templates / New Crema view action.

## Sister links

| | |
|--|--|
| Framework | https://github.com/konaframework/kona |
| Framework handoff | https://github.com/konaframework/kona/blob/main/docs/governance/HANDOFF.md |
| VS Code extension (in monorepo) | `ide-support/vscode-crema/` |
| Public docs | https://github.com/konaframework/kona/blob/main/apps/docs/src/content/docs/docs/language/intellij.mdx |

## Refuse

Do not lower the Kover gate below 98% on verified classes. Do not reintroduce TextMate as the primary IntelliJ path. Do not start kona-lsp from the plugin by default (native-first, like Almasix Idea).
