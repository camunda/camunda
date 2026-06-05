---

name: frontend-feature
description: Use when creating new pages, components, modules, or features in the orchestration cluster webapp at webapp/client/apps/orchestration-cluster-webapp/. Use when adding routes, data loading, forms, API integration, or UI components. Trigger whenever someone is building or modifying frontend feature code in webapp/client/, even for small changes like adding a column, filter, or panel.

---

# Frontend Feature Development

Reference for building features in `@camunda/orchestration-cluster-webapp` — the unified React frontend replacing Operate, Tasklist, and Admin. The app uses React 19, TypeScript, Vite, TanStack Router (file-based), TanStack Query, MobX (theme + session), Carbon Design System, and SCSS.

## Key rules

- Use Carbon Design System components (`@carbon/react`). Introducing alternative UI libraries fragments the design language and creates maintenance burden.
- Use `#/` path aliases for all imports. Each pod has its own alias; shared infrastructure has one too:
  - `#/operate/*` → `src/operate/` (Operate pod)
  - `#/tasklist/*` → `src/tasklist/` (Tasklist pod)
  - `#/admin/*` → `src/admin/` (Admin pod)
  - `#/shared/*` → `src/shared/` (cross-pod shared code)
- Prefer **types** from `@camunda/camunda-api-zod-schemas` to type API responses — trust the API contract. Use Zod schema validation only for **user input** (forms, URL search params, path params). For general validation needs beyond API contracts, use Zod directly.
- Pod areas (`src/operate/`, `src/tasklist/`, `src/admin/`) are **autonomous** — pods decide their own internal folder structure, naming conventions, and patterns. Do not prescribe internal layout for another pod's area.
- `src/shared/` holds cross-cutting infrastructure (http, auth, config, errors, theme, i18n, tracking, feature flags). Changes here affect all pods; keep shared code focused and small.
- Route files live in `src/routes/_auth/{pod}/` and are **thin wrappers**: they wire the pod's page component into the router. No feature logic in route files.
- Use a single `export {}` block at the end of each file — no inline `export` on declarations. Only export symbols that are actually imported by other files. Don't export internal helpers, types used only within the same file, or constants that nothing else references. This keeps the public surface minimal and scannable.
- YAGNI — don't build abstractions for hypothetical future use. Three similar lines beat a premature wrapper. Wait until a real requirement forces the shape.

## Code style

- **Booleans**: prefix with `is` (e.g., `isLoading`, `isVisible`).
- **Constants**: `SCREAMING_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`).
- **Components**: `PascalCase` (e.g., `DashboardHeader`).
- **Pages**: `PascalCase` + `Page` suffix (e.g., `DashboardPage`); co-located styles and tests use the same name (`DashboardPage.module.scss`, `DashboardPage.test.tsx`).
- **Comments**: avoid them. Code should explain itself. When a comment is necessary, explain *why*, not *how*.
- **Memoize derived data**: when creating new values inside a component (e.g., `.map()`, `.filter()`, transformations), always wrap in `useMemo` to avoid recomputing on every render.
- **SCSS spacing**: use Carbon design token CSS variables for spacing (e.g., `$spacing-05`, `var(--cds-spacing-05)`). Never hardcode pixel or rem values for layout spacing.

## Building a feature

A feature lives in the pod's area and plugs into the shared router:

1. **Pod area** (`src/{operate,tasklist,admin}/`) — build your page component and any supporting code here. Internal structure (folder names, conventions, depth) is the pod's decision. Reuse existing shared modules before creating new ones in the pod area.
2. **`src/shared/`** — for cross-cutting concerns used by more than one pod: http, auth, config, errors, theme, i18n, tracking, feature flags. Keep shared code focused; one module per concern.
3. **`src/routes/_auth/{pod}/`** — thin route file. File path = URL path. Auth-gated routes go under `_auth/`. The route owns the loader, `pendingComponent`, and `errorComponent`. It imports the page component from the pod area.

Default to a new route for anything a user can navigate to. Skip a route only for transient overlays (toasts, hover cards), non-linkable modals (confirmations), or in-page tabs sharing the same data (encode as `?tab=...` search param).

## URL as state

The URL holds the page's state. Components read from it, not from each other.

- **Route params**: entity identity (`/_auth/processes/$processKey`).
- **Search params**: view state — filters, sort, cursor, selection, active tab, modal-open flag.
- **Local React state**: ephemeral UI only — open menu, input draft, hover, focus.

