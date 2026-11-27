---
name: operate-frontend
description: Use for any Operate frontend work in the orchestration cluster webapp — code under webapp/client/apps/orchestration-cluster-webapp/src/operate/ and its routes. Covers scope, routing, data fetching, writes, state, styling, i18n and how Operate unit tests are written.
---

# Operate Frontend

Operate lives in `webapp/client/apps/orchestration-cluster-webapp/src/operate/`. React 19, TanStack
Router + Query, `bpmn-js` / `dmn-js` for diagrams, Monaco (`@monaco-editor/react`) for JSON.

This skill records Operate-specific choices and overrides. For everything else read the canonical
guides under `docs/monorepo-docs/frontend/`, starting with `orchestration-cluster-webapp.md` for
app-wide layout and boundaries.

## Scope

Change only Operate code: `src/operate/**`, the Operate routes, and Operate tests. `src/shared/**`
and the shared test modules affect every pod: when a task seems to need a change there, name the
file and ask before editing it.

Do what the task asks and nothing next to it. Report other problems you find; do not fix them.

## Project structure

Routes in `src/routes/_carbon/_auth/operate/`, pages and their query/search contracts in
`src/operate/`, cross-pod primitives in `src/shared/`. Dependencies flow **routes → operate →
shared**: feature code must not import route files, shared code must not import feature code. Define
shared route/feature schemas in the feature.

Pages are directories named after their primary export — `Dashboard/Dashboard.tsx`, not
`DashboardPage`.

## Routing

TanStack file-based routes. Route IDs include `/_carbon/_auth`; browser URLs stay `/operate/...`.
Do not introduce `/_auth/operate/...` IDs. Inspect the existing routes before adding or splitting
one. `beforeLoad` is for auth/guards only; `loader` prefetches data. Route files are thin: they wire
a page component and own `loader`, `pendingComponent`, `errorComponent`.

Use typed router links, not hard-coded path strings. Regenerate `routeTree.gen.ts`; never hand-merge
it.

## URL as state

Follow `docs/monorepo-docs/frontend/development-process/creating-a-new-page.md`: entity identity goes
in path params, shareable view state in validated search params, and ephemeral UI state locally.
Operate links must preserve validated tenant, definition/version, and incident identity filters.
Cover duplicate definition IDs across tenants and browser back/forward.

## Data fetching

Endpoint factories live in `#/shared/http/endpoints.ts`. Operate-only query options live **beside
the owning feature** in `<feature>.queries.ts` or a local hook; only cross-app query options belong
in `#/shared/http/queries.ts`. Do not add Operate-specific polling, aggregation or multi-page
fetching to the shared registry.

| Concern                      | Where it goes                                                                                           |
| ---------------------------- | ------------------------------------------------------------------------------------------------------- |
| Polling / cache policy       | Feature-local query options, local hook, or call site                                                   |
| Multi-page fetching          | Local hook exporting a `queryOptions` function (for route prefetch) + a `use*` hook (for the component) |
| Aggregation / transformation | `select` on `useSuspenseQuery`, or inside the local hook's `queryFn`                                    |

Reference: `operate/pages/Dashboard/useRunningInstancesCount.ts` exports
`runningInstancesCountQuery()` and `useRunningInstancesCount()`; the route imports the query options,
the component imports the hook. Check `@camunda/camunda-api-zod-schemas/8.10` before writing a custom
endpoint — most Operate endpoints are already there. To add or change a schema there, follow the
`camunda-api-zod-schemas` skill.

Suspense queries throw initial errors without data, but a failed refetch can retain cached data —
handle those explicitly. Where a panel needs independent loading and recovery, use a granular
boundary or `useQuery`. See `docs/monorepo-docs/frontend/data-loading.md`.

Preserve each endpoint's pagination contract: keep the paginated table for offset-based pages
rather than converting it to infinite scroll. Honor eventual-consistency metadata, keep
authorization server-driven, and cover multi-tenancy.

## Writes

Use direct `request()` handlers for simple writes, and XState machines for accepted/pending
lifecycles. This is an Operate rule; it does not change other pods' write patterns.

- **Simple write, then refresh:** call `request(endpoints.xxx(...))` in the handler, then
  `queryClient.invalidateQueries({queryKey: [...]})` for affected lists.
- **Write with a lifecycle** (API returns 202 and the resource passes through pending states): model
  it as an XState machine (`setup` + `fromPromise` actors) taking `queryClient` as input — optimistic
  update via `setQueryData` with rollback, poll via `fetchQuery` until the resource leaves the
  transitional state, then invalidate affected lists.

Operate's batch operations follow this lifecycle. Distinguish **starting** a new batch (returns a
key) from suspend/resume/cancel of an **existing** one (bodyless response — don't parse JSON from an
empty body). Never put write logic in `queries.ts`; it stays a read-only registry.

## State management

URL search params own shareable state, `useState` owns ephemeral UI, and TanStack Query owns server
data. Reuse the shared session, theme, and notification modules rather than adding stores. Complex
pending state may use a local reducer or MobX when simpler state is insufficient.

