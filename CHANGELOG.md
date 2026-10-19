# Changelog

## 0.5.0

- **Plugin id / package** — `org.konaframework.idea` (Maven group `org.konaframework`);
  brand icons refreshed from the monorepo mark.
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
