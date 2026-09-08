---
name: operate-frontend
description: Use for any Operate frontend work — the target pod at webapp/client/apps/orchestration-cluster-webapp/src/operate/ and the legacy client at operate/client/. Covers routing, data fetching, state, styling, testing, forms and conventions in both. For porting a page from legacy to target, use frontend-operate-migrator.
---

# Operate Frontend

Operate's frontend lives in two codebases while the migration runs:

- **Target** — `webapp/client/apps/orchestration-cluster-webapp/src/operate/` (~240 files). All new
  work goes here. React 19, TanStack Router + Query, Carbon, styled-components (temporary).
- **Legacy** — `operate/client/` (~1200 files). Bug fixes, small adjustments and maintenance only.
  React 18, React Router 7, TanStack Query 5, MobX 6, Carbon, styled-components, React Final Form.
  Winding down; don't add architecture.

Both render BPMN/DMN with `bpmn-js` / `dmn-js` and edit JSON with Monaco (`@monaco-editor/react`).
The target rules follow. For legacy work, read [references/legacy.md](references/legacy.md); for a
migration, read both and use [frontend-operate-migrator](../frontend-operate-migrator/SKILL.md).

For target unit tests, follow [frontend-unit-test](../frontend-unit-test/SKILL.md). For other target
concerns, read the relevant canonical guide under `docs/monorepo-docs/frontend/`. For app-wide
layout and boundaries, start with `docs/monorepo-docs/frontend/orchestration-cluster-webapp.md`.
The rules below record Operate-specific choices and overrides.

## Project structure

**Target:** routes in `src/routes/_carbon/_auth/operate/`, pages and their query/search contracts in
`src/operate/`, cross-pod primitives in `src/shared/`. Dependencies flow **routes → operate →
shared**: feature code must not import route files, shared code must not import feature code. Define
shared route/feature schemas in the feature. Never import across the legacy app boundary.

Pages are directories named after their primary export — `Dashboard/Dashboard.tsx`, not
`DashboardPage`. One file, one primary export, filename matches it. A colocated query module may
export both its `queryOptions` and its `use*` hook.

## Routing

**Target:** TanStack file-based routes under `src/routes/_carbon/_auth/operate/`. Route IDs include
`/_carbon/_auth`; browser URLs stay `/operate/...`. Do not introduce historical `/_auth/operate/...`
IDs. The guard, Dashboard and Processes list already exist — inspect before adding or splitting.
`beforeLoad` is for auth/guards only; `loader` prefetches data. Route files are thin: they wire a
page component and own `loader`, `pendingComponent`, `errorComponent`.

## URL as state (target)

Follow `docs/monorepo-docs/frontend/development-process/creating-a-new-page.md`: entity identity goes
in path params, shareable view state in validated search params, and ephemeral UI state locally.
Operate links must preserve validated tenant, definition/version, and incident identity filters.
Cover duplicate definition IDs across tenants and browser back/forward.

## Data fetching

**Target:** endpoint factories in `#/shared/http/endpoints.ts`. Operate-only query options live
**beside the owning feature** in `<feature>.queries.ts` or a local hook; only cross-app query options
belong in `#/shared/http/queries.ts`. Do not add Operate-specific polling, aggregation or
multi-page fetching to the shared registry.

| Concern | Where it goes |
|---------|--------------|
| Polling / cache policy | Feature-local query options, local hook, or call site |
| Multi-page fetching | Local hook exporting a `queryOptions` function (for route prefetch) + a `use*` hook (for the component) |
| Aggregation / transformation | `select` on `useSuspenseQuery`, or inside the local hook's `queryFn` |

Reference: `operate/pages/Dashboard/useRunningInstancesCount.ts` exports
`runningInstancesCountQuery()` and `useRunningInstancesCount()`; the route imports the query options,
the component imports the hook. Check `@camunda/camunda-api-zod-schemas/8.10` before writing a custom
endpoint — most Operate endpoints are already there.

