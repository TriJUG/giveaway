# TriJUG Giveaway System — Implementation Plan

## Overview

Three components: a public entry form, a REST API backend, and an admin TUI. Everything runs on Cloudflare infrastructure. GitHub Actions handles all deployments.

---

## Architecture

```
giveaway.trijug.dev        →  Cloudflare Pages  (static HTML/JS form)
giveaway.trijug.dev/api/*  →  Cloudflare Worker (REST API, TypeScript)
                                      │
                                      ▼
                               Cloudflare D1 (SQLite)

Admin TUI (local)  ───────────→  same Worker API  (Bearer token)
```

---

## Data Model (D1 / SQLite)

> **Note on `TEXT` dates:** SQLite has no native `DATE`/`DATETIME` type. The standard convention is ISO 8601 `TEXT` (e.g. `"2026-03-28T19:00:00"`), which sorts and compares correctly as a string. `datetime('now')` returns exactly this format.

```sql
CREATE TABLE giveaways (
  id          TEXT PRIMARY KEY,        -- UUID
  name        TEXT NOT NULL,           -- display name, e.g. "March 2026 Meetup"
  date        TEXT NOT NULL,           -- ISO date, e.g. "2026-03-28"
  active      INTEGER DEFAULT 0,       -- 1 = currently accepting entries
  created_at  TEXT DEFAULT (datetime('now'))
);

CREATE TABLE entries (
  id          TEXT PRIMARY KEY,        -- UUID
  giveaway_id TEXT NOT NULL REFERENCES giveaways(id),
  name        TEXT NOT NULL,
  email       TEXT NOT NULL,
  created_at  TEXT DEFAULT (datetime('now')),
  UNIQUE(giveaway_id, email)           -- silent dedup: same email = same entry
);

CREATE TABLE selections (
  id          TEXT PRIMARY KEY,        -- UUID
  giveaway_id TEXT NOT NULL REFERENCES giveaways(id),
  entry_id    TEXT NOT NULL REFERENCES entries(id),
  kept        INTEGER NOT NULL,        -- 1 = accepted winner, 0 = rejected (not present/ineligible)
  selected_at TEXT DEFAULT (datetime('now'))
);
```

**Notes:**
- Duplicate email per giveaway: `INSERT OR IGNORE` — silently dropped, no error shown to user.
- No cross-giveaway dedup. Dave Smith in January and Dave Smith in February are unrelated rows. Honor system.
- A giveaway can produce multiple winners (multiple `kept = 1` rows). The TUI keeps drawing until the admin stops.

---

## Component 1: Cloudflare Worker (API)

**Runtime:** Cloudflare Workers (TypeScript)
**Database:** Cloudflare D1 bound to the worker
**Auth:** Single admin bearer token stored as a Cloudflare Worker secret (`ADMIN_TOKEN`)

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/giveaways` | Public | List all giveaways |
| GET | `/api/giveaways/active` | Public | Get the currently active giveaway (id + name + date only) |
| POST | `/api/giveaways` | Admin | Create a giveaway |
| PATCH | `/api/giveaways/:id` | Admin | Update name, date, or active flag |
| POST | `/api/giveaways/:id/entries` | Public | Submit an entry (name + email) |
| GET | `/api/giveaways/:id/entries` | Admin | List all entries |
| POST | `/api/giveaways/:id/entries` | Admin | Manually add an entry |
| DELETE | `/api/entries/:id` | Admin | Delete an entry |
| POST | `/api/giveaways/:id/selections` | Admin | Record a selection (kept: true/false) |
| GET | `/api/giveaways/:id/selections` | Admin | List all selections for a giveaway |

**Auth:** `Authorization: Bearer <ADMIN_TOKEN>` header. Requests without it to admin endpoints get `401`.

**Public entry rules:**
- Only accepts submissions when a giveaway is `active = 1`. Returns `409` if none is active.
- Duplicate email per giveaway: `INSERT OR IGNORE`.
- Confirmation response only echoes back name, not email.

---

## Component 2: Public Entry Form (Cloudflare Pages)

**Stack:** Plain HTML + vanilla JS (zero build step, zero dependencies)
**URL:** `giveaway.trijug.dev`

### Behavior
1. On load, fetches `GET /api/giveaways/active`.
   - Active giveaway found: shows the form with the giveaway name in the heading.
   - None found: shows "No giveaway is currently open."
2. Form fields: **Name** (text) and **Email** (email).
3. On submit: `POST /api/giveaways/:id/entries`. Inline success/error message — no page reload.
4. After successful submission the form is replaced with "You're entered! Good luck."

### Design
- Single-page, mobile-friendly.
- TriJUG branding: red (`#CC0000`) primary, white/light gray background, dark text.
- No login, no cookies, no tracking.

