# Kona Idea (IntelliJ IDEA)

Native JetBrains plugin for [Kona](https://github.com/konaframework/kona) —
Crema language support, view/DB completion, scaffold markers, Grind run
configurations (including Netty `serve`), a right-side tool window, and
live/file templates. **No LSP** — IntelliJ platform APIs only.

| | |
| --- | --- |
| **Releases** | [GitHub Releases](https://github.com/konaframework/kona-idea/releases) (`.zip`) |
| **Framework** | [konaframework/kona](https://github.com/konaframework/kona) |
| **Sister extension** | [ide-support/vscode-crema](https://github.com/konaframework/kona/tree/main/ide-support/vscode-crema) in the Kona monorepo |
| **Docs** | [IntelliJ](https://docs.konaframework.dev/docs/language/intellij) (when published) |

**Supported products:** IntelliJ IDEA Community and Ultimate only (2024.2+).
The plugin descriptor depends on `com.intellij.modules.idea`, so other JetBrains IDEs are not compatible.
Installing, updating, or removing the plugin requires an IDE restart.

**Plugin id:** `org.konaframework.idea` (Maven group `org.konaframework`).
Changing the Marketplace plugin id from a prior `dev.kona.idea` listing may require a **new Marketplace listing**.

---

## How it works

```text
Kona app (kona new / grind scaffold)
  src/main/{kotlin|java}/bootstrap|app|routes|config|database
  src/main/resources/views/*.crema.html
  ./grind  +  storage/  +  .env (DB_*)
        │
        ├── Folder icons (official Kona mark)
        ├── Grind Serve run config (Netty, :4000, virtual threads)
        ├── Right “Kona” tool window (serve, migrate, docs, …)
        └── Crema + view/DB completion + live/file templates

*.crema.html / *.crema.kt
  @CremaTemplate / template() / // language=Crema strings
        │
        ▼
  Crema file type (overrider + detector beat HTML)
  LanguageInjector (Java/Kotlin hosts → Crema fragments)
        ├── Native CremaLexer + layered HTML highlighter
        ├── @directive autopopup + <x-*> / <wire:*> completion
        ├── View name refs → Ctrl+B / Ctrl+Click
        └── {{ }} typed handler

Cadence Kotlin/Java
        │
        ▼
  Model / DB column completion (scoped to model table)
  Settings → Tools → Kona (Connection / Migrations / Both)
```

---

## Install (local / from zip)

1. Build or download a zip:
   - Release: [GitHub Releases](https://github.com/konaframework/kona-idea/releases)
   - Local: `./gradlew buildPlugin` → `build/distributions/kona-idea-*.zip`
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…** and choose the zip.
3. Restart when prompted.
4. Open a Kona app (or create one with `kona new demo`). Confirm:
   - Crema views open as language **Crema**
   - A **Grind Serve** run configuration appears (or add **Run → Edit Configurations → Grind**)
   - The **Kona** tool window is available on the right
5. Run **Grind Serve** → browse `http://127.0.0.1:4000`

### Sandbox IDE (developers)

```bash
./gradlew runIde        # IntelliJ with this plugin loaded
```

---

## Features (0.5.24)

### Crema (native)
- File type for `*.crema.html` and `*.crema.kt`
- Native highlighter: HTML + `@directives`, `{{ }}`, `{!! !!}`, `{{-- --}}`
- **`@` directive autopopup** — type `@` to see all directives; type `@com` to
  filter without Ctrl+Space (HTML template layer supported)
- **Echo helpers** — inside `{{ }}` / `{!! !!}`, type `url` (or `route`,
  `asset`, `csrf_token`, `old`, `env`, `config`, `trans`, `lang`, `isset`,
  `empty`, `errors`, `loop.index`, …) to complete every Kona helper and
  conventional view variable; selecting a helper inserts `url('')` with the
  caret between the quotes
- **`<x-*>` components** — complete tags from `views/components/**`; `<x-slot>` /
  `<x-slot:name>` hints for named slots
- **`<wire:*>` components** — complete tags from `app.wire` (`Counter` →
  `<wire:counter />`); `<brew:*>` / `app.brew` remain aliases; HTML unknown-tag warnings suppressed
- Unknown-directive warnings (emails like `hi@example.com` ignored)
- **Reformat Code** — Ctrl+Alt+L (macOS: ⌥⌘L) formats tags with HTML code-style
  rules; indent size matches **Settings → Editor → Code Style → HTML**. Crema
  `@directives` and `{{ }}` are left intact
- `{{` auto-closes as `{{  }}`
- **Inline injection** — Java/Kotlin strings marked `@CremaTemplate`,
  `Component.template()` overrides, `inline("…")`, or `// language=Crema`
  highlight and complete as Crema (text blocks + `trimIndent()` / `trimMargin()`)
- **New → Crema View**

### View names
- Autocomplete dotted names (`home`, `layouts.app`) in `Crema.view` / `Wire.view` /
  `engine.make` / `engine.render` / `@extends` / `@include*` / `@component`
- Inertia page names (`Pages/Welcome`) in `Inertia.render` / `manager.render` /
  `Inertia.manager().render`
- **Go to Declaration:** **Ctrl+B** or **Ctrl+Click** (macOS: **⌘B** / **⌘Click**)
  opens the `.crema.html` / `.crema.kt` file
- Help: **Kona → Show View Navigation Help**

### Wire (`wire:*`)
- Completes known Wire attributes in Crema HTML (`wire:model`, `wire:click`,
  `wire:submit`, `wire:loading`, … + common modifiers)
- Completes **actions** (`wire:submit="add"`) and **properties** /
  `@Computed` (`wire:model="draft"`, `{{ remaining }}`) from the owning
  `app.wire` component class paired with the view
  (`views/wire/todos.crema.html` ↔ `Todos`), or from the **host class** when
  the markup lives in an injected `template()` string
- Known `wire:*` names are registered as HTML attributes, so IDEA does not
  report them as “attribute is not allowed here”
- Weak warnings for unknown `wire:*` names
- Maven: `org.konaframework:kona-wire` (`kona-brew` is a deprecated alias)

### Env keys
- In config (and any `Env.get` / `Env.getOrDefault` / `Env.require` string),
  **Ctrl+Space** completes keys from `.env`, `.env.*` templates, and keys
  already read in `src/main/{kotlin,java}`
- **Ctrl+B** or **Ctrl+Click** on that key opens the assignment in the project `.env`
- In `.env`, **Ctrl+Space** on a key completes names referenced from source

### Inertia
- Live templates: `kinertia`, `kinertashare` (Kotlin), `jinertia` (Java)
- File templates: **Kona Inertia Page.vue**, **Kona Inertia Root.crema.html**
- Help: **Kona → Show Wire + Inertia Help**
- Maven: `org.konaframework:kona-inertia`

### Cadence models + DB columns
- Completes column names in Cadence / QueryBuilder call sites (`where`, `select`,
  `orderBy`, `create(mapOf(…))`, …), **scoped to the receiver model’s table**
  (`User.where("` / Java `User.QUERY.where("` → `users` columns only)
- Completes **model attributes** on instances (`val user = User.query().first()` /
  `Auth.user() as? User` / Java `User user = User.QUERY.find(1)` →
  `user.email` / `user.name`), including fillable/casts, accessors, and relations
- Completes the same keys in `user.getAttribute("…")`, `user?.getAttribute("…")`,
  `user.get("…")`, `user["…"]`, `this.` / bare `getAttribute("` **inside the
  model class**, and when the caret is still inside `getAttribute()`. Works in
  **any** Kotlin/Java file (services, seeders, jobs, commands — not only
  controllers), including `fun greet(user: Authenticatable)` via `user` →
  `User`. Kona items are always at the **top** of the lookup list.
- Column sources (configurable):
  1. **Migrations** under `src/main/{kotlin|java}/database/migrations`
     (`app/models` from `kona new`, including `json` / `longText`)
  2. **JDBC** — `.env` `DB_URL` or `DB_CONNECTION=sqlite` / `h2` (relative file
     URLs resolved against the app root). Drivers load from
     `build/kona-runtime.classpath` when they are not on the plugin classpath.
- **Settings → Tools → Kona** — Column source: **Both** (default) |
  **Migrations only** | **Connection only**

### Tool window (RIGHT)
- **View → Tool Windows → Kona** (or the Kona icon on the right strip)
- Quick actions: Serve, List, Migrate, Queue work, Schedule work, New Crema View,
  layout/routing/Maven/Brew+Inertia help, docs links (incl. Brew / Inertia)

### Project structure
Recognizes the scaffold tree from `kona new`:
`bootstrap/`, `app/`, `routes/`, `config/`, `database/`, `resources/` (views,
lang, css, js), top-level `public/` (HTTP document root; Vite writes
`public/build`), `storage/`, app-local `./grind`, and `org.konaframework:*`
(or legacy `dev.kona:*`) Gradle dependencies. The Kona Project View lists that
root `public/` beside `resources`. A `public` folder nested under `resources`
is not the document root. Root files that exist are listed after the folders:
`./grind`, `.env`, `README.md`, `package.json`, lockfiles, `tsconfig.json`,
`vite.config.js`, and related Vite / Svelte config. Canonical folders use the
official Kona brand mark. For Kona apps, the Project tool window defaults to
the **Kona** pane (scaffold folders with short names); Project / Packages
remain available.

### Grind + Netty HTTP
- **Grind** run configuration type with persisted arguments
- Host / port fields for `serve` (defaults `127.0.0.1` / `4000`)
- Auto-creates **Grind Serve** when opening a detected Kona app
- Serve uses the framework’s **Netty** `KonaHttpServer` + **virtual threads**
- **`Grind.call("…")` / `callSilently`** complete built-in commands (`migrate`,
  `migrate:fresh`, `db:wipe`, `db:show`, `schema:dump`, `make:model`,
  `db:seed`, …) and custom `ConsoleCommand` names from
  `META-INF/kona/commands` and `app/console`

### Tooling / APIs
- Live templates: `kroute`, `jroute`, `kcadence`, `kauth`, `kdisk`, `kqueue`,
  `kschedule`, `kcrema`, `kinertia`, `kinertashare`, `jinertia`, `kserve`
- File templates: Controller (Kotlin/Java), Crema view, Inertia page/root,
  WebRoutes (typed handlers)
- **Kona** menu: layout, routing, Maven modules, view navigation, Brew + Inertia

---

## Verify in the IDE (`./gradlew runIde`)

1. **No LSP** — there is no kona-lsp menu item; completion works offline.
2. **Crema `@` autopopup** — open `*.crema.html`, type `@` (no Ctrl+Space) → full
   directive list; continue with `com` → filtered `@component` / … stays open.
3. **`<x-*>`** — add `views/components/alert.crema.html`, type `<x-` → `alert`;
   inside `<x-alert>`, type `<` → `x-slot` hints.
4. **Model attributes** — in a `kona new` app, `val user = Auth.user() as? User`
   then `user.` → `email` / `name` / `password` from the users migration. Inside
   `User.kt`, `this.` and bare `getAttribute("` complete the same columns. Java:
   `User user = User.QUERY.find(1);` then `user.get("` / `User.QUERY.where("`.
5. **Scoped query columns** — `User.where("` (and `User.QUERY.where("`) suggests
   only `users` columns (not `projects` / `todos`).
6. **Settings** — **Settings → Tools → Kona** → change Column source and re-try
   completion (Connection only / Migrations only / Both). Connection uses the
   relative SQLite/H2 URL in `.env` against the app root; run `./grind` once so
   `build/kona-runtime.classpath` exists when the driver is not bundled.
7. **Tool window** — open **Kona** on the right → **Serve (Netty :4000)**.
8. **Views** — in `Crema.view(engine, "|")` or `@extends('|')`, complete names; **Ctrl+B** opens the file.

---

## Develop

```bash
./gradlew test
./gradlew buildPlugin   # → build/distributions/*.zip
./gradlew runIde        # sandboxed IntelliJ with the plugin
```

JDK 21+. Coverage gate: ≥ 98% line on verified product logic (thin Platform
wiring excluded — see `build.gradle.kts`).

---

## Releasing

Versions live in `gradle.properties` (`pluginVersion`) and must match the Git tag
(without the leading `v`). Plugin metadata / change notes are set in
`build.gradle.kts` (`intellijPlatform.pluginConfiguration`).

### Cut a release (tag → GitHub → Marketplace)

1. Bump `pluginVersion` (e.g. `0.3.0`), update `CHANGELOG.md` and change notes
   in `build.gradle.kts`, merge to `main`.
2. Tag and push:
   ```bash
   git tag v0.3.0
   git push origin v0.3.0
   ```
3. The **Publish release** workflow (`.github/workflows/publish.yml`) will:
   - run tests + `buildPlugin`
   - create/update a [GitHub Release](https://github.com/konaframework/kona-idea/releases)
     with `build/distributions/kona-idea-*.zip` and `CHANGELOG.md`
   - if `PUBLISH_TOKEN` is configured, run `publishPlugin` (unsigned unless signing secrets are set)

Optional: Actions → **Publish release** → Run workflow
(`skip_marketplace` to publish GitHub-only). Dispatch from `main` uploads the
current `pluginVersion` and does not create another GitHub Release. A re-run of
an old tag job still uses the workflow file stored on that tag.

### GitHub Actions secrets

Configure under **Settings → Secrets and variables → Actions**:

| Secret | Purpose |
| --- | --- |
| `PUBLISH_TOKEN` | JetBrains Marketplace permanent token ([My Tokens](https://plugins.jetbrains.com/author/me/tokens)). Alias: `JETBRAINS_PUBLISH_TOKEN`. This is the only secret Marketplace upload needs. |
| `GITHUB_TOKEN` | Automatic; workflow only needs `contents: write` for Releases. |

Author signing secrets are optional. Marketplace signs the zip after upload, and other plugins publish with the token alone. Set all three only if you want `signPlugin` to run first: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`. See [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html). Never commit tokens or PEMs.

Optional env (workflow / local): `PUBLISH_CHANNEL` — non-default channel
(`beta`, `alpha`, `eap`, …). Unset = Stable (`default`).

### Marketplace listing text and slides

The IDE and Marketplace overview use the HTML in `src/main/resources/META-INF/plugin.xml`
(`<description>`). Gradle must not replace that block.

Slides for the Marketplace **Media** section (~1920×1080 PNGs) live in
[`marketplace/media/`](marketplace/media/). These are **real IntelliJ IDEA
screenshots** (Crema editor, Grind Serve run config, Kona project tree) — not
HTML mocks. Prefer a clear light theme when re-capturing; the checked-in set
may use the Islands Dark UI if that is what the capture machine had. Optional
mock HTML under `marketplace/studio/` remains only as a fallback generator.
The same install steps, for the separate **Getting Started** field on the
plugin admin page, are in
[`marketplace/getting-started.html`](marketplace/getting-started.html).
Marketplace has no upload API for those two fields: open the plugin page →
admin → Media, and paste Getting Started there.

### JetBrains Marketplace — first publish & org verification

JetBrains requires the **first** Marketplace listing to be uploaded **manually**:

1. `./gradlew buildPlugin` (or download the GitHub Release zip).
2. Sign in at [JetBrains Marketplace](https://plugins.jetbrains.com/) →
   **Add new plugin** → upload the zip → fill vendor / description.
3. After the plugin exists, later versions can use `./gradlew publishPlugin`
   (what the workflow runs).

If publishing under an **organization** (recommended for `Kona Framework`):

1. Create/verify a Marketplace organization and link your JetBrains Account.
2. Complete any required org verification / agreement steps JetBrains shows
   on the vendor profile (tax / banking / contact as applicable).
3. Ensure the permanent token belongs to an account that can publish that
   organization's plugins.
4. Align `<vendor>` in `plugin.xml` / Marketplace listing with the verified org.

Until Marketplace approval finishes, install from GitHub Release zips
(**Settings → Plugins → ⚙ → Install Plugin from Disk…**).

Local dry-run (no upload):

```bash
./gradlew test koverVerify buildPlugin
# with the token in the environment only:
#   PUBLISH_TOKEN=… ./gradlew publishPlugin
```

---

## License

Apache-2.0 (same as Kona).
