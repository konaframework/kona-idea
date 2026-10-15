# Agent handoff — Kona Idea

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

**Framework monorepo:** [`konaframework/kona`](https://github.com/konaframework/kona) — see [`docs/governance/HANDOFF.md`](https://github.com/konaframework/kona/blob/main/docs/governance/HANDOFF.md).

## Read first

1. [README.md](README.md) — install, features, develop commands.
2. [CHANGELOG.md](CHANGELOG.md) — release notes.
3. Keep directive names in sync with kona `modules/lsp` → `dev.kona.lsp.CremaDirectives`.
4. Keep grind command list / layout markers aligned with kona scaffolds + `ListCommand.BUILTIN`.
5. HTTP: `KonaHttpServer` is **Netty + virtual threads**; default serve port **4000**.
6. Routing templates: **typed instance/lambda only** — do not reintroduce `Class@method` UX.

## Where we are

| Item | Value |
|------|--------|
| Repo | https://github.com/konaframework/kona-idea |
| Plugin id | `dev.kona.idea` |
| Version | **0.2.0** |
| Platform | IntelliJ IDEA Community/Ultimate — `platformType=IC`, `platformVersion=2024.2.5`, `sinceBuild=242` |
| Package | `dev.kona.idea` |
| Coverage | Kover ≥ **98%** line on verified set (Platform wiring excluded in `build.gradle.kts`) |
| Models | **Auto** unless the human names a model |
| CI | `.github/workflows/ci.yml` — `test` + `koverVerify` + `buildPlugin` |
| Release | Tag `v*` → `.github/workflows/publish.yml` attaches zip to GitHub Release |

## What 0.2.0 ships

- Crema (unchanged core): file type, lexer, highlighter, completion, annotator, `{{ }}`
- `KonaLayout` detection + folder icons for scaffold dirs
- Grind configs with host/port + Netty serve semantics; auto **Grind Serve**
- File + live templates for modern APIs (typed routes, Extract, Auth, disk, Bus, Schedule, Crema)
- Menu help: layout / routing / Maven modules / LSP
- New Crema View action

## Layout

```text
src/main/kotlin/dev/kona/idea/
  Crema*.kt                 # language support
  Grind*.kt / GrindArgs.kt / GrindCommands.kt
  KonaLayout.kt / KonaArtifacts.kt
  KonaProjectOpenActivity.kt / KonaFolderIconProvider.kt
  KonaFileTemplates.kt / KonaLiveTemplates.kt / KonaRoutingGuidance.kt
  NewCremaViewAction.kt / ShowKonaHelpActions.kt
src/main/resources/
  META-INF/plugin.xml
  fileTemplates/            # Controller, WebRoutes, Crema view
  liveTemplates/Kona.xml
src/test/kotlin/dev/kona/idea/
  CremaLexerTest.kt / CremaDirectivesTest.kt / KonaModernAlignmentTest.kt
```

## How to resume

```text
Clone/pull konaframework/kona-idea.
Read HANDOFF.md + README.md.
Sync CremaDirectives / GrindCommands / KonaLayout when kona scaffolds or serve change.
Develop on a feature branch; PR → green CI → squash merge (or tag release).
Never edit GitHub rulesets. Use Auto models unless human overrides.
```

## Local verify

```bash
# JDK 21+ (toolchain 21)
./gradlew test koverVerify
./gradlew buildPlugin   # → build/distributions/kona-idea-*.zip
./gradlew runIde        # sandboxed IntelliJ
```

## Likely next work

1. JetBrains Marketplace publish (`JETBRAINS_PUBLISH_TOKEN` + `publishPlugin`).
2. Optional Kotlin/Java inspections once `org.jetbrains.kotlin` optional dependency is worth it.
3. Bump `platformVersion` / `sinceBuild` as JetBrains ships new baselines.
4. Deeper index (`grind ide:…`) if the framework adds an IDE dump command.

## Sister links

| | |
|--|--|
| Framework | https://github.com/konaframework/kona |
| Framework handoff | https://github.com/konaframework/kona/blob/main/docs/governance/HANDOFF.md |
| VS Code extension (in monorepo) | `ide-support/vscode-crema/` |
| Public docs | https://github.com/konaframework/kona/blob/main/apps/docs/src/content/docs/docs/language/intellij.mdx |

## Refuse

Do not lower the Kover gate below 98% on verified classes. Do not reintroduce TextMate as the primary IntelliJ path. Do not start kona-lsp from the plugin by default. Do not teach `Controller@method` string routing.
