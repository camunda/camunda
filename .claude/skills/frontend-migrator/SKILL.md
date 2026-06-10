---

name: frontend-migrator
description: Use when migrating, porting, rewriting, or moving frontend code from operate/client/ or tasklist/client/ to the orchestration cluster webapp at webapp/client/apps/orchestration-cluster-webapp/. Trigger whenever someone mentions migrating a legacy page, component, or module to the new unified frontend, converting React Router to TanStack Router, replacing MobX stores with TanStack Query or URL state, rewriting styled-components as SCSS modules, or converting legacy test patterns to Vitest browser mode. Also use when someone asks how a legacy pattern maps to the new architecture, even for small questions like "how would I write this Operate component in the new app?" or "what's the equivalent of this Tasklist store in the unified frontend?"

---

# Frontend Migration: Legacy to Orchestration Cluster Webapp

The orchestration cluster webapp (`webapp/client/apps/orchestration-cluster-webapp/`) is replacing the legacy Operate, Tasklist, and Admin frontends. Migration is a **rewrite using new patterns**, not a code port. The legacy code is the specification of *what* the feature does; the new code follows a different architecture for *how*.

This skill is the translation layer. It maps legacy patterns to their target equivalents so you produce code that fits the new codebase from the start. For target conventions (pod areas + shared structure, data loading tiers, forms, feature flags), defer to the `frontend-feature` skill. For test-writing details, defer to `frontend-unit-test` and `frontend-integration-test`.

## Migration workflow

### 1. Understand the legacy feature's intent

Read the legacy code and extract *what it does for users*, not how it's implemented. Identify:

- What data does it display? Where does that data come from (which API endpoints)?
- What actions can the user take? What side effects do those actions trigger?
- What URL does the page live at? What parameters does it accept?
- What states does it handle? (loading, empty, error, forbidden, pagination)

Focus on behavior. The implementation details (MobX stores, styled-components, React Router hooks) are all going to change.

### 2. Check API endpoint availability

Look up every endpoint the legacy feature calls in `@camunda/camunda-api-zod-schemas`. The legacy code typically imports from this package already — check its `import` statements. If an endpoint is missing from the schema package, it needs to be added before or alongside the migration.

### 3. Map to pod area / shared / routes

Decide how the feature decomposes in the new architecture. The general rule:

- **One route file** per navigable URL, under `src/routes/_auth/<pod>/`
- **Page component and supporting code** in the pod's area (`src/operate/`, `src/tasklist/`, or `src/admin/`). The pod decides its own internal structure — there is no prescribed layout.
- **Shared cross-cutting code** in `src/shared/<concern>/` — http, auth, config, errors, i18n, theme, tracking. Use what's already there; extend it rather than duplicating in the pod area.

Produce the code directly. If the decomposition is genuinely ambiguous (e.g., a legacy page that mixes two concerns and could become one page or two), flag it for the user with your recommendation and reasoning.

Dependencies flow one way: **routes → pod area → shared**. Pod code must never import from a route file. Shared code must never import from a pod area. If a type (like search param schemas) needs to be shared between a route and pod code, define it in the pod area and import it into the route — not the other way around.

### 4. Transform patterns

This is the core of the migration. Each legacy pattern has a target equivalent — apply them systematically. The summary is below; see `references/pattern-mapping.md` for side-by-side code examples.

### 5. Write tests

Rewrite tests using the new patterns (Vitest browser mode, MSW via worker fixture, `expect.element()`). Don't port legacy test code — the testing infrastructure is fundamentally different. See the testing transformation table below and `references/pattern-mapping.md` for full before/after examples.

### 6. Run local checks

    # From webapp/client/
    npm run lint

    # From webapp/client/apps/orchestration-cluster-webapp/
    npm run typecheck
    npm run test:unit

## Pattern transformations

### Routing

| Legacy | Target |
|--------|--------|
| `createBrowserRouter` + `createRoutesFromElements` | TanStack Router file-based routes |
| `lazy={() => import('./Page')}` with `Component` export | `createFileRoute` in a file under `src/routes/` |
| `Paths.processes()` path builder object | File path *is* the URL — `src/routes/_auth/operate/processes/index.tsx` |
| `useParams()` / `useSearchParams()` | `useParams({ from: '/route-path' })` / `useSearch({ from: '/route-path' })` (typed, validated) |
| `useNavigate()` + `navigate(path)` | `useNavigate()` from `@tanstack/react-router` or `<Link>` |
| `useOutletContext()` for parent data | Shared `queryOptions` — child re-calls the same query (TanStack Query deduplicates) |
| `<Navigate to={path} />` redirect | `throw redirect({ to: path })` in `beforeLoad` |
| `ErrorBoundary` per route | `errorComponent` on the route definition |

Auth-gated routes go under `_auth/` (pathless layout route). Product routes go under `_auth/operate/`, `_auth/tasklist/`, or `_auth/admin/`.

### Data fetching

