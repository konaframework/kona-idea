# Agent handoff — Kona Idea

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

**Framework monorepo:** [`konaframework/kona`](https://github.com/konaframework/kona) — see [`docs/governance/HANDOFF.md`](https://github.com/konaframework/kona/blob/main/docs/governance/HANDOFF.md).

## Read first

1. [README.md](README.md) — install, features, develop commands.
2. [CHANGELOG.md](CHANGELOG.md) — release notes.
3. Keep directive names in sync with Crema in kona (`modules/crema` / editor lists).
4. Keep grind command list / layout markers aligned with kona scaffolds + `ListCommand.BUILTIN`.
5. HTTP: `KonaHttpServer` is **Netty + virtual threads**; default serve port **4000**.
6. Routing templates: **typed instance/lambda only** — do not reintroduce `Class@method` UX.
7. **No LSP in this plugin** — native CompletionContributor / PsiReference / tool window only.
8. ORM package is **Cadence** (`dev.kona:kona-cadence`), not Extract.

## Where we are

| Item | Value |
|------|--------|
| Repo | https://github.com/konaframework/kona-idea |
| Plugin id | `dev.kona.idea` |
| Version | **0.4.0** |
| Platform | IntelliJ IDEA Community/Ultimate — `platformType=IC`, `platformVersion=2024.2.5`, `sinceBuild=242` |
| Package | `dev.kona.idea` |
| Coverage | Kover ≥ **98%** line on verified set (Platform wiring excluded in `build.gradle.kts`) |
| Models | **Auto** unless the human names a model |
| CI | `.github/workflows/ci.yml` — `test` + `koverVerify` + `buildPlugin` |
| Release | Tag `v*` → `.github/workflows/publish.yml` attaches zip to GitHub Release + Marketplace |

## What 0.4.0 ships

- Crema `@` autopopup + filter-as-you-type (`CremaTypedHandler`, `CremaCharFilter`)
- `<x-*>` / `<x-slot>` completion (`XComponentIndex`, `XComponentCompletionContributor`)
- Cadence model attribute completion (`ModelPropertyCompletionContributor`)
- DB column completion scoped to model table (`DbColumnPatterns.receiver`, `ModelIndex`)
- **Settings → Tools → Kona** column source mode (`KonaIdeaSettings`, `KonaConfigurable`)

## Layout

```text
src/main/kotlin/dev/kona/idea/
  Crema*.kt                 # language support + @ completion + CharFilter
  XComponent*.kt            # <x-*> index + patterns + contributor
  View*.kt                  # view index, patterns, refs, goto
  Db*.kt / Migration*.kt / Model*.kt / CadenceTableNames.kt
  KonaIdeaSettings.kt / KonaConfigurable.kt
  KonaToolWindowFactory.kt / GrindQuickActions.kt
  Grind*.kt / KonaLayout.kt / KonaIcons.kt
  …
src/main/resources/
  META-INF/plugin.xml
  icons/ … fileTemplates/ liveTemplates/
src/test/kotlin/dev/kona/idea/
  CremaLexerTest.kt / CremaDirectivesTest.kt
  KonaModernAlignmentTest.kt / NativeIdeFeaturesTest.kt
  CompletionUxTest.kt
```

## How to resume

```text
Clone/pull konaframework/kona-idea.
Read HANDOFF.md + README.md.
Develop on a feature branch; PR → green CI → merge (or tag release).
Never edit GitHub rulesets. Use Auto models unless human overrides.
```

## Local verify

```bash
# JDK 21+ (toolchain 21)
./gradlew test koverVerify
./gradlew buildPlugin   # → build/distributions/kona-idea-*.zip
./gradlew runIde        # sandboxed IntelliJ
```