---

## Component 3: Admin TUI (`trijug`)

**Language:** Java 21+
**UI framework:** TamboUI `tamboui-toolkit` (Toolkit DSL / `ToolkitApp` — Level 3 API)
**Terminal backend:** `tamboui-jline3-backend`
**Build tool:** Gradle
**Distribution:** GraalVM `native-image` → single native binary (`trijug`)
**Config:** `TRIJUG_API_URL` and `TRIJUG_ADMIN_TOKEN` read from environment variables, falling back to `~/.config/trijug/config` (a simple `KEY=VALUE` file). The TUI will error clearly on startup if either value is missing.

Launched with a single command: `trijug`. Everything is navigated from within the TUI — no subcommands.

### Header (persistent across all screens)

Every screen has a fixed header panel containing:
- **Left:** Duke ASCII art — _placeholder, to be sourced separately (needs pointy head, star on forehead, round body — not Tux)_
- **Right top:** "TriJUG Giveaway" in bold red
- **Right bottom:** Active giveaway name + date in dim text

### Home Screen

Full-screen menu on launch:

```
┌─────────────────────────────────────────┐
│  [Duke]   TriJUG Giveaway               │
│           March 2026 Meetup · 2026-03-28│
├─────────────────────────────────────────┤
│                                         │
│   › Pick Winner                         │
│     Manage Entries                      │
│     Manage Giveaways                    │
│     Quit                                │
│                                         │
└─────────────────────────────────────────┘
  ↑/↓ navigate   Enter select
```

### Screen: Pick Winner

1. Fetches all entries for the active giveaway; removes any already `kept = 1`.
2. **Animation:** names cycle rapidly in a large center panel (slot machine style using TamboUI `canvas` or `WaveText`). Speed decreases over ~5 seconds and lands on one name.
3. Winner name is shown large and bold in red.
4. Prompt: **[K] Keep** (present and eligible) / **[R] Re-roll** (not present or ineligible).
5. **Keep:** POSTs `{ kept: true }`. Shows winner confirmation. Then: **[A] Pick another** / **[Q] Done**.
6. **Re-roll:** POSTs `{ kept: false }`. Removes from local pool. Immediately reruns animation.
7. A dim sidebar tracks all names drawn this session (kept and rejected).
8. If pool is exhausted: "No eligible entries remaining."

### Screen: Manage Entries

Interactive table of all entries for the active giveaway:
- Columns: `#`, `Name`, `Email`, `Entered At`
- `↑/↓` navigate, `D` delete with confirmation, `A` add new (inline form), `Esc` back to home

### Screen: Manage Giveaways

Table of all giveaways:
- Columns: `Name`, `Date`, `Active`, `Entries`
- `↑/↓` navigate, `N` create new (inline form: name, date, set active?), `Space` toggle active, `Esc` back

---

## Repository Structure

```
github.com/TriJUG/trijug-giveaway  (public)

trijug-giveaway/
├── packages/
│   ├── api/                        # Cloudflare Worker (TypeScript)
│   │   ├── src/
│   │   │   ├── index.ts            # Worker entrypoint + router
│   │   │   ├── auth.ts             # Bearer token middleware
│   │   │   └── routes/             # One file per resource
│   │   ├── migrations/
│   │   │   └── 0001_initial.sql    # D1 schema
│   │   └── wrangler.toml
│   ├── web/                        # Cloudflare Pages (static)
│   │   └── public/
│   │       ├── index.html
│   │       └── app.js
│   └── tui/                        # Java TamboUI TUI
│       ├── src/main/java/dev/trijug/giveaway/
│       │   ├── Main.java
│       │   ├── screens/            # One class per TUI screen
│       │   ├── components/         # Shared TamboUI components (header, etc.)
│       │   └── api/                # HTTP client (java.net.http)
│       ├── build.gradle.kts
│       └── src/main/resources/
│           └── META-INF/native-image/  # GraalVM reflection/resource hints
├── .github/
│   └── workflows/
│       ├── deploy-api.yml
│       └── deploy-web.yml
├── package.json                    # pnpm workspace root (api + web)
└── plan.md
```