| Legacy | Target |
|--------|--------|
| `use*.query.ts` files scattered across modules | Centralized `queryOptions` in `#/shared/http/queries.ts` |
| Endpoint URL construction inline in hooks | `Request` factories in `#/shared/http/endpoints.ts` |
| `requestAndParse()` / `requestWithThrow()` | `request()` from `#/shared/http/request` |
| `useQuery` / `useInfiniteQuery` in components | `useSuspenseQuery` / `useSuspenseInfiniteQuery` in components |
| No data prefetching | `queryClient.ensureQueryData(queries.xxx())` in route `beforeLoad` |
| `document.title` in `useEffect` | `head()` function on the route |
| `refetchInterval` for polling | Same — `refetchInterval` on the query options |

The pattern is: define the endpoint in `endpoints.ts`, wrap it in `queryOptions` in `queries.ts`, prefetch in the route loader, consume with `useSuspenseQuery` in the pod page component. This replaces the legacy pattern where each feature scattered its own query hooks.

### State management

This is where most legacy code diverges from the target. Legacy apps use MobX stores for everything — server cache, UI state, filters, form drafts. The target splits state across purpose-built mechanisms:

| What the MobX store holds | Target mechanism | Why |
|---------------------------|------------------|-----|
| Server data (fetched from API) | TanStack Query via `queries.ts` | Automatic caching, deduplication, background refresh, garbage collection |
| Filters, sort order, pagination cursor | URL search params with Zod `validateSearch` | Linkable, shareable, survives refresh, back/forward navigation works |
| Selected items, active tab | URL search params | Same reasons — if the user can share the URL and expect the same view, it belongs in the URL |
| Modal open/close, menu open, input draft | `useState` | Ephemeral — losing it on navigation is fine |
| Authentication session | MobX store (`authentication.store.ts`) | Already exists in the target, keep it |
| Theme preference | MobX store | Already exists in the target, keep it |

When you encounter a MobX store in legacy code, don't port it. Decompose it by asking: "Would a user want to bookmark or share this state?" If yes → URL. If it's server data → TanStack Query. If it's ephemeral → `useState`.

### Styling

| Legacy | Target |
|--------|--------|
| `styled-components` (`styled.div`, `styled(Tile)`) | SCSS Modules (`.module.scss`) |
| `css` helper from styled-components | Regular SCSS |
| Transient props (`$isActive`) | CSS class toggling or data attributes |
| `@carbon/elements` `styles.productiveHeading02` | `@carbon/type` SCSS mixins (`@include type.type-style('productive-heading-02')`) |
| Hardcoded `px` / `rem` values | Carbon spacing tokens (`var(--cds-spacing-05)`, `$spacing-05`) |
| Carbon token CSS vars (`--cds-border-subtle-01`) | Same — keep using Carbon CSS custom properties |

SCSS modules use dot notation for class access: `className={styles.container}`.

### Component structure

| Legacy | Target |
|--------|--------|
| `export default Component` or `export {Component}` inline | `export {Component}` block at end of file |
| `const Component: React.FC = () => ...` | `function Component() { ... }` or `const Component: React.FC = () => ...` (both OK, function declarations preferred) |
| `index.tsx` as main file in component dir | `PascalCase.tsx` named file (no barrel files) |
| Import via directory (`./Dashboard`) | Import via file (`#/shared/dashboard/Dashboard` or `#/operate/components/Dashboard`) |
| `React.memo()` wrapping | `useMemo` for derived data inside components; `React.memo` only when profiling shows a need |
| `observer()` from mobx-react-lite (for MobX) | Remove — no MobX observation needed for server state or URL state |

### Error handling

