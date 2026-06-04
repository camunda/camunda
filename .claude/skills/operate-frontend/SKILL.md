---

name: operate-frontend
description: Use when fixing bugs, making changes, writing tests, or understanding code in the Operate legacy frontend at operate/client/. Trigger for any work touching operate/client/src/, including component changes, test modifications, API hook updates, styled-components edits, MobX store changes, or React Router route adjustments. Also use when someone asks about Operate frontend patterns, conventions, or architecture. This is the legacy process monitoring UI being phased out in favor of the orchestration cluster webapp.

---

# Operate Legacy Frontend

Operate is the process monitoring frontend at `operate/client/`. It is **legacy code being phased out** — the orchestration cluster webapp at `webapp/client/` is replacing it. Work in Operate should be limited to bug fixes, small adjustments, and maintenance. For substantial new features, build them in the new app instead (see the `frontend-feature` and `frontend-migrator` skills).

Follow the existing conventions described here. Don't introduce new architectural patterns — consistency matters more than modernization in a codebase that's winding down.

## Tech stack

|   Category   |                                   Technology                                    |
|--------------|---------------------------------------------------------------------------------|
| UI framework | React 18, React DOM 18                                                          |
| Language     | TypeScript 5.9 (strict mode)                                                    |
| Bundler      | Vite 8                                                                          |
| Routing      | react-router-dom 7 (React Router v6 API)                                        |
| Server state | TanStack React Query 5                                                          |
| Client state | MobX 6 + mobx-react / mobx-react-lite                                           |
| Styling      | styled-components 6, Carbon Design System (`@carbon/react`, `@carbon/elements`) |
| Forms        | React Final Form + final-form-arrays                                            |
| Testing      | Vitest (jsdom) + Testing Library + MSW 2, Playwright (E2E)                      |
| BPMN/DMN     | bpmn-js 18, dmn-js 17                                                           |
| Code editor  | Monaco Editor (`@monaco-editor/react`)                                          |

## Project structure

```
operate/client/src/
  index.tsx                          # Entry point, renders <App />
  App/                               # App shell + page components
    index.tsx                        # Router, providers, route tree
    Layout/                          # App shell (header, sidebar, content)
    Dashboard/                       # Dashboard page
    Processes/                       # Process instances list
    ProcessInstance/                  # Process instance detail (BPMN + tabs)
    Decisions/                       # Decision instances list
    DecisionInstance/                 # Decision instance detail
    BatchOperations/                 # Batch operations list + detail
    OperationsLog/                   # Operations log
    Login/                           # Login page
    RedirectDeprecatedRoutes.tsx     # Migrates old /instances URLs
  modules/                           # Shared concerns
    api/v2/                          # API endpoint functions (one per endpoint)
    queries/                         # React Query hooks (useQuery, useInfiniteQuery)
    mutations/                       # React Query mutations (useMutation)
    react-query/                     # QueryClient provider + config
    stores/                          # MobX stores (UI state)
    hooks/                           # Custom React hooks
    components/                      # Shared UI components
    mock-server/                     # MSW setup (node for tests)
    mocks/                           # Mock data + mock request builders
    request/                         # HTTP request utilities
    Routes.tsx                       # Centralized path builders (Paths, Locations)
    testing-library.ts               # Custom render() with userEvent
    types/                           # Shared TypeScript types
    utils/                           # General utilities
```

Components follow a consistent directory layout: `index.tsx` (component), `styled.ts` (styled-components), `index.test.tsx` (tests). Some components place tests in a `tests/` subdirectory instead.

## Routing

Routes are defined in `src/App/index.tsx` using React Router v6's `createBrowserRouter` with `createRoutesFromElements`. Every page-level route is lazy-loaded:

```tsx
<Route
  path={Paths.processes()}
  lazy={async () => {
    const {Processes} = await import('./Processes/index');
    return {Component: Processes};
  }}
/>
```

### Path builders

All route paths are centralized in `modules/Routes.tsx` via the `Paths` object. Never hardcode path strings — always use `Paths`:

```tsx
import {Paths} from 'modules/Routes';

Paths.processes()
Paths.processInstance('123')
Paths.processInstance()
Paths.decisionInstance('456')
Paths.batchOperation('789')
```

The `Locations` object builds `{pathname, search}` objects with default filter params:

```tsx
import {Locations} from 'modules/Routes';

Locations.processes()
Locations.decisions()
```

### Route params and search params

Route params use `useParams` with a type argument:

```tsx
const {processInstanceId} = useParams<{processInstanceId: string}>();
```

The `useProcessInstancePageParams` hook in `App/ProcessInstance/` wraps this for the process instance detail pages — use it instead of calling `useParams` directly in that context.

Search params drive filter state. The `useFilters` hook in `modules/hooks/useFilters.tsx` provides `getFilters()` and `setFilters()` that read/write URL search params via `useNavigate` and `useLocation`. Filters are fully URL-driven — no MobX store for filter state.

### Authentication guards

All authenticated routes are wrapped in `AuthenticationCheck` + `AuthorizationCheck`, composed in the dashboard route's `lazy` loader. New authenticated routes must nest under the dashboard route — don't duplicate the guards.

### Error boundaries

A single `PageErrorBoundary` on the root route catches all errors via `useRouteError()`. Don't add per-page error boundaries.

## Data fetching

Data fetching has three layers. Follow this architecture — don't bypass it.

### Layer 1: API functions (`modules/api/v2/`)

Each endpoint gets a thin typed function. Endpoints come from `@camunda/camunda-api-zod-schemas/8.10`:

```tsx
import {endpoints, type QueryProcessInstancesRequestBody, type QueryProcessInstancesResponseBody}
  from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';
```

`requestWithThrow` returns `{response, error}` — a discriminated union. `response` is parsed data on success (else `null`), `error` is `RequestError` on failure (else `null`). 401s are handled automatically. Don't use `requestAndParse` (legacy) for new code.

### Layer 2: React Query hooks (`modules/queries/`)

Query hooks wrap the API functions. Query keys are centralized in `modules/queries/queryKeys.ts`:

```tsx
const useProcessInstance = () => {
  const {processInstanceId} = useProcessInstancePageParams();
  return useQuery({
```

The standard pattern: return `response` on success, `throw error` on failure.

### Layer 3: Components

Components call the query hooks directly. There is no route-level data prefetching — components initiate their own fetches:

```tsx
function ProcessInstanceHeader() {
  const {data: processInstance, isLoading} = useProcessInstance();
  if (isLoading) return <SkeletonText />;
}
```

### Mutations

Mutations follow the same `{response, error}` pattern. Some mutations poll for eventual consistency using `queryClient.fetchQuery` with `retry: true`:

```tsx
await queryClient.fetchQuery({
  queryKey: queryKeys.processInstance.get(key),
  queryFn: async () => {
    const {response} = await fetchProcessInstance(key);
    if (response.state === 'ACTIVE') throw new Error('Still running');
    return response;
  },
  retry: true,
  retryDelay: 1000,
});
```

### Polling

Live data uses `refetchInterval` on query hooks (standard interval is 5000ms). Conditional polling is common — only poll when the instance is running or active.

## State management

State is split across three mechanisms. When you encounter state, identify which category it belongs to:

|                              What                               |                         Where                         |
|-----------------------------------------------------------------|-------------------------------------------------------|
| Server data (API responses)                                     | React Query via `modules/queries/`                    |
| Filters, sort, pagination, element selection                    | URL search params via `useFilters`, `useSearchParams` |
| UI mode (modification mode, panel visibility, selection, theme) | MobX stores in `modules/stores/`                      |

### MobX stores

There are ~20 MobX stores. The important ones:

|           Store            |                               What it manages                               |
|----------------------------|-----------------------------------------------------------------------------|
| `authentication`           | Session state, login/logout flow                                            |
| `modifications`            | Process instance modification mode (add/cancel/move tokens, variable edits) |
| `notifications`            | Toast notification queue (max 5 visible)                                    |
| `instancesSelection`       | Selected process instances for batch operations                             |
| `processInstanceMigration` | Migration wizard state                                                      |
| `panelStates`              | UI panel open/closed state                                                  |
| `batchModification`        | Batch modification mode                                                     |
| `currentTheme`             | Light/dark theme preference                                                 |

Components that read MobX stores must be wrapped with `observer()`:

```tsx
import {observer} from 'mobx-react';
import {panelStatesStore} from 'modules/stores/panelStates';

const MyComponent = observer(() => {
  const isOpen = panelStatesStore.isFiltersOpen;
});

export {MyComponent};
```

