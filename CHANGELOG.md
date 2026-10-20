# Changelog

## 0.5.16

- **Kona Project View `public`** — the HTTP document root is the project-root
  `public/` folder (Vite writes `public/build`). It is a top-level scaffold
  node beside `app`, `routes`, and `resources`. A `public` directory under
  `resources` or `src/main/resources` is not that node; when root `public/`
  exists, the nested duplicate is hidden.

## 0.5.15

- **Grind database commands** — built-in completion / run-config list now
  includes `migrate:fresh`, `migrate:refresh`, `migrate:install`, `db:wipe`,
  `db:show`, `db:table`, and `schema:dump` (aligned with grind
  `ListCommand.BUILTIN`). Existing migrate/rollback/reset/status, `db:seed`,
  and `make:migration` / `make:seeder` / `make:factory` names are unchanged.

## 0.5.14

- **Crema language injection** — Java/Kotlin strings that are inline Brew/Crema
  markup get full Crema highlighting and completion:
  - Return literals of `@CremaTemplate` methods and of `template()` overrides
    (`Component.template()`, including Java text blocks and Kotlin `"""` +
    `trimIndent()` / `trimMargin()`)
  - First argument of `inline(` / `BrewView.inline(` and parameters annotated
    `@CremaTemplate`
  - Strings preceded by `// language=Crema` (or `/* language=Crema */`)
- **Injected completion** — `@` directives, `<brew:*>`, `wire:*`, and `{{ }}`
  members work inside injected fragments. Brew member completion resolves the
  **host Component class**, not a sibling `.crema.html`.

## 0.5.13

- **Cadence type-based instance completion** — `user.` / `getAttribute` / `get("` /
  `user["` resolve the Kotlin/Java **type** of the receiver, so
  `val user = Auth.user() as? User`, `as User`, `val user: User?`, and Java
  `(User) Auth.user()` complete Cadence columns, fillable, casts, accessors, and
  relations (same as `User.query().first()`).
- **Kona lookups first** — every Kona suggestion is wrapped with
  `PrioritizedLookupElement` (priority `1_000_000`) and a `CompletionWeigher`
  (`order="first"`) so columns, grind commands, env keys, views, brew tags/wire,
  and Crema directives appear at the top of the list instead of under Java/Kotlin
  members.

## 0.5.12

- **Cadence instance completion** — `user.` / `user?.` completes table columns
  even when `User.kt` is not in `app/models` (falls back to the `users` table
  from migrations, plus fillable / casts / `id` / timestamps). Completes inside
  `getAttribute()` with no quotes yet, Java `get("…")` / `set("…")`, and Kotlin
  `user["…"]`. Dummy identifiers with a trailing space or a partial
  `IntellijIdeaRulezzz` no longer wipe the prefix.
- **Grind.call** — `Grind.call("…")` / `callSilently` complete built-in grind
  commands and custom `ConsoleCommand` names from `META-INF/kona/commands` and
  `app/console`.

## 0.5.11

- **Cadence model attribute completion** — `user.` completes DB columns (migrations
  and/or JDBC), accessors (`getFullNameAttribute` → `full_name` / `fullName`),
  appends, and relations (`posts`, `profile`, …). Same keys inside
  `getAttribute` / `setAttribute` / `attribute` / `hasAttribute` /
  `getRawAttribute` / `setRawAttribute`. Only for discovered Cadence models.
- **Fix** — completion text/offset resolves from the position file (not
  `originalFile` + clamped offset), so Kotlin’s `IntellijIdeaRulezzz` dummy no
  longer blanked instance property suggestions.

## 0.5.10

- **Env value completion** — for known option-like keys (`DB_CONNECTION`,
  `LOG_CHANNEL` / `LOG_STACK` / `LOG_LEVEL`, `SESSION_DRIVER`, cache/queue/mail/hash,
  `FILESYSTEM_DISK`, `APP_ENV`, booleans, `SESSION_SAME_SITE`, …) in `.env` /
  `.env.*` and in `Env.getOrDefault("KEY", "…")` defaults. Project connection and
  disk names from `config/Database` and `config/Filesystems` are merged in.

## 0.5.9

- **Kona Project View order** — `resources` (and all scaffold folders) sort with
  directories again; root files (`.env`, `grind`, …) always come last. The
  `resources` node is a real `PsiDirectoryNode` so IDEA’s type-sort no longer
  parks it after scripts.

## 0.5.8

- **Brew tag navigation** — Ctrl+B / Ctrl+Click on `<brew:counter` opens the
  `Counter` class; on `:start` / property attributes opens the matching property
  or `mount(...)` parameter.
- **Brew tag attribute completion** — inside `<brew:… >`, completes attributes
  from the component’s public properties and `mount` parameters (plus `:bound`
  forms and `key` / `lazy` / `defer`).
- **View variable navigation** — Ctrl+B on `{{ name }}` / `wire:model` values
  jumps to the Brew member when the view is a component view, otherwise to the
  controller `mapOf("name" to …)` key that feeds the page.

## 0.5.7

- **Kona Project View `public`** — `public` is a top-level scaffold entry again;
  `resources` still groups `views` / `lang` / `css` / `js` and no longer nests
  `public` underneath.

## 0.5.6

- **`<brew:*>` tags** — complete and recognize Brew components from `app.brew`
  (`Counter` → `<brew:counter />`, `@BrewComponent`, nested packages). HTML
  unknown-tag / attribute-not-allowed inspections are suppressed for those tags.
- **Kona Project View default** — when a Kona app is opened, the Project tool
  window uses the **Kona** pane by default (`isDefaultPane` + switch on open).
- **Resources grouping** — Kona Project View shows one `resources` node
  (`src/main/resources`) for `views` / `lang` / `css` / `js` (not `public`).