| Legacy | Target |
|--------|--------|
| Try/catch in components | `errorComponent` on the route handles render errors |
| Manual 401 checks | Centralized in `request()` — clears query cache, disables session |
| `notificationsStore.displayNotification()` | Build a notification module when needed, or use Carbon's `InlineNotification` inline |
| `<ErrorBoundary>` per route | `errorComponent` per route (TanStack Router's built-in mechanism) |

### Testing

This is the biggest divergence. Legacy tests use jsdom + Testing Library globals + `vi.mock()`. The target uses a real Chromium browser + MSW-only mocking + no global `screen`.

| Legacy | Target |
|--------|--------|
| `import {render, screen} from 'modules/testing-library'` | `import {it} from '#/vitest-modules/test-extend'` + `import {render} from 'vitest-browser-react'` or `renderWithRouter` |
| `screen.findByRole()` / `screen.getByRole()` | `screen.getByRole()` from render return value |
| `await screen.findByText('...')` | `await expect.element(screen.getByText('...')).toBeVisible()` |
| `waitFor(() => expect(...))` | `await expect.element(...)` — retries automatically |
| `screen.queryByText('...')` for absence | `await expect.element(screen.getByText('...')).not.toBeVisible()` |
| `vi.mock('../../stores/auth')` | MSW endpoint mocks — mock the HTTP, not the module |
| `nodeMockServer.use(http.post(...))` | `worker.use(mockEndpoint({successResponse: ...}))` |
| `mockFetchProcesses().withSuccess(data)` | `mockProcessesEndpoint({successResponse: HttpResponse.json(data)})` |
| `MemoryRouter` + `QueryClientProvider` wrapper | `renderWithRouter('/path')` — creates real TanStack Router + QueryClient |
| `getMockQueryClient()` | Built into `renderWithRouter` |
| `user.click()` from userEvent | `screen.getByRole('button').click()` — direct locator interaction |
| `// given / when / then` comments | No section comments — use blank lines for visual grouping |

Endpoint mocks live in `shared-test-modules/mock-handlers.ts`. If the mock you need doesn't exist, add it there using `createEndpointMock()` — never create mocks inline in test files.

### i18n

**Legacy (Operate):** No i18n — all strings are hardcoded in English. There are no `i18next` imports, translation files, or `useTranslation()` calls.

**Target:** The orchestration cluster webapp uses `i18next` + `react-i18next`. When migrating from Operate, wrap user-facing strings in `t('key')` via `useTranslation()` and add translation keys to the appropriate namespace in `src/shared/i18n/`. Check the target's namespace structure before adding keys.

## Handling partial migrations

Not every migration is a full page. You might be adding a single component, column, filter, or panel to a page that already exists in the orchestration webapp.

For partial work:
- Skip route creation — the route already exists
- Check if a module already exists for the concern — extend it rather than creating a new one
- Follow the same pattern transformations for the piece you're adding
- If the page component needs new data, add the query to `queries.ts` and the endpoint to `endpoints.ts`

## Common pitfalls

**Porting MobX stores verbatim.** This is the most common mistake. Legacy stores mix server cache, UI state, and derived data in one class. In the target, these concerns are separated. Decompose the store, don't port it.

**Scattering query definitions.** Legacy apps have `use*.query.ts` files next to each feature. The target centralizes all queries in `#/shared/http/queries.ts` and all endpoints in `#/shared/http/endpoints.ts`. This makes the full API surface visible in one place.

**Using `vi.mock()`.** Legacy tests mock MobX stores and modules with `vi.mock()`. The target mocks HTTP responses with MSW. If you need to control what a component sees, control it at the network level. The only acceptable use of `vi.mock()` is for things like `vi.useFakeTimers()` where there's no HTTP-level alternative.

**Creating inline MSW handlers in test files.** Don't write `http.post('*/v2/...', () => HttpResponse.json(...))` directly in tests. All endpoint mocks must be defined in `shared-test-modules/mock-handlers.ts` using `createEndpointMock()` and consumed via `worker.use(mockXxxEndpoint({successResponse: ...}))`. If the mock you need doesn't exist yet, add it to `shared-test-modules/mock-handlers.ts` — never inline it.

**Using `waitFor` or `findBy*`.** These don't exist in Vitest browser mode. Use `await expect.element(...)` which retries automatically.

**Creating barrel files.** The target doesn't use `index.ts` barrel files. Import files directly using `#/` path aliases.

**Putting fetch logic in page components.** Data fetching belongs in route loaders (`beforeLoad` or `loader`). Page components consume data via `useSuspenseQuery`, they don't initiate fetches.

**Using styled-components.** Even if the legacy code uses them, the target uses SCSS modules exclusively. Rewrite, don't port.

**Checking `isLoading` / `isError` on `useSuspenseQuery`.** Unlike `useQuery`, `useSuspenseQuery` never returns loading or error states — it suspends the component (handled by `<Suspense>` or the route's `pendingComponent`) and throws on errors (handled by `errorComponent`). If you need inline loading/error handling within a component, use `useQuery` instead, but prefer `useSuspenseQuery` with route-level boundaries as the default.

**Porting `useEffect` for navigation.** Legacy code uses `useEffect(() => navigate(...))` for conditional redirects. The target uses `throw redirect()` in `beforeLoad` — it's synchronous relative to the route lifecycle and avoids the flash of the wrong page.

## Canonical docs

- `docs/monorepo-docs/frontend/orchestration-cluster-webapp.md` — tech stack, layout, scripts
- `docs/monorepo-docs/frontend/data-loading.md` — TanStack Router + Query patterns
- `docs/monorepo-docs/frontend/forms.md` — form library guidance
- `docs/monorepo-docs/frontend/testing.md` — unit, integration, a11y, visual testing
- `docs/monorepo-docs/frontend/code-style.md` — naming, exports, comments
- `docs/monorepo-docs/frontend/development-process/creating-a-new-page.md` — page creation checklist
- `docs/monorepo-docs/frontend/development-process/before-starting.md` — pre-feature considerations
- `docs/monorepo-docs/frontend/legacy-components.md` — legacy app overview + migration epic link
- `references/pattern-mapping.md` — detailed side-by-side code examples for every transformation