Validate all URL inputs with Zod. Use `validateSearch` for search params and `parseParams` for path params. Reuse schemas from `@camunda/camunda-api-zod-schemas` when the URL slice maps to an API contract; otherwise co-locate the schema in the route file.

## Data loading

Uses TanStack Router loaders + TanStack Query. Three tiers in descending order of preference:

1. **Route loader + suspense queries** — `queryClient.ensureQueryData` in the loader, `useSuspenseQuery` in the component. Same `queryOptions` constant for both.
2. **Route loader + `pendingComponent`** — same as above, but fire-and-forget the loader (don't `await`) and add a `pendingComponent` skeleton so the page doesn't freeze.
3. **Streamed promises + granular skeletons** — `await` the fast query, fire-and-forget the slow one, wrap the slow slice in its own `<Suspense>`.

`queryOptions` constants live in `#/shared/http/queries.ts` — the central query dictionary. API endpoint definitions (URL + method + types) live in `#/shared/http/endpoints.ts`. Do not co-locate queries or endpoints with individual pod areas; keep them in one place so the full set is visible and discoverable. Error handling defaults to `errorComponent` on the route. 401s are handled centrally by the `request()` wrapper (cache clear + login redirect).

## Before starting a feature

Work through these Camunda-specific considerations. They shape the page architecture:

- **API schemas**: check if the endpoints exist in `@camunda/camunda-api-zod-schemas`. If not, add them as part of your work.
- **Pagination**: most list endpoints paginate. Default to infinite scroll with `useSuspenseInfiniteQuery`. Trust `hasMoreTotalItems`, not `totalItems`. Prefer cursor-based pagination over offset for performance.
- **Permissions**: authorization is server-side. For actions, leave the button visible; surface a toast on 403. For data loads, render a forbidden state (page-level or section-level). A 403 can also mean a feature is disabled for the deployment.
- **Eventual consistency**: reads from secondary storage are eventually consistent. If the OpenAPI spec flags `x-eventually-consistent`, poll with `refetchInterval`. Default to pessimistic UI — reach for optimistic updates only with an explicit reconciliation plan.
- **Batch operations**: POST returns a `batchOperationKey`. Poll `GET /v2/batch-operations/{key}` for state. Show a toast confirming submission, poll in the background, surface the result when it lands. Never block the page on the poll.
- **Multi-tenancy**: deployment-level toggle. Render tenant picker, columns, and filters only when it's on. Always pass the active tenant in requests when enabled.

## Forms

Decision rule: use plain HTML `<form>` for simple forms. Reach for react-final-form when the form needs schema-based validation or form/field meta state (`dirty`, `touched`, submission state, field arrays). Validate on submit (whole form) and on blur (single field) — never on every keystroke. Reuse Zod schemas from `@camunda/camunda-api-zod-schemas` where the form maps to an API contract.

## Feature flags

When a feature is not ready for users but code needs to merge to `main`, gate it behind a flag. Export a boolean `const` from `src/shared/feature-flags.ts`, `SCREAMING_SNAKE_CASE`, default `false`. Gate at the highest possible level — route, page, or nav item — not deep inside a pod area. Remove flags in a dedicated cleanup PR once the feature ships.

## Local checks before commit

Run these commands:

    # From webapp/client/
    npm run lint             # ESLint + Prettier (workspace)

    # From webapp/client/apps/orchestration-cluster-webapp/
    npm run typecheck        # TypeScript across all tsconfigs
    npm run test:unit        # Vitest browser mode (headless Chromium)

## Canonical docs

- `docs/monorepo-docs/frontend/orchestration-cluster-webapp.md` — tech stack, layout, scripts, testing overview.
- `docs/monorepo-docs/frontend/data-loading.md` — TanStack Router + Query patterns with full examples.
- `docs/monorepo-docs/frontend/forms.md` — form library guidance.
- `docs/monorepo-docs/frontend/development-process/creating-a-new-page.md` — step-by-step with checklist.
- `docs/monorepo-docs/frontend/development-process/before-starting.md` — pre-feature considerations.
- `docs/monorepo-docs/frontend/development-process/extending-an-existing-page.md` — incremental changes.
- `docs/monorepo-docs/frontend/development-process/working-on-large-feature.md` — PR splitting and feature flags.
- `docs/monorepo-docs/frontend/code-style.md` — naming, exports, comments.
