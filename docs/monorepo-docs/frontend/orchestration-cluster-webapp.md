# Orchestration cluster webapp

`@camunda/orchestration-cluster-webapp` is the unified React webapp that
will replace the Operate, Tasklist, and Admin frontends shipped today
from `operate/client`, `tasklist/client`, and `identity/client`.

## Tech stack

| Concern         | Library                                                 | Notes                                         |
| --------------- | ------------------------------------------------------- | --------------------------------------------- |
| Framework       | React 19, TypeScript                                    | Strict mode                                   |
| Bundler         | Vite                                                    | Dev on `:3000`, preview on `:3003`            |
| Routing         | TanStack Router (+ Vite plugin)                         | File-based; `routeTree.gen.ts` auto-generated |
| Server state    | TanStack Query                                          | See [Data loading](./data-loading.md)         |
| Client state    | MobX                                                    | Used for theme + session                      |
| Forms           | react-final-form + Zod                                  | See [Forms](./forms.md)                       |
| API contracts   | `@camunda/camunda-api-zod-schemas`                      | Runtime-validated                             |
| Design system   | Carbon (`@carbon/react`) + Camunda composite components | Sass for styles                               |
| Telemetry       | Mixpanel + Osano consent                                |                                               |
| Unit tests      | Vitest browser mode (Playwright provider)               | See [Testing](./testing.md)                   |
| E2E / a11y / VR | Playwright + Axe + MSW                                  |                                               |

## Directory layout

```
apps/orchestration-cluster-webapp/
├── src/
│   ├── operate/            # Operate pod (internal structure owned by the pod)
│   ├── tasklist/           # Tasklist pod (internal structure owned by the pod)
│   ├── admin/              # Admin pod (internal structure owned by the pod)
│   ├── shared/             # Cross-pod shared code (see Shared below)
│   │   ├── auth/           # Session & authentication
│   │   ├── browser-storage/# Typed storage abstraction
│   │   ├── config/         # Boot & client configuration
│   │   ├── errors.ts       # Error classes
│   │   ├── http/           # Endpoints, queries, request wrapper
│   │   ├── i18n/           # Internationalization
│   │   ├── login/          # Login UI components
│   │   ├── svg/            # Generated SVG components
│   │   ├── theme/          # Theme provider (MobX)
│   │   ├── tracking.tsx    # Analytics (Mixpanel)
│   │   ├── pages/          # Shared pages (login, errors, forbidden, 404)
│   │   ├── assets/svg/     # SVG sources (see Generating SVG components)
│   │   └── feature-flags.ts
│   ├── routes/             # TanStack Router file-based routes (shared)
│   ├── vitest-modules/     # Unit test infrastructure
│   └── main.tsx            # App entry
├── test/                   # Playwright based tests
│   ├── a11y/               # Accessibility (Axe)
│   ├── integration/        # MSW-mocked integration
│   ├── visual/             # Visual regression
│   ├── pw-modules/         # Shared fixtures (MSW + Axe)
│   └── pages/              # Page objects
├── shared-test-modules/    # Test utils shared between unit and Playwright tests
└── vite.config.ts
```

## Pod areas

Each pod owns its directory (`src/operate/`, `src/tasklist/`, `src/admin/`) and is
**free to define its own internal structure**. There is no prescribed layout inside
a pod. Pods decide their own folder names, naming conventions, and internal patterns.

Path aliases give each pod a clean import boundary:

| Alias | Resolves to |
| ---- | ---- |
| `#/operate/*` | `src/operate/*` |
| `#/tasklist/*` | `src/tasklist/*` |
| `#/admin/*` | `src/admin/*` |
| `#/shared/*` | `src/shared/*` |

## Shared

`src/shared/` holds cross-cutting infrastructure that all pods can import.
Changes here affect every pod, so they require cross-pod coordination.

Current shared modules: `auth`, `browser-storage`, `config`, `errors`, `http`,
`i18n`, `login`, `svg`, `theme`, `tracking`, plus shared pages (login,
error states, 404) and feature flags.

Keep shared code **focused and small**. A shared module owns one concern.
When a concern is relevant to only one pod, keep it in that pod's area.

### Filename conventions for shared code

Keep filenames consistent so the role of each file is obvious at a glance:

| Kind      | Pattern          | Example                  |
| --------- | ---------------- | ------------------------ |
| Hook      | `use*.ts(x)`     | `useAuth.ts`             |
| Store     | `*.store.ts`     | `session.store.ts`       |
| Component | `PascalCase.tsx` | `LoadingSpinner.tsx`     |
| Page      | `*Page.tsx`      | `DashboardPage.tsx`      |
| Unit test | `*.test.ts(x)`   | `request.test.ts`        |

Co-located styles and tests mirror the component name (e.g. `DashboardPage.module.scss`,
`DashboardPage.test.tsx`). Pod areas may adopt these conventions or define their own.

## Scripts

| Script                    | What it does                                                                                                                        |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `dev`                     | Vite dev server on `:3000`, opens the browser                                                                                       |
| `build`                   | Production bundle, then `renameProdIndex.mjs` post-build                                                                            |
| `build:visual-regression` | Production build with the visual-regression entry point                                                                             |
| `preview`                 | Serve the production build on `:3003`. Used for visual regression tests.                                                            |
| `typecheck`               | Run `tsc` against `tsconfig.browser.json`, `tsconfig.vitest.json`, `tsconfig.node.json`                                             |
| `extract-sbom`            | Vite build with the SBOM Rollup plugin                                                                                              |
| `test:unit`               | Vitest in headless Chromium                                                                                                         |
| `test:unit:ui`            | Vitest with a visible browser                                                                                                       |
| `test:a11y`               | Playwright a11y projects (light + dark)                                                                                             |
| `test:visual`             | Playwright visual-regression projects (light/dark × desktop/tablet)                                                                 |
| `test:integration`        | Playwright integration project (MSW-mocked)                                                                                         |
| `generate:svg`            | Convert `src/shared/assets/svg/` to React components (see [Generating SVG components](./development-process/generating-svg-components.md)) |

## Dev server & backend integration

- Vite dev server on `:3000`, with proxies to the orchestration-cluster
  backend on `:8080`:
  - `/v2` → `http://localhost:8080`
  - `/login` (POST only) → `http://localhost:8080`
  - `/logout` (POST only) → `http://localhost:8080`
- Auth is session-cookie based with a CSRF token: the `request()` wrapper
  appends `X-CSRF-TOKEN` (read from `sessionStorage`) to mutating
  requests, and a `401` clears the React Query cache.
- Endpoint shapes come from `@camunda/camunda-api-zod-schemas`, so
  responses are validated at runtime.

## Testing approach

- **Unit** — Vitest browser mode (Playwright provider, headless
  Chromium). HTTP mocks are done via MSW.
- **Integration** — Playwright project, MSW via `@msw/playwright` for
  request interception.
- **Accessibility** — Playwright + `@axe-core/playwright`, in light and
  dark themes.
- **Visual regression** — Playwright; uses a containerized browser
  (`CONTAINERIZED_BROWSER=true` runs the official `mcr.microsoft.com/playwright`
  image) for stable rendering across machines.

See [Testing](./testing.md) for the full guide.
