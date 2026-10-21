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
8. ORM package is **Cadence** (`org.konaframework:kona-cadence`), not Extract.
9. Brew / Inertia: keep `wire:*` lists aligned with `js/brew`; page-name patterns with `kona-inertia` samples.

## Where we are

| Item | Value |
|------|--------|
| Repo | https://github.com/konaframework/kona-idea |
| Plugin id | `org.konaframework.idea` |
| Version | **0.5.22** |
| Platform | IntelliJ IDEA Community/Ultimate — `platformType=IC`, `platformVersion=2024.2.5`, `sinceBuild=242` |
| Package | `org.konaframework.idea` |
| Coverage | Kover ≥ **98%** line on verified set (Platform wiring excluded in `build.gradle.kts`) |
| Models | **Auto** unless the human names a model |
| CI | `.github/workflows/ci.yml` — `test` + `koverVerify` + `buildPlugin` |
| Release | Tag `v*` → `.github/workflows/publish.yml` attaches zip to GitHub Release + Marketplace |

**Marketplace id:** changing the plugin id from `dev.kona.idea` to `org.konaframework.idea` may require a **new Marketplace listing** (ids are immutable once published). Sideload/GitHub Release zips are unaffected.

## What 0.5.22 ships

- DashboardController: a following indented `val session = … as Session` no
  longer overrides `val user = Auth.user() as? User`. `user?.getAttribute("…")`
  resolves the `user` receiver.

## What 0.5.21 ships

- Cadence column completion uses the real `kona new` tree (`app/models`,
  users migration). `this.` / bare `getAttribute("` inside a model, Java
  `User.QUERY.find` / `where`, relative JDBC URLs, and drivers from
  `build/kona-runtime.classpath` all work. `KonaLayout.completionRootNear`
  finds the app from a nested file even without `./grind`.

## What 0.5.20 ships

- Kona Project View lists frontend / docs files at the project root when they
  exist: `README.md`, `package.json`, npm/pnpm/yarn/bun lockfiles,
  `tsconfig.json` / `jsconfig.json`, `vite.config.js` / `.ts` / `.mjs`,
  `svelte.config.js`, `env.d.ts` / `vite-env.d.ts`. Keep the list in
  `KonaProjectStructure` in sync with `kona new` scaffolds.

## What 0.5.19 ships

- Cadence column completion is restored: `CadenceCompletion` is the testable
  pipeline for `user.` / `getAttribute` / `get(` / `user["` / `User.where("`.
  Dummy rewind looks inside the dummy; dummy prefix-matcher residue is empty;
  `DbColumnCompletionContributor` uses `appRootNear`; `KonaLookup.PRIORITY` is
  `1000` (weigher + marker rank items first — `1e6` hid the list).

## What 0.5.18 ships

- Crema **Reformat Code** uses the HTML formatter
  (`CremaFormattingModelBuilder` → `SimpleTemplateLanguageFormattingModelBuilder`)
  and HTML indent options (`CremaFileIndentOptionsProvider`). Keep
  `CremaFormatting.frozenRanges` in sync with the lexer so echoes/directives
  stay outer-language tokens.

## What 0.5.17 ships

- Crema echo completion: `CremaEchoCatalog` + `CremaEchoCompletionContributor`
  offer every Kona helper and conventional variable inside `{{ }}` /
  `{!! !!}` (welcome views, login, dashboard — not only Brew components).
  Keep the catalog in sync with kona `CremaHelpers` and kona-lsp
  `CremaEchoCatalog`.

## What 0.5.16 ships

- Kona Project View: project-root `public/` is the HTTP document directory and
  a top-level scaffold node (`app`, `routes`, `resources`, `public`, …).
  `KonaProjectStructure` no longer re-parents `src/main/resources/public`.
  `KonaResourcesDirectoryNode` hides a nested `public` only when root `public/`
  exists. Layout help and folder markers use root `public/`, not
  `resources/public`.

## What 0.5.15 ships