Don't wrap components that don't access stores — `observer()` adds overhead.

## Styling

All component styling uses styled-components. There are no SCSS modules in this codebase — don't introduce them. Don't use inline `style={{}}` props either — all styling belongs in a `styled.ts` file.

### File convention

Each component directory has a `styled.ts` file exporting styled components:

```tsx
import styled, {css} from 'styled-components';
import {Tile as BaseTile} from '@carbon/react';
import {styles} from '@carbon/elements';
```

### Key patterns

- **Wrapping Carbon components**: `styled(CarbonComponent)` applies additional styles on top of Carbon's defaults.
- **Respect Carbon's defaults**: Before overriding a Carbon component's visual defaults (colors, spacing, typography), explain the override to the user, suggest the Carbon-native alternative if one exists (prop, token, different component), and get confirmation before proceeding.
- **Carbon tokens**: typography via `@carbon/elements` `styles` object (`${styles.productiveHeading02}`), spacing/color via CSS custom properties (`var(--cds-spacing-05)`, `var(--cds-text-primary)`).
- **Transient props**: use the `$` prefix (`$isActive`, `$size`). Type them with generics: `styled.div<{$isActive: boolean}>`.
- **`css` helper**: use for conditional style blocks inside template literals.

## Component structure

- **File naming**: `index.tsx` for the component, `styled.ts` for styles, `index.test.tsx` for tests.
- **Exports**: always use named exports at the end of the file: `export {MyComponent}`. Never `export default`.
- **`observer()` wrapping**: wrap the component function, not the export: `const Comp = observer(() => {...}); export {Comp};`
- **`React.FC` typing**: most components use `const Component: React.FC<Props> = ({...}) => {...}`.
- **No comments**: don't generate code comments. The code should be self-explanatory. If something needs a comment, the code itself should be rewritten to be clearer instead.

## Testing

Tests use Vitest with jsdom, `@testing-library/react`, and MSW v2 for API mocking.

### Custom render

Import `render` from `modules/testing-library`, not from `@testing-library/react` directly. It bundles a pre-configured `userEvent` instance:

```tsx
import {render, screen, waitFor} from 'modules/testing-library';

const {user} = render(<MyComponent />, {wrapper: getWrapper()});
await user.click(screen.getByRole('button', {name: /submit/i}));
```

### Test wrapper

Tests that render components needing context use a wrapper function composing `QueryClientProvider` + `MemoryRouter`:

```tsx
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';

const getWrapper = (initialPath = Paths.processes()) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};
```

`getMockQueryClient()` creates a `QueryClient` with `retry: false`, `gcTime: Infinity`, `staleTime: Infinity`.

### API mocking

Each endpoint has a typed mock builder in `modules/mocks/api/`. The fluent builder pattern:

```tsx
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

mockFetchProcessInstance().withSuccess(processInstanceData);

mockFetchProcessInstance().withServerError(404);

mockFetchProcessInstance().withDelay(processInstanceData);

mockFetchProcessInstance().withNetworkError();
```

All handlers except `withNetworkError` are `{once: true}` — consumed on first match. Chain multiple `.withSuccess()` calls for sequential requests. MSW resets all handlers in `afterEach`.

To create a new mock, use `mockGetRequest`, `mockPostRequest`, etc. from `modules/mocks/api/mockRequest.ts`:

```tsx
import {mockPostRequest} from 'modules/mocks/api/mockRequest';
import type {MyResponseType} from '@camunda/camunda-api-zod-schemas/8.10';

const mockSearchMyEntity = (contextPath = '') =>
  mockPostRequest<MyResponseType>(`${contextPath}/v2/my-entities/search`);
```

### Test data factories

Use factory functions from `modules/mocks/` to create typed test data:

```tsx
import {createUser} from 'modules/mocks/user';
import {createProcessDefinition} from 'modules/mocks/processDefinition';

mockMe().withSuccess(createUser());
mockSearchProcessDefinitions().withSuccess(searchResult([createProcessDefinition({name: 'Test'})]));
```

### Assertion patterns

```tsx
expect(screen.getByRole('button', {name: /cancel/i})).toBeInTheDocument();

expect(await screen.findByText('10 running instances')).toBeInTheDocument();

expect(screen.queryByText('Error')).not.toBeInTheDocument();

await waitFor(() => {
  expect(screen.getByRole('cell', {name: 'completed'})).toBeInTheDocument();
});

const row = screen.getByRole('row', {name: /my-process/i});
expect(within(row).getByText('v2')).toBeInTheDocument();
```