## 0.5.5

- **Brew member completion** — inside `wire:submit` / `wire:click` / `wire:init` /
  `wire:target` values, completes **actions** from the owning `app.brew` component
  (`views/brew/todos.crema.html` ↔ `Todos`). `wire:model` / `wire:show` /
  `wire:text` and `{{ }}` / `{!! !!}` echoes complete **properties** and
  `@Computed` members.
- **Kona Project View** — Project tool window view dropdown includes **Kona**,
  showing scaffold folders (`app`, `routes`, `views`, `storage`, …) with short
  names instead of deep package paths.

## 0.5.4

- **Brew `wire:*` HTML descriptors** — known attributes are registered so IDEA
  does not flag them as “attribute is not allowed here” in `*.crema.html`.
- **Env key completion** — `Env.get` / `getOrDefault` / `require` completes keys
  from `.env` and source; `.env` completes keys referenced from `src/main`.
- **Env Go to Declaration** — Ctrl+B / Ctrl+Click on a config env key opens the
  assignment in the project `.env`.

## 0.5.3

- **Marketplace description** — opens with “Kona Framework IDE support in IDEA”
  and lists Crema, Brew, Inertia, Cadence, and Grind.
- **Getting started** — install, restart, open a Crema view, type `@` / `wire:`,
  run Serve from the Kona tool window, and jump to a view with Ctrl+B.
- **Listing art** — 1920×1080 slides in `marketplace/media/` for the Marketplace
  Media section (upload those in the plugin admin; they are not inside the zip).

## 0.5.2

- **Supported IDEs** — Marketplace compatibility is IntelliJ IDEA only
  (Community and Ultimate, 2024.2+). The plugin now depends on
  `com.intellij.modules.idea` instead of `com.intellij.modules.platform`.

## 0.5.1

- **Plugin icon** — Marketplace / Plugins list `pluginIcon.svg` (+ `_dark`) now uses the
  caramel mug mark from monorepo `kona-logo.svg` (replacing the old Ko lettermark).

## 0.5.0

- **Plugin id / package** — `org.konaframework.idea` (Maven group `org.konaframework`);
  in-IDE brand icons (tool window, file type, folders) use the monorepo mug mark.
- **Brew `wire:*`** — completion + weak-warning annotator for known attributes
  (`wire:model`, `wire:click`, `wire:submit`, `wire:loading`, `wire:target`,
  `wire:ignore`, `wire:key`, `wire:dirty`, `wire:offline`, `wire:confirm`,
  `wire:poll`, `wire:init`, `wire:show`, `wire:transition`, + common modifiers).
- **Inertia page names** — `Inertia.render` / `manager.render` /
  `Inertia.manager().render` participate in view-name completion and navigation.
- **Inertia templates** — live templates `kinertia`, `kinertashare`, `jinertia`;
  file templates for a minimal Vue page and Crema root shell.
- **Help / tool window** — Brew + Inertia help action; Maven coords
  `org.konaframework:kona-brew` / `kona-inertia`; docs links.

## 0.4.0

- **Crema `@` autopopup** — typing `@` in `*.crema.html` shows all directives
  immediately (no Ctrl+Space); typing a prefix (e.g. `@com`) filters and keeps
  the popup open (`CremaTypedHandler` + `CremaCharFilter`).
- **`<x-*>` components** — complete tags from `views/components/**`, plus
  `<x-slot>` / `<x-slot:name>` hints aligned with Crema named slots.
- **Model attributes** — complete Cadence columns on instance access
  (`project.description` after `Project.find(1)`), from migrations and/or JDBC.
- **Scoped query columns** — `User.where("` only suggests columns for that
  model’s table (override or conventional plural), not every table in the app.
- **Settings → Tools → Kona** — column source: Connection only | Migrations only |
  Both (default).

## 0.3.1

- Rename **Extract** → **Cadence** across templates, artifacts, docs, and DB column completion.

## 0.3.0

- **No LSP** — removed kona-lsp menu/help; all Crema/IDE features are native
  IntelliJ APIs (CompletionContributor, PsiReference, GotoDeclarationHandler).
- **Crema `@` completion fixed** — works with the HTML template data language;
  autopopup when typing `@`.
- **View names** — autocomplete + Go to Declaration (**Ctrl+B** / Ctrl+Click,
  ⌘B on macOS) for `Crema.view` / `Brew.view` / `engine.make` / `@extends` /
  `@include` / …
- **DB columns** — completion from Cadence migration parsing and optional live
  JDBC via project `.env` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`).
- **Kona tool window** (RIGHT) — grind serve, migrate, queue/schedule, New Crema
  View, layout/routing help, docs links.
- **Icons** — official Kona square brand mark for tool window, file type, folder
  markers, run configs, and actions.

## 0.2.0

- Align with current Kona framework (`kona new` scaffold tree, `dev.kona` Maven
  modules, Netty HTTP server).
- Detect Kona apps and mark canonical folders (`bootstrap`, `routes`, `app`,
  `config`, `database`, `views`, `storage`, …).
- Grind run configurations: persist args; host/port for `serve` (default
  `127.0.0.1:4000`); auto-add **Grind Serve**; document Netty + virtual threads.
- File templates (Controller, Crema view, WebRoutes) and live templates for
  typed Router handlers, Cadence, Auth, filesystem disks, Queue `Bus`, Schedule,
  Crema — no `Controller@method` teaching.
- Kona menu: layout, routing, Maven modules; **New → Crema View**.

## 0.1.0

- Initial release: native Crema file type, HTML-layered highlighter, directive
  completion, unknown-directive annotator, `{{ }}` typed handler, Grind run
  configuration.
