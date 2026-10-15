# Kona Idea (IntelliJ IDEA)

Native JetBrains plugin for [Kona](https://github.com/konaframework/kona) —
Crema language support, scaffold-aware project markers, Grind run configurations
(including Netty `serve`), and live/file templates for current public APIs.
**Not** TextMate and **not** LSP4IJ by default.

| | |
| --- | --- |
| **Releases** | [GitHub Releases](https://github.com/konaframework/kona-idea/releases) (`.zip`) |
| **Framework** | [konaframework/kona](https://github.com/konaframework/kona) |
| **Sister extension** | [ide-support/vscode-crema](https://github.com/konaframework/kona/tree/main/ide-support/vscode-crema) in the Kona monorepo |
| **Docs** | [IntelliJ](https://docs.konaframework.dev/docs/language/intellij) (when published) |

**Supported products:** IntelliJ IDEA Community and Ultimate (2024.2+).
Installing, updating, or removing the plugin requires an IDE restart.

---

## How it works

```text
Kona app (kona new / grind scaffold)
  src/main/{kotlin|java}/bootstrap|app|routes|config|database
  src/main/resources/views/*.crema.html
  ./grind  +  storage/
        │
        ├── Folder icons on canonical layout dirs
        ├── Grind Serve run config (Netty, :4000, virtual threads)
        └── Crema + live/file templates (typed Router handlers)

*.crema.html / *.crema.kt
        │
        ▼
  Crema file type (overrider + detector beat HTML)
        ├── Native CremaLexer + layered HTML highlighter
        ├── @directive completion + unknown-directive warnings
        └── {{ }} typed handler
```

Optional [kona-lsp](https://github.com/konaframework/kona/tree/main/modules/lsp)
remains for other editors — **Kona → Show kona-lsp Setup Help**.

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
5. Run **Grind Serve** → browse `http://127.0.0.1:4000`

### Sandbox IDE (developers)

```bash
./gradlew runIde        # IntelliJ with this plugin loaded
```

---

## Features (0.2.0)

### Crema
- File type for `*.crema.html` and `*.crema.kt`
- Native highlighter: HTML + `@directives`, `{{ }}`, `{!! !!}`, `{{-- --}}`
- Directive completion and unknown-directive warnings
- `{{` auto-closes as `{{  }}`
- **New → Crema View**

### Project structure (scaffold parity)
Recognizes the Laravel-shaped tree from `kona new`:
`bootstrap/`, `app/`, `routes/`, `config/`, `database/`, `resources/views|public|lang`,
`storage/`, app-local `./grind`, and `dev.kona:*` Gradle dependencies.
Canonical folders get the Kona icon in the project view.

### Grind + Netty HTTP
- **Grind** run configuration type with persisted arguments
- Host / port fields for `serve` (defaults `127.0.0.1` / `4000`)
- Auto-creates **Grind Serve** when opening a detected Kona app
- Serve uses the framework’s **Netty** `KonaHttpServer` + **virtual threads**
  (not the old JDK `HttpServer`)

### Tooling / APIs
- Live templates: `kroute`, `jroute`, `kextract`, `kauth`, `kdisk`, `kqueue`,
  `kschedule`, `kcrema`, `kserve`
- File templates: Controller (Kotlin/Java), Crema view, WebRoutes (typed handlers)
- **Kona** menu: layout help, routing help (typed instance/lambda — no `Class@method`),
  Maven `dev.kona` modules, kona-lsp setup

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

## License

Apache-2.0 (same as Kona).