### Store cleanup

All MobX stores are reset automatically in `afterEach` via `resetAllStores()` in `setupTests.tsx`. You don't need to reset stores manually in tests unless you need a mid-test reset.

## Forms

Forms use React Final Form. Two main patterns:

1. **Filter forms**: use `<Form>` with field components that sync to URL search params via `useFilters`.

2. **Modal/editing forms**: standard `<Form onSubmit={...}>` with `<Field>` components and explicit submit buttons. Variable editing uses `FieldArray` from `final-form-arrays`.

## Commands

Run from `operate/client/`:

```bash
npm start              # Dev server on :3000 (proxies API to :8080)
npm test               # Unit tests (Vitest, jsdom)
npm run lint           # TypeScript check + ESLint + Prettier
npm run ts-check       # TypeScript only (tsc -b)
npm run build          # Production build
npm run knip           # Dead code/dependency analysis
```

## Common workflows

### Fixing a bug

1. Reproduce with a failing test in the component's `index.test.tsx`
2. Fix the component in `index.tsx`
3. Verify: `npm test -- --reporter=verbose <test-file>` — if tests fail, check mock handler consumption (chain `.withSuccess()` for repeated requests)
4. Run `npm run lint` — if it fails, fix reported issues and re-run before proceeding
5. Check for regressions in related components

### Writing a test for an existing component

1. Set up mocks for API calls the component makes (check which query hooks it uses)
2. Render with `getWrapper()` — match the route path the component expects
3. Chain `.withSuccess()` calls if the component fetches the same endpoint multiple times
4. Assert on user-visible text and ARIA roles, not implementation details
5. Run: `npm test -- --reporter=verbose <test-file>` — if assertions fail on async content, use `findBy*` queries or `waitFor`

### Adding a new API endpoint

1. Create the API function in `modules/api/v2/` using `requestWithThrow`
2. Add a query key to `modules/queries/queryKeys.ts`
3. Create the query hook in `modules/queries/` wrapping the API function
4. Create mock builders in `modules/mocks/api/v2/` using `mockPostRequest` or `mockGetRequest`
5. Use the query hook in the component — verify with `npm test`

## Common pitfalls

- **Mock handler consumption**: `.withSuccess()` handlers are `{once: true}` — if a component makes the same request twice, the second call gets no handler. Chain two `.withSuccess()` calls or use a non-one-shot approach.
- **Missing `observer()`**: if a component reads a MobX store but isn't wrapped in `observer()`, it won't re-render when the store changes. Symptoms: stale UI, tests that pass individually but fail in sequence.
- **`requestAndParse` vs `requestWithThrow`**: `requestAndParse` is legacy. Use `requestWithThrow` for all new code. They have different return shapes — don't mix them up.
- **No i18n**: all strings are hardcoded in English. Don't add `i18next` or translation files.
- **No SCSS modules**: styling is entirely styled-components. Don't introduce `.module.scss` files.
- **No data prefetching**: components fetch their own data via query hooks. There are no route-level loaders. Don't add them — that's the new app's pattern.

## Boundaries

**Follow existing conventions:**
- styled-components for styling
- React Final Form for forms
- `mockFetchX().withSuccess()` pattern for test mocking
- Named exports, `index.tsx` entry files
- `Paths` object for all route paths

**Don't introduce:**
- SCSS modules or CSS modules
- i18n / translation files
- TanStack Router or file-based routing
- Route-level data loaders / prefetching
- New UI component libraries

**For substantial new features:** use the `frontend-migrator` skill to build them in the orchestration cluster webapp instead. Operate is winding down — invest engineering effort in the replacement.

## Key references

| What | Where |
|------|-------|
| Route definitions | `src/App/index.tsx` |
| Path/Location builders | `src/modules/Routes.tsx` |
| Custom test render | `src/modules/testing-library.ts` |
| Mock query client | `src/modules/react-query/mockQueryClient.ts` |
| Mock request builders | `src/modules/mocks/api/mockRequest.ts` |
| Query key registry | `src/modules/queries/queryKeys.ts` |
| HTTP request utils | `src/modules/request/` |
| Test setup (store reset, MSW) | `src/setupTests.tsx` |