- Grind built-in command list includes `migrate:fresh`, `migrate:refresh`,
  `migrate:install`, `db:wipe`, `db:show`, `db:table`, and `schema:dump`
  (`GrindCommands.BUILTIN`, same names as kona `ListCommand.BUILTIN`).

## What 0.5.14 ships

- **Crema language injection** into Java/Kotlin strings: `@CremaTemplate`
  methods / `template()` overrides (`Component.template()`), `inline(source)`
  / `@CremaTemplate` parameters, and `// language=Crema`. Java text blocks and
  Kotlin `"""` + `trimIndent()` / `trimMargin()` use ElementManipulators for
  indent. Community Edition `LanguageInjector` (no IntelliLang XML).
- Completion / navigation / annotators treat injected fragments as Crema
  (`CremaContexts` + `InjectedLanguageManager`), not only `originalFile` name.
- Brew members inside an injected `template()` resolve the **host Component
  class** (`BrewInjectionOwners`).

## What 0.5.13 ships

- Cadence **type-based** instance completion: `Auth.user() as? User` /
  `as User` / `val user: User?` / Java `(User) Auth.user()` resolve the
  variable or cast type (Cadence `Model` subclass) and offer columns +
  fillable/casts/accessors/relations. Query assignment still works.
- **Lookup ranking:** `KonaLookup` wraps every Kona `LookupElement` with
  `PrioritizedLookupElement` (1e6) + userData marker; `KonaCompletionWeigher`
  (`order="first"`) boosts those items above Java/Kotlin members.

## What 0.5.12 ships

- Cadence **instance** completion actually fires for queried locals (`val user =
  User.query().first()` → `user.` / `user.getAttribute(` / `user.get("` /
  `user["`). Columns come from migrations/JDBC **or** fillable/casts even if
  `ModelIndex.find` misses the source file. `fillCompletionVariants` + dummy
  rewind (including trailing space / partial dummy).
- `Grind.call` / `callSilently` command-name completion (built-ins +
  `META-INF/kona/commands` + `app/console` `ConsoleCommand` classes).

## What 0.5.11 ships

- Cadence **model attribute** completion: columns + accessors + relations on
  `user.` / `user.em`, and string keys in `getAttribute` / `setAttribute` /
  `attribute` / `hasAttribute` / `getRawAttribute` / `setRawAttribute`
  (`ModelAttributeIndex`, `AttributeKeyPatterns`, `CompletionText` dummy fix).

## What 0.5.10 ships

- Env **value** completion (`EnvValueOptions` + `EnvValueCompletionContributor`) for
  known option-like keys in `.env` / `.env.*` and `Env.getOrDefault` defaults;
  merges project DB connection / filesystem disk / log channel names from config.

## What 0.5.9 ships

- Kona Project View: folders first (including `resources` as `PsiDirectoryNode`),
  then root files (`.env`, `grind`, …).

## What 0.5.8 ships

- Brew tag Ctrl+B → component class; attribute Ctrl+B → property / mount param.
- Brew tag attribute completion from component properties + `mount(...)`.
- `{{ }}` / wire values: Brew member or controller `mapOf` key (`ViewDataIndex`).

## What 0.5.7 ships

- Kona Project View: `public` is top-level again; `resources` hides nested
  `public` (`KonaResourcesDirectoryNode`). 0.5.16 corrects the path: the node
  is `<project>/public`, not `src/main/resources/public`.

## What 0.5.6 ships

- `<brew:*>` tag completion / HTML recognition (`BrewTagPatterns`,
  `BrewTagCompletionContributor`, `BrewTagDescriptorsProvider`) from
  `BrewComponentIndex` (`app.brew` / `@BrewComponent` → kebab tag names).
- Kona Project View is the **default** Project tool window pane for Kona apps
  (`KonaProjectViewPane.isDefaultPane` + `ensureKonaProjectView` on open).
- Scaffold tree groups under one `resources` node (`KonaProjectStructure`), with
  `public` as a sibling top-level entry.

## What 0.5.5 ships

- Brew action / property / `@Computed` completion from the owning component class
  (`BrewComponentIndex`, `BrewMemberCompletionContributor`, value/echo patterns on
  `BrewWirePatterns`). Pairing: `views/brew/<name>` ↔ `app.brew` kebab name.