**JS monorepo:** pnpm workspaces for `api` + `web`
**Java:** Gradle in `packages/tui/`, independent of the pnpm workspace

---

## Deployment (GitHub Actions)

### Secrets required in the GitHub repo

| Secret | Description |
|--------|-------------|
| `CF_API_TOKEN` | Cloudflare API token (Workers + Pages + D1 permissions) |
| `CF_ACCOUNT_ID` | Cloudflare account ID |

> `ADMIN_TOKEN` is set directly in Cloudflare once via `wrangler secret put ADMIN_TOKEN` — it is never stored in GitHub.

### `deploy-api.yml`
- **Trigger:** push to `main` affecting `packages/api/**`
- **Steps:** install pnpm → `wrangler d1 migrations apply` → `wrangler deploy`
- Migrations are additive; no destructive schema changes in prod

### `deploy-web.yml`
- **Trigger:** push to `main` affecting `packages/web/**`
- **Steps:** deploy `packages/web/public/` to Cloudflare Pages via Wrangler
- No build step — plain HTML/JS

### TUI (no CI)
- Built locally: `./gradlew nativeCompile` (requires GraalVM JDK)
- Produces `packages/tui/build/native/nativeCompile/trijug` — copy to `$PATH`
- Requires local config before first run (see step 9 in setup checklist below)

### First-time setup checklist
1. Register Cloudflare account, install `wrangler`, run `wrangler login`
2. Create D1 database: `wrangler d1 create trijug-giveaway` — copy the DB ID into `wrangler.toml`
3. Set admin token once: `wrangler secret put ADMIN_TOKEN`
4. Create Pages project: `wrangler pages project create trijug-giveaway-web`
5. Add DNS in Cloudflare dashboard: `giveaway.trijug.dev` CNAME → Pages project
6. Add Worker route in `wrangler.toml`: `giveaway.trijug.dev/api/*`
7. Add `CF_API_TOKEN` and `CF_ACCOUNT_ID` as GitHub repo secrets
8. Push to `main` — both workflows deploy automatically
9. Configure the TUI locally — create `~/.config/trijug/config`:
   ```
   TRIJUG_API_URL=https://giveaway.trijug.dev
   TRIJUG_ADMIN_TOKEN=<your token>
   ```
   Or export both as environment variables in your shell profile.

---

## DNS

No API subdomain needed. The Worker handles `/api/*` as a route on the same domain as Pages.

| Record / Route | Target |
|----------------|--------|
| `giveaway.trijug.dev` (CNAME) | Cloudflare Pages |
| `giveaway.trijug.dev/api/*` (Worker route) | Cloudflare Worker |

---

## Decisions Log

| Question | Decision |
|----------|----------|
| Backend | Cloudflare Workers + D1 |
| TUI language | Java 21+, TamboUI Toolkit DSL |
| TUI distribution | GraalVM native binary, built locally |
| Build tool | Gradle |
| TUI navigation | Pure TUI — no CLI subcommands |
| TUI style | Full-screen alternate buffer, slot machine animation |
| TriJUG colors | Red `#CC0000` + white/light gray |
| Duke ASCII | Placeholder — to be sourced/designed separately |
| Prize description on form | No — just "Enter to Win" |
| Past winners display | Not shown |
| Multiple winners | Yes — loop after each "Keep" |
| Duplicate email per giveaway | Silent dedup (INSERT OR IGNORE) |
| Cross-giveaway dedup | None — honor system |
| Admin accounts | One static bearer token |
| Admin UI | TUI only |
| TUI publishing | No — run from repo |
| GitHub org | TriJUG (`github.com/TriJUG/trijug-giveaway`) |
| Repo visibility | Public |

---

## TODO

### Repository & Project Setup
- [x] Create GitHub repo `TriJUG/trijug-giveaway` (public)
- [x] Initialize repo with `.gitignore` (Node + Java + Gradle)
- [x] Add `.gitattributes` to enforce LF line endings (fixes WSL2/Windows CRLF warnings)
- [x] Set up pnpm workspace root (`package.json`, `pnpm-workspace.yaml`)
- [ ] Add `CF_API_TOKEN` and `CF_ACCOUNT_ID` as GitHub repo secrets _(manual — after CF account created)_

