# Legacy Operate Frontend

Apply this reference only under `operate/client/`. The app is winding down: make bug fixes and small
maintenance changes without introducing new architecture.

## Structure and routing

```
src/
  App/                    # Pages and route tree
  modules/
    api/v2/               # One typed function per endpoint
    queries/              # React Query hooks + queryKeys.ts
    mutations/            # React Query mutations
    stores/               # MobX UI state
    mocks/                # Factories + fluent request builders
    Routes.tsx            # Paths / Locations builders
    testing-library.ts    # Custom render() with userEvent
```

Components use `index.tsx`, `styled.ts`, and `index.test.tsx`; some tests live in `tests/`.

Routes use React Router v6 `createBrowserRouter` in `src/App/index.tsx` and are lazy-loaded. Use
`Paths` and `Locations` from `modules/Routes.tsx`; never hardcode paths. Use
`useProcessInstancePageParams` on process-instance detail pages and `useParams` elsewhere. Filters
live in URL search params through `useFilters`, not MobX. Authenticated routes inherit the dashboard
guards, and the root `PageErrorBoundary` handles page errors; do not duplicate either.

## Data, writes, and state

Keep the three data layers:

1. `modules/api/v2/` exposes thin typed endpoint functions built on `requestWithThrow`.
2. `modules/queries/` and `modules/mutations/` own React Query behavior and centralized keys.
3. Components call those hooks directly; there is no route prefetching.

Despite its name, `requestWithThrow` returns the `{response, error}` discriminated union and handles
401s by disabling the session. Do not use the older `requestAndParse` for new code. Live data
normally polls every 5000ms while an instance is running. For eventually consistent writes, poll
with `queryClient.fetchQuery`, `retry: true`, and a `queryFn` that throws until the expected state.

Use React Query for server data, `useFilters` for shareable filters/sort/pagination/selection, and
MobX for ephemeral UI mode. Important stores include `authentication`, `modifications`,
`notifications`, `instancesSelection`, `processInstanceMigration`, `panelStates`,
`batchModification`, and `currentTheme`. Wrap components that read a store in `observer()` around
the function, not the export; do not wrap components that read no store.

## Components and styling

Use named exports. Most components use `const C: React.FC<Props> = ...`. Do not write code comments;
rewrite unclear code instead. Styling belongs in `styled.ts`; do not add SCSS modules or inline
styles.

- Wrap Carbon components with `styled(CarbonComponent)`.
- Preserve Carbon defaults. Before overriding one, explain the risk and Carbon-native alternative
  and wait for confirmation.
- Use `@carbon/elements` `styles` for typography and Carbon CSS variables for spacing and color.
- Prefix transient styled-component props with `$` and type them with generics.

Legacy has no i18n; keep strings in English and do not add translation files.

## Testing

Tests use Vitest with jsdom, Testing Library, and MSW 2. Import `render` from
`modules/testing-library`, which returns a configured `{user}`. For context, copy an existing wrapper
combining `QueryClientProvider` with `getMockQueryClient()` and `MemoryRouter`.

Use fluent endpoint builders from `modules/mocks/api/` and data factories from `modules/mocks/`.
Every builder except `withNetworkError` registers a one-shot handler, so register repeated requests
more than once. MobX stores reset in `afterEach` through `resetAllStores()`.

## Forms and commands

Use React Final Form. Filter forms synchronize through `useFilters`; modal/editing forms use
`<Form>` and `<Field>`, with `FieldArray` for variable arrays.

Run from `operate/client/`:

```bash
npm start
npm test
npm run ts-check
npm run lint
npm run build
npm run knip
```

Do not introduce SCSS/CSS modules, i18n, TanStack Router, route loaders, prefetching, or new UI
libraries.
