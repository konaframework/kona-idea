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

**Supported products:** IntelliJ IDEA Community and Ultimate (2024.2+).
Installing, updating, or removing the plugin requires an IDE restart.

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
        │
        ▼
  Crema file type (overrider + detector beat HTML)
        ├── Native CremaLexer + layered HTML highlighter
        ├── @directive completion (autopopup on @) + unknown-directive warnings
        ├── View name refs → Ctrl+B / Ctrl+Click
        └── {{ }} typed handler
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

## Features (0.3.0)

### Crema (native)
- File type for `*.crema.html` and `*.crema.kt`
- Native highlighter: HTML + `@directives`, `{{ }}`, `{!! !!}`, `{{-- --}}`
- **`@` directive completion** with autopopup (works with the HTML template layer)
- Unknown-directive warnings (emails like `hi@example.com` ignored)
- `{{` auto-closes as `{{  }}`
- **New → Crema View**

### View names
- Autocomplete dotted names (`home`, `layouts.app`) in `Crema.view` / `Brew.view` /
  `engine.make` / `engine.render` / `@extends` / `@include*` / `@component`
- **Go to Declaration:** **Ctrl+B** or **Ctrl+Click** (macOS: **⌘B** / **⌘Click**)
  opens the `.crema.html` / `.crema.kt` file
- Help: **Kona → Show View Navigation Help**

### DB columns
- Completes column names in Cadence / QueryBuilder call sites (`where`, `select`,
  `orderBy`, `create(mapOf(…))`, …)
- Sources (merged when both available):
  1. **Migrations** under `src/main/{kotlin|java}/database/migrations`
  2. **JDBC** — if `.env` has `DB_URL` (and optional `DB_USERNAME` / `DB_PASSWORD` /
     `DB_DRIVER`) and a driver can connect, columns come from `DatabaseMetaData`

### Tool window (RIGHT)
- **View → Tool Windows → Kona** (or the Kona icon on the right strip)
- Quick actions: Serve, List, Migrate, Queue work, Schedule work, New Crema View,
  layout/routing/Maven help, docs links

### Project structure
Recognizes the scaffold tree from `kona new`:
`bootstrap/`, `app/`, `routes/`, `config/`, `database/`, `resources/views|public|lang`,
`storage/`, app-local `./grind`, and `dev.kona:*` Gradle dependencies.
Canonical folders use the official Kona brand mark.

### Grind + Netty HTTP
- **Grind** run configuration type with persisted arguments
- Host / port fields for `serve` (defaults `127.0.0.1` / `4000`)
- Auto-creates **Grind Serve** when opening a detected Kona app
- Serve uses the framework’s **Netty** `KonaHttpServer` + **virtual threads**

### Tooling / APIs
- Live templates: `kroute`, `jroute`, `kcadence`, `kauth`, `kdisk`, `kqueue`,
  `kschedule`, `kcrema`, `kserve`
- File templates: Controller (Kotlin/Java), Crema view, WebRoutes (typed handlers)
- **Kona** menu: layout, routing, Maven modules, view navigation help

---

## Verify in the IDE (`./gradlew runIde`)

1. **No LSP** — there is no kona-lsp menu item; completion works offline.
2. **Crema `@`** — open `*.crema.html`, type `@` → directive popup (`@csrf`, `@extends`, …).
3. **Tool window** — open **Kona** on the right → **Serve (Netty :4000)**.
4. **Icons** — tool window, Crema files, marked folders, Grind configs show the Kona mark.
5. **DB columns** — with a migration defining `string("email")`, type `User.where("` → `email`.
6. **Views** — in `Crema.view(engine, "|")` or `@extends('|')`, complete names; **Ctrl+B** opens the file.

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
   - if Marketplace secrets are configured, run `publishPlugin` (signs, then uploads)

Optional: Actions → **Publish release** → Run workflow on the tag ref
(`skip_marketplace` to publish GitHub-only).

### Required GitHub Actions secrets

Configure under **Settings → Secrets and variables → Actions**:

| Secret | Purpose |
| --- | --- |
| `PUBLISH_TOKEN` | JetBrains Marketplace permanent token ([My Tokens](https://plugins.jetbrains.com/author/me/tokens)). Alias: `JETBRAINS_PUBLISH_TOKEN`. |
| `CERTIFICATE_CHAIN` | Marketplace plugin-signing certificate chain (PEM). |
| `PRIVATE_KEY` | Marketplace plugin-signing private key (PEM). |
| `PRIVATE_KEY_PASSWORD` | Password for `PRIVATE_KEY`. |
| `GITHUB_TOKEN` | Automatic; workflow only needs `contents: write` for Releases. |

Optional env (workflow / local): `PUBLISH_CHANNEL` — non-default channel
(`beta`, `alpha`, `eap`, …). Unset = Stable (`default`).

Generate signing materials per
[Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).
Never commit tokens or PEMs.

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
# with secrets in the environment only:
#   PUBLISH_TOKEN=… CERTIFICATE_CHAIN=… PRIVATE_KEY=… PRIVATE_KEY_PASSWORD=…
#   ./gradlew publishPlugin
```

---

## License

Apache-2.0 (same as Kona).