### Cloudflare Setup (one-time, manual)
- [ ] Register Cloudflare account
- [ ] Install `wrangler` CLI and run `wrangler login`
- [ ] Create D1 database: `wrangler d1 create trijug-giveaway` — paste DB ID into `wrangler.toml` (marked with TODO comment)
- [ ] Set admin token: `wrangler secret put ADMIN_TOKEN`
- [ ] Create Pages project: `wrangler pages project create trijug-giveaway-web`
- [ ] Add `giveaway.trijug.dev` CNAME in Cloudflare DNS pointing to Pages project
- [ ] Verify `wrangler.toml` Worker route is correct (already uncommented: `giveaway.trijug.dev/api/*`)

### Component 1: API (Cloudflare Worker)
- [x] Scaffold `packages/api/` with TypeScript + Wrangler
- [x] Write `wrangler.toml` (D1 binding, Worker route, custom domain)
- [x] Write `migrations/0001_initial.sql` (giveaways, entries, selections tables)
- [x] Implement bearer token auth middleware (`auth.ts`)
- [x] Implement route: `GET /api/giveaways` — list all giveaways
- [x] Implement route: `GET /api/giveaways/active` — get active giveaway
- [x] Implement route: `POST /api/giveaways` (admin) — create giveaway
- [x] Implement route: `PATCH /api/giveaways/:id` (admin) — update giveaway
- [x] Implement route: `POST /api/giveaways/:id/entries` (public) — submit entry
- [x] Implement route: `GET /api/giveaways/:id/entries` (admin) — list entries
- [x] Implement route: `POST /api/giveaways/:id/entries` (admin) — manually add entry
- [x] Implement route: `DELETE /api/entries/:id` (admin) — delete entry
- [x] Implement route: `POST /api/giveaways/:id/selections` (admin) — record selection
- [x] Implement route: `GET /api/giveaways/:id/selections` (admin) — list selections
- [x] Handle `INSERT OR IGNORE` for duplicate email per giveaway
- [x] Enforce only-one-active constraint on `PATCH` (deactivate others when setting active)
- [x] Validate inputs (non-empty name/email, valid date format, valid UUID refs)
- [x] Return appropriate HTTP status codes (400, 401, 404, 409)

### Component 2: Web Form (Cloudflare Pages)
- [x] Create `packages/web/public/index.html`
- [x] Create `packages/web/public/app.js`
- [x] Fetch active giveaway on load; show "no giveaway open" state if none
- [x] Render name + email form with TriJUG red branding
- [x] Submit entry via `POST /api/giveaways/:id/entries`
- [x] Show inline success message; hide form after submission
- [x] Show inline error message on failure (network error, 409, etc.)
- [x] Make layout mobile-friendly

### Component 3: Admin TUI (Java / TamboUI)
#### Project scaffold
- [x] Create `packages/tui/` Gradle project (Kotlin DSL)
- [x] Add TamboUI BOM + `tamboui-toolkit` + `tamboui-jline3-backend` dependencies (snapshot repo)
- [x] Configure GraalVM `native-image` Gradle plugin
- [x] Write `META-INF/native-image/` reflection/resource hints — added TamboUI binding properties pattern
- [x] Implement config loading: env vars → `~/.config/trijug/config` fallback; fail fast if missing

#### API client
- [x] Implement `ApiClient` using `java.net.http.HttpClient`
- [x] Methods mirroring all admin API endpoints
- [x] Attach `Authorization: Bearer` header on all requests
- [x] Handle errors (401, 404, 409, network failure) and surface them in the TUI

#### Shared header component
- [x] Build persistent header panel (TriJUG block-art logo left, title + giveaway info right)
- [x] Fetch and display active giveaway name + date on startup
- [x] Logo loaded from `trijug.txt` resource with 1-space padding all sides; white `│` divider; right side vertically centered

#### Home screen
- [x] Implement `HomeScreen` with navigable menu (Pick Winner, Manage Entries, Manage Giveaways, Quit)
- [x] Wire up screen transitions