## Styling

Operate is moving from Carbon and `styled-components` to the Camunda design system through
[design-system-migrator](../design-system-migrator/SKILL.md). The design-system variant of a
component lives in a `shadcn.components/` folder next to the Carbon one, and is served from
`src/routes/_shadcn/_auth/operate-preview/`.

- In design-system components use design-system tokens and `Text`. In Carbon components keep Carbon
  primitives and tokens. Never use raw colors.
- Reuse an existing component before writing custom JSX.

## Component structure

Named exports only, never `export default`. One file, one primary export, filename matches it; a
colocated query module may export both its `queryOptions` and its `use*` hook. No code comments — if
something needs one, rewrite the code. Prefer `const` and `map`/`filter`/`reduce`; a local
`let`/`for` is fine for tight data aggregation where it reads clearer (see
`useRunningInstancesCount.ts`).

Make a prop required when the caller must pass it; don't add placeholder fallbacks. Give every
spinner and status an accessible name, and use one persistent live region instead of mounting a
`role="status"` per update.

## i18n

Operate strings go under `operate.*` inside the shared `translation` namespace in
`src/shared/i18n/locales/`, used as `t('operate.dashboard.title')`. Every key must exist in all four
locales (en/de/fr/es).

## Forms and feature flags

Follow `docs/monorepo-docs/frontend/forms.md` and
`docs/monorepo-docs/frontend/development-process/working-on-large-feature.md`. Before starting a
feature, read `docs/monorepo-docs/frontend/development-process/before-starting.md`.

## Unit tests

[frontend-unit-test](../frontend-unit-test/SKILL.md) explains the tooling: rendering, locators, the
`worker` fixture and the shared endpoint mocks. The rules below say what an Operate test may do.
Where they are stricter than that skill, they win.

**Test what the user sees and does.** A test that follows the user survives a refactor; a test that
follows the code breaks on one and can pass while the screen is wrong.

- Find elements by role, label or text. No new test ids. When a native control has no accessible
  name, give it one that reads correctly for a screen-reader user. Never put `aria-label` on a
  generic element such as a `div`: that is invalid ARIA. Leave existing test ids alone unless the
  task is about them; a test id is the last resort for an element with no role and no accessible
  name.
- Act with `userEvent`. Reach a page state by clicking the link, row or tab, not by calling the
  router.
- Assert rendered output, and the URL after navigation. Do not assert on a store, the query client,
  a request count, a class name or layout geometry.

**Do not mock our own code.** No `vi.mock`, no spies on Operate or shared modules.

- HTTP is the only thing a test replaces, through the shared endpoint mocks passed to
  `worker.use()`. For a different response later in the test, call `worker.use()` again right before
  the action that needs it.
- A browser API the test cannot reach, such as `navigator.clipboard`, may be spied on.
- `vi.fn()` is for a callback prop of the component under test.
- To check what a mutation sends, give the endpoint mock a `schema` with the exact expected values
  (`.extend()`, `z.literal()`), a `successResponse` and a `failureResponse`. A wrong payload then
  gets the failure response and the assertion on the screen fails. Do not record or count requests.

**Make the test deterministic.** A test must pass every time or fail every time, without retries.

- Never wait on real time: no sleep, `waitFor`, `vi.waitFor` or raised timeout. For a pending state
  use `delay: 'infinite'`: with fake timers on, `expect.element` and waiting actions advance the
  clock themselves, so a finite `delay` is never held pending. Fake timers advanced by the test are
  for a real debounce, backoff or poll the test must fast-forward.
- Assert on the DOM only through `expect.element` on a locator, which retries
  (`await expect.element(locator).toHaveLength(n)` counts). Do not assert on a resolved node
  (`.element()`, `.elements()` in a plain `expect(...)`, `querySelector`, `closest`) and do not call
  DOM methods on one (`.click()`, `.focus()`). To hand a node to an external API use
  `await locator.findElement()`.
- `expect.element` and `userEvent` wait up to the test deadline, `expect.poll` and `vi.waitFor` only
  1000 ms. This holds only while the browser provider sets no `actionTimeout`.
- Before asserting that something is absent, assert something visible that proves the UI rendered.
  A component that is expected to render nothing has no such anchor; that test stands as it is.
- Mock every request the test triggers. An unhandled request is a failure.
- A fix removes the cause of a failure. Never add a retry or a `try/catch`.

**Keep tests small and honest.**

- No logic in a test: no `if`, ternary or `??` that picks an action or an expected value. Split the
  test, or put the expected values in the `it.for` case table.
- After writing or changing an assertion, break the behaviour once by hand and see the test fail.
- A test that repeats the coverage of another is deleted, not rewritten.
- Change a test file only where the task requires it.

## Boundaries

Don't introduce: `/_auth/operate/...` route IDs, Operate-specific policy in
`#/shared/http/queries.ts`, per-consumer copies of shared logic, or Mixpanel tracking (the app has
none).
