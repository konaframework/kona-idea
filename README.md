# Kona Idea (IntelliJ IDEA)

Native JetBrains plugin for [Kona](https://github.com/konaframework/kona) —
Crema file type, HTML-layered highlighting, directive completion, and Grind
run configurations. **Not** TextMate and **not** LSP4IJ by default.

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
*.crema.html / *.crema.kt
        │
        ▼
  Crema file type (overrider + detector beat HTML)
        │
        ├── Native CremaLexer + layered HTML highlighter
        ├── @directive completion + unknown-directive warnings
        ├── {{ }} typed handler
        └── Grind run configuration (./grind …)
```

Optional [kona-lsp](https://github.com/konaframework/kona/tree/main/modules/lsp)
remains for other editors and for users who wire an LSP client themselves —
**Kona → Show kona-lsp Setup Help**.

---

## Install

1. **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [Releases](https://github.com/konaframework/kona-idea/releases).
2. Restart when prompted.
3. Open a Crema view (`resources/views/….crema.html`). Confirm the language is
   **Crema** (not HTML).

---

## Features (0.1.0)

- Crema language + file type for `*.crema.html` and `*.crema.kt`
- Native highlighter: HTML spans + `@directives`, `{{ }}`, `{!! !!}`, `{{-- --}}`
- Directive completion (Ctrl+Space on `@…`)
- Warning annotator for unknown `@name` tokens
- `{{` auto-closes as `{{  }}`
- **Grind** run configuration type
- **Kona** menu → LSP setup help

---

## Develop

```bash
./gradlew test
./gradlew buildPlugin   # → build/distributions/*.zip
./gradlew runIde        # sandboxed IntelliJ with the plugin
```

JDK 17+. Coverage gate: ≥ 98% line on verified product logic (thin Platform
wiring excluded — see `build.gradle.kts`).

---

## License

Apache-2.0 (same as Kona).
