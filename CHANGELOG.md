# Changelog

## 0.3.0

- **No LSP** — removed kona-lsp menu/help; all Crema/IDE features are native
  IntelliJ APIs (CompletionContributor, PsiReference, GotoDeclarationHandler).
- **Crema `@` completion fixed** — works with the HTML template data language;
  autopopup when typing `@`.
- **View names** — autocomplete + Go to Declaration (**Ctrl+B** / Ctrl+Click,
  ⌘B on macOS) for `Crema.view` / `Brew.view` / `engine.make` / `@extends` /
  `@include` / …
- **DB columns** — completion from Extract migration parsing and optional live
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
  typed Router handlers, Extract, Auth, filesystem disks, Queue `Bus`, Schedule,
  Crema — no `Controller@method` teaching.
- Kona menu: layout, routing, Maven modules; **New → Crema View**.

## 0.1.0

- Initial release: native Crema file type, HTML-layered highlighter, directive
  completion, unknown-directive annotator, `{{ }}` typed handler, Grind run
  configuration.