#### Pick Winner screen
- [x] Fetch entry list; filter out already-kept winners
- [x] READY phase: shows all entries as a static list, waits for Space/Enter to start spin
- [x] Implement slot machine animation (names cycling at fixed 80ms/step)
- [x] Display selected winner bold in red, others dimmed
- [x] Implement [K] Keep / [R] Re-roll prompt
- [x] POST selection result to API on either action
- [x] Maintain and display session history sidebar (kept + rejected names)
- [x] Handle pool exhaustion ("No eligible entries remaining")
- [x] Implement [A] Pick another / [Esc] done flow after a Keep
- [x] READY phase — shows full entry list (plain white, no highlight) via `renderSpinView()`; panel title shows eligible count; hint shows Space/Esc
- [x] **BUG: rejected entries re-appear in pool on re-entry** — resolved.
- [x] **BUG: screen flash on Esc** — fixed by collapsing all screens into a single `RootApp extends
      ToolkitApp` with a `ScreenView` interface (`activate` / `deactivate` / `render`). One terminal
      session for the app lifetime; navigation calls `runner.runOnRenderThread(() -> navigate.accept(screen))`
      to safely switch views without tearing down the alternate-screen buffer.

#### Manage Entries screen
- [x] Fetch and render entries table (Name, Email, Entered At)
- [x] Implement `↑/↓` navigation
- [x] Implement `D` — delete with confirmation prompt → DELETE API call
- [x] Implement `A` — inline add form (name + email) → POST API call
- [x] Implement `Esc` — return to home
- [x] **BUG: table rows not rendering** — fixed by dropping the `panel()` wrapper; `TableElement`
      has its own border/title system. Use `table().title(...).rounded().widths(...).fill()` directly.
- [x] **Email obfuscation** — emails masked by default: `todd@ginsberg.com` → `t***@g*******.com`.
      `[V]` toggles reveal/mask. Display only; data and API unaffected.

#### Manage Giveaways screen
- [x] Fetch and render giveaways table (Name, Date, Active)
- [x] Implement `↑/↓` navigation
- [x] Implement `N` — inline create form (name, date, set active?) → POST API call
- [x] Implement `Space` — toggle active flag → PATCH API call
- [x] Implement `Esc` — return to home
- [x] **BUG: same table rendering bug as Entries screen** — fixed same way

#### Polish
- [x] Use `.rounded()` on all panels across all screens for visual consistency — currently only the Home screen menu and a few dialogs use rounded corners; Pick Winner name list, Entries/Giveaways panels, and inline forms should match.
- [x] Panel titles — add a leading space to every panel title string so the text sits one character in from the corner (e.g. `" Menu"` instead of `"Menu"`). Apply consistently across all screens and components.
- [x] Rework Pick Winner selection process and animation — done.

#### Build & distribution
- [x] `./gradlew build` passes cleanly (JAR)
- [x] Verify `./gradlew nativeCompile` produces working binary — 36.6MB, starts cleanly
- [ ] Test binary on target machine _(manual)_

### GitHub Actions
- [x] Write `.github/workflows/deploy-api.yml` (trigger: `packages/api/**` on `main`)
- [x] Write `.github/workflows/deploy-web.yml` (trigger: `packages/web/**` on `main`)
- [x] Add `build-tui.yml` CI workflow — builds native binary on Linux + macOS, uploads as downloadable artifacts (90-day retention); triggers on push to `packages/tui/**` or manually via `workflow_dispatch`
- [ ] Verify both deploy workflows run successfully end-to-end _(after CF account + secrets set up)_

### End-to-end Validation _(manual — requires live Cloudflare deployment)_
- [ ] Submit a test entry via the web form; confirm it appears in D1
- [ ] Use the TUI to create a giveaway and activate it
- [ ] Submit entries; run the Pick Winner flow; confirm selections recorded in D1
- [ ] Test duplicate email submission — confirm silent dedup
- [ ] Test TUI with missing config — confirm clear error message

### Known gaps / future hardening
- [ ] **Rate limiting on public entry endpoint** — Cloudflare Workers rate limiting or a simple IP-based counter to prevent entry flooding. `INSERT OR IGNORE` only dedupes same email, not unique spammers.
- [x] **`Json.java` edge cases** — fixed `\uXXXX` unicode escape decoding; all other cases (`\\`, `\"`, `\/`, null, numbers, booleans) were already correct.
