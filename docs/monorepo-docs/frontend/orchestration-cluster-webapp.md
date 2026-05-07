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
│   ├── assets/svg/         # SVG sources (see Generating SVG components)
│   ├── modules/            # This folders contains all app parts separated by feature (e.g. login, http, errors, etc.)
│   ├── pages/              # Standalone pages. Each file assembles all compponents of a page. Like main page content, error, loading, etc.
│   ├── routes/             # TanStack Router file-based routes. Each page is plugged into a route here.
│   ├── vitest-modules/     # Unit test utils
│   └── main.tsx            # App entry
├── test/                   # Playwright based tests
│   ├── a11y/               # accessibility (Axe)
│   ├── integration/        # MSW-mocked integration
│   ├── visual/             # visual regression
│   ├── pw-modules/         # shared fixtures (MSW + Axe)
│   └── pages/              # page objects
├── shared-test-modules/    # Test utils shared between unit and Playwright tests
└── vite.config.ts
```

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
| `generate:svg`            | Convert `src/assets/svg/` to React components (see [Generating SVG components](./development-process/generating-svg-components.md)) |

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