- Kona Project View pane (`KonaProjectViewPane` / `KonaProjectStructure`) — scaffold
  tree beside Project / Packages.

## What 0.5.4 ships

- Known Brew `wire:*` attributes are HTML attribute descriptors in `*.crema.html` (`BrewWireAttributeDescriptorsProvider`). Unknown names still get the weak warning.
- Env keys: `EnvKeys` + completion / PsiReference / Go to Declaration. Config `Env.get` / `getOrDefault` / `require` completes keys from `.env` and source; Ctrl+B opens the key in `.env`. `.env` completes keys referenced from `src/main`.

## What 0.5.3 ships

- `plugin.xml` description is the Marketplace/IDE text (do not override it from Gradle)
- Getting Started steps live in that description and in `marketplace/getting-started.html`
- Listing slides: `marketplace/media/*.png` (upload in the Marketplace Media admin)

## What 0.5.2 ships

- Supported products are IntelliJ IDEA only (`com.intellij.modules.idea`, not `com.intellij.modules.platform`)

## What 0.5.1 ships

- Marketplace `pluginIcon.svg` / `pluginIcon_dark.svg` — caramel mug mark (`kona-logo.svg`)

## What 0.5.0 ships

- Package / plugin id `org.konaframework.idea`; Maven group `org.konaframework`
- Brew `wire:*` completion + annotator (`BrewWireAttributes`, `BrewWirePatterns`, …)
- Inertia page-name patterns on `ViewNamePatterns` (`Inertia.render`, manager)
- Inertia live templates (`kinertia`, `kinertashare`, `jinertia`) + file templates
- Brew + Inertia help action / tool window mentions `kona-brew` / `kona-inertia`

## What 0.4.0 shipped

- Crema `@` autopopup + filter-as-you-type (`CremaTypedHandler`, `CremaCharFilter`)
- `<x-*>` / `<x-slot>` completion (`XComponentIndex`, `XComponentCompletionContributor`)
- Cadence model attribute completion (`ModelPropertyCompletionContributor`)
- DB column completion scoped to model table (`DbColumnPatterns.receiver`, `ModelIndex`)
- **Settings → Tools → Kona** column source mode (`KonaIdeaSettings`, `KonaConfigurable`)

## Layout

```text
src/main/kotlin/org/konaframework/idea/
  Crema*.kt                 # language support + @ completion + CharFilter
  CremaInjectionSites.kt    # @CremaTemplate / language=Crema / template() sites
  CremaLanguageInjector.kt  # LanguageInjector (Java/Kotlin strings → Crema)
  XComponent*.kt            # <x-*> index + patterns + contributor
  BrewTag*.kt               # <brew:*> patterns + contributor + HTML descriptors
  BrewWire*.kt              # wire:* attributes
  BrewComponentIndex.kt / BrewMemberCompletionContributor.kt
  KonaProjectViewPane.kt / KonaProjectRootNode.kt / KonaProjectStructure.kt
  View*.kt                  # view index, patterns, refs, goto (+ Inertia)
  Db*.kt / Migration*.kt / Model*.kt / ModelAttributeIndex.kt / CompletionText.kt
  AttributeKeyPatterns.kt / CadenceTableNames.kt / ModelCompletion.kt
  KonaLookup.kt / KonaCompletionWeigher.kt
  GrindCall*.kt / GrindCommandIndex.kt
  KonaIdeaSettings.kt / KonaConfigurable.kt
  KonaToolWindowFactory.kt / GrindQuickActions.kt
  Grind*.kt / KonaLayout.kt / KonaIcons.kt
  …
src/main/resources/
  META-INF/plugin.xml
  icons/ … fileTemplates/ liveTemplates/
src/test/kotlin/org/konaframework/idea/
  CremaLexerTest.kt / CremaDirectivesTest.kt / CremaInjectionTest.kt
  KonaModernAlignmentTest.kt / NativeIdeFeaturesTest.kt
  CompletionUxTest.kt / BrewInertiaSupportTest.kt
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
