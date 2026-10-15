# Changelog

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
- Kona menu: layout, routing, Maven modules, kona-lsp help; **New → Crema View**.

## 0.1.0

- Initial release: native Crema file type, HTML-layered highlighter, directive
  completion, unknown-directive annotator, `{{ }}` typed handler, Grind run
  configuration, Kona menu LSP help.