Suspense queries throw initial errors without data, but a failed refetch can retain cached data —
handle those explicitly. Where a panel needs independent loading and recovery, use a granular
boundary or `useQuery`. See `docs/monorepo-docs/frontend/data-loading.md`.

## Writes (target)

Use direct `request()` handlers for simple writes, and XState machines for accepted/pending
lifecycles. This is an Operate rule; it does not change other pods' write patterns.

- **Simple write, then refresh:** call `request(endpoints.xxx(...))` in the handler, then
  `queryClient.invalidateQueries({queryKey: [...]})` for affected lists.
- **Write with a lifecycle** (API returns 202 and the resource passes through pending states): model
  it as an XState machine (`setup` + `fromPromise` actors) taking `queryClient` as input — optimistic
  update via `setQueryData` with rollback, poll via `fetchQuery` until the resource leaves the
  transitional state, then invalidate affected lists. Behavior reference:
  `tasklist/modules/task-details/taskCompletionMachine.ts` (the machine's shape, not Tasklist's route
  tree or design-system wrappers).

Operate's batch operations follow this lifecycle. Distinguish **starting** a new batch (returns a
key) from suspend/resume/cancel of an **existing** one (bodyless response — don't parse JSON from an
empty body). Never put write logic in `queries.ts`; it stays a read-only registry.

## State management

**Target:** URL search params own shareable state, `useState` owns ephemeral UI, and TanStack Query
owns server data. Reuse shared session, theme, and notification modules rather than porting stores.
Complex pending state may use a local reducer or MobX when simpler state is insufficient.

## Styling

**Target:** `styled-components` and Carbon are kept **temporarily** for the legacy-to-unified
migration. This is a compatibility step, not the target design system and not a frontend-wide
default — the Camunda design system replaces it later via
[design-system-migrator](../design-system-migrator/SKILL.md). Reuse existing Carbon components;
custom JSX is a last resort at this stage. Tasklist's design-system migration runs independently —
never apply these Carbon rules there.

## Component structure

**Both:** named exports only, never `export default`. No code comments — if something needs one,
rewrite the code. Prefer declarative and functional (`const`, `map`/`filter`/`reduce`); a local
`let`/`for` is fine for tight data aggregation where it reads clearer (see
`useRunningInstancesCount.ts`).

**Target:** one file, one primary export, filename matches it.

## i18n (target)

Operate strings go under `operate.*` inside the shared `translation` namespace in
`src/shared/i18n/locales/`, used as `t('operate.dashboard.title')`. Add all four locales (en/de/fr/es)
— LLM-translate de/fr/es and note "LLM-translated — native speaker review requested" in the PR
description.

## Testing

**Target:** follow [frontend-unit-test](../frontend-unit-test/SKILL.md). Put reusable response data in
`shared-test-modules/api-mocks/` factories rather than constructing large literals in tests.

## Forms

**Target:** follow `docs/monorepo-docs/frontend/forms.md`.

## Before building a target feature

Read `docs/monorepo-docs/frontend/development-process/before-starting.md`. Preserve each endpoint's
pagination contract and legacy UX: keep the existing paginated table for offset-based pages rather
than converting it to infinite scroll. Honor eventual-consistency metadata, keep authorization
server-driven, and cover multi-tenancy. Verify imports are declared dependencies and inspect
`tsconfig.browser.json` before adding global types.

## Feature flags (target)

Follow `docs/monorepo-docs/frontend/development-process/working-on-large-feature.md`.

## Commands

Run target checks from `webapp/client/`:

```bash
npm run lint
npm run typecheck -w @camunda/orchestration-cluster-webapp
npm run test:unit -w @camunda/orchestration-cluster-webapp
```

For tracked end-to-end work, the complete validation tiers live in
[operate-engineering-loop](../operate-engineering-loop/SKILL.md).

## Boundaries

**Target — don't introduce:** `/_auth/operate/...` route IDs, Operate-specific policy in
`#/shared/http/queries.ts`, per-consumer copies of shared logic, imports across the legacy app
boundary, or Mixpanel tracking (the app has none — when porting a callback that mixes tracking with
behavior, keep the behavior and drop the tracking).
