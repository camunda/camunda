---
name: frontend-operate-migrator
description: Operate-specific context for migrating operate/client/ pages into webapp/client/apps/orchestration-cluster-webapp/. Always read frontend-migrator first — this skill adds Operate-specific overrides, the migration loop protocol, and page-by-page context.
---

# Operate Migration — Pod-Specific Context

> **Read `frontend-migrator` first.** This skill only documents what is different or specific to Operate.

## Styling exception

**Keep `styled-components`.** The `frontend-migrator` skill says SCSS modules — ignore that for Operate. Port styled-components as-is and defer the ShadCN migration until after all pages are unified. The ShadCN migration will happen cross-pod in one coordinated sweep.

## Code conventions

**TypeScript:**
- Prefer declarative and functional — `const`, `map`/`filter`/`reduce` over mutable patterns. Local `let`/`for` is fine for tight data aggregation where it reads clearer (see `useRunningInstancesCount.ts`).
- One file, one primary export; the file name matches it (`ProcessesPage.tsx` exports `ProcessesPage`). Exception: a colocated query module may export both its `queryOptions` and its `use*` hook (see the shared HTTP layer reference below).

**Components:**
- Carbon Design System first, custom JSX last resort.

**Tests:**
- Mock only external dependencies (network via MSW, timers). Never mock internal modules or components.

## Pages to migrate

Page list, status, and per-PR breakdown live in GitHub: epic [#51305](https://github.com/camunda/camunda/issues/51305) → one page subissue each → inner subissues per PR. Source path, target route, and fidelity scope live in each page issue body. Query live (see "State lives in GitHub" below); never cache here.

Operate route files live under `webapp/client/apps/orchestration-cluster-webapp/src/routes/_auth/operate/`. The route guard (`route.tsx`) and empty shell (`index.tsx`) already exist — do not recreate them.

## MobX store decomposition

Operate has ~20 stores. Most are transient UI state and do not need porting. Map each one:

| Store | What it holds | Target |
|-------|--------------|--------|
| `authentication.ts` | Session | Already in `#/shared/auth/` — reuse |
| `currentTheme.ts` | Theme preference | Already in `#/shared/theme/` — reuse |
| `variableFilter.ts` | Filter inputs on Processes page | URL search params via `validateSearch` on the route |
| `instancesSelection.ts` | Selected rows | `useState` inside the page component |
| `panelStates.ts` | Which panel is open/collapsed | `useState` |
| `dateRangePopover.ts` | Calendar open/close | `useState` |
| `executionCountToggle.ts` | Toggle state | `useState` |
| `incidentsPanelFiltersStore.ts` | Filter inputs on Incidents tab | URL search params |
| `modifications.ts` | Pending variable modifications (complex) | `useState` + local reducer — or keep as MobX if truly complex |
| `batchModification.ts` | Batch operation in-progress | `useState` |
| `processInstanceMigration.ts` | Migration wizard state | `useState` + URL params for step |
| `diagramOverlays.ts` | Diagram overlay data | `useState` inside BPMN component |
| `networkReconnectionHandler.ts` | Connectivity polling | Port to a standalone hook with `useEffect` |
| `notifications.tsx` | Toast queue | Already handled by the toast notifications PR (wait for merge) |

**Decision rule:** Ask "Would the user want to share/bookmark this state?" → URL search params. "Is it ephemeral per-visit?" → `useState`. "Is it server data?" → TanStack Query.

## Shared HTTP layer

Endpoints go in `#/shared/http/endpoints.ts`. Queries go in `#/shared/http/queries.ts`.

**`queries.ts` is a thin registry — `queryKey` + `queryFn` for a single HTTP request, nothing else.**
Never add `refetchInterval`, `staleTime`, `gcTime`, aggregation logic, or multi-page fetch logic here.
These belong in the component or a component-local hook.

| Concern | Where it goes |
|---------|--------------|
| Polling (`refetchInterval`) | `useSuspenseQuery({...query(), refetchInterval: N})` at the call site, or in a local hook |
| Multi-page fetching | Local hook — export a `queryOptions` function for route prefetching + a `useSomething()` hook for the component |
| Aggregation / data transformation | `select` option on `useSuspenseQuery`, or inside the local hook's `queryFn` |

**Reference implementation:** `operate/pages/Dashboard/useRunningInstancesCount.ts` — exports both `runningInstancesCountQuery()` (used in `beforeLoad` for prefetching) and `useRunningInstancesCount()` (used in the component). The route imports the query function; the component imports the hook. `queries.ts` stays thin.

Pattern (copy from existing entries in those files):

```ts
// endpoints.ts
import {endpoints as api} from '@camunda/camunda-api-zod-schemas/8.10';

const endpoints = {
  // existing entries...
  searchProcessInstances: (body: SearchProcessInstancesRequest) =>
    new Request(getFullURL(api.searchProcessInstances.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: api.searchProcessInstances.method,
      body: JSON.stringify(body),
      headers: {'Content-Type': 'application/json'},
    }),
};
```

```ts
// queries.ts
const queries = {
  // existing entries...
  searchProcessInstances: (params: SearchProcessInstancesRequest) =>
    queryOptions({
      queryKey: ['searchProcessInstances', params] as const,
      queryFn: async () => {
        const {response, error} = await request(endpoints.searchProcessInstances(params));
        if (error !== null) throw error;
        return response.json() as Promise<SearchProcessInstancesResponse>;
      },
    }),
};
```

**Check `@camunda/camunda-api-zod-schemas/8.10` first** before writing a custom endpoint — many Operate endpoints are already there. Import `endpoints` from the package to get the URL and method.

## i18n

Operate strings go under `operate.*` inside the shared `translation` namespace:

```json
// shared/i18n/locales/en.json — inside "translation": { … }
"operate": {
  "dashboard": { "title": "Dashboard" },
  "processes": { "title": "Processes" },
  "decisions": { "title": "Decisions" },
  "operationsLog": { "title": "Operations Log" },
  "batchOperations": { "title": "Batch Operations" }
}
```

Usage: `const {t} = useTranslation(); t('operate.dashboard.title')`

Add all 4 locales (en/de/fr/es) — LLM-translate de/fr/es and note "LLM-translated — native speaker review requested" in the PR description.

## Test pattern

```ts
// SomePage.test.tsx
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockSomeEndpoint} from 'shared-test-modules/mock-handlers';
import {HttpResponse} from 'msw';

// worker is injected and auto-managed by the `it` fixture — no beforeAll/afterAll needed

it('should display process instances', async ({worker}) => {
  worker.use(
    mockSomeEndpoint({successResponse: HttpResponse.json({items: [], totalCount: 0})}),
  );

  const screen = await renderWithRouter(SomePage, {path: '/operate'});

  await expect.element(screen.getByText('Dashboard')).toBeVisible();
});
```

**Add new endpoint mocks to `shared-test-modules/mock-handlers.ts`** using `createEndpointMock()`. Never inline `http.post(...)` directly in test files.

## Definition of Done (9 gates)

Run from `webapp/client` unless noted. Gates 1–6 are local; 7–9 gate the PR (9 is CI-authoritative — verify locally, never push regenerated snapshots).

1. **Prettier** — `npx prettier --check "apps/orchestration-cluster-webapp/src/**/*.{ts,tsx}" "apps/orchestration-cluster-webapp/test/**/*.ts"`
2. **ESLint** — `npm run lint`
3. **Typecheck** — `cd apps/orchestration-cluster-webapp && npm run typecheck`
4. **Unit** — `cd apps/orchestration-cluster-webapp && npm run test:unit`
5. **Build** — `npm run build -w @camunda/orchestration-cluster-webapp`
6. **Knip** — `cd apps/orchestration-cluster-webapp && npm run lint:knip`
7. **Integration** — `cd apps/orchestration-cluster-webapp && npx playwright test --project=integration test/integration/`
8. **a11y** — `cd apps/orchestration-cluster-webapp && npx playwright test --project=a11y-light --project=a11y-dark`
9. **Visual** — CI is authoritative; never regenerate snapshots locally.

## Migration loop

Iterate against feedback signals in three tiers, by cost. Loop on the cheapest tier that can fail; graduate only when green.

| Tier | Gates | Loop on it when | Max iterations |
|------|-------|-----------------|----------------|
| **edit** | 1 Prettier · 2 ESLint · 3 Typecheck | after every meaningful edit (seconds) | 5 |
| **component** | + 4 Unit · 5 Build · 6 Knip | a component is done (minutes) | 5 |
| **PR** | 7 Integration · 8 a11y · 9 Visual (CI) | before marking ready — drive with `ci-fix-failure` | 3 |

**Stop condition (guardrail).** Each tier loop is bounded. If a tier is not green within its max iterations, **stop and report** — do not keep iterating. A loop with no bound spins forever and burns budget on a problem it cannot converge on; the cap forces escalation to the engineer instead. An iteration that makes zero progress (same failure, same fix attempted) counts double — bail early.

Full loop, start to close:

```
read this skill + the page issue (gh, live)        load spec + state
→ own the inner subissue for this PR               (set in progress)
→ port the component (legacy = exact spec, 1:1)
→ [edit tier]       loop until green
→ [fidelity]        deterministic checks + LLM flagger (below)
→ [component tier]  loop until green
→ open DRAFT PR     title feat:/fix:/refactor: …; body "Closes #<inner-subissue>"; request Copilot review
→ push → [PR tier]  ci-fix-failure loop until CI green
→ close the loop    check off the inner subissue, update the page issue
```

### State lives in GitHub, checked live

Epic [#51305](https://github.com/camunda/camunda/issues/51305) → page subissue (sibling naming `Migrate Operate <Page> page to unified webapp`) → inner subissue per PR (conventional-commit naming, one PR each). Never cache issue/PR state in a file; query it:

- `gh issue view <n> --repo camunda/camunda --json title,body,state`
- `gh api repos/camunda/camunda/issues/<n>/sub_issues`
- `gh pr list --repo camunda/camunda --search "<page>"`

### Finishing step — draft PR + Copilot review

Open the PR as a **draft**, body `Closes #<inner-subissue>`, then request Copilot review. Keep it draft until all 9 gates are green and Copilot threads resolved; only then mark ready.

- `gh pr create --draft --title "<conventional-commit>" --body "Closes #<n>"`
- Request Copilot review (mechanism: your `copilot-review` memory).

Every new failure mode becomes a rule, not a one-off fix: encode it in this skill or your Claude memory so it cannot recur.

## Fidelity checks (the 1:1 oracle)

Run **after the edit tier, scoped to the just-ported component** — not across the whole `operate/` dir, or you flag not-yet-ported features.

**Deterministic (script, always trusted):** run from the **repo root** (the script and its default locales path are repo-root relative — not `webapp/client`):

```bash
node .claude/skills/frontend-operate-migrator/scripts/fidelity.mjs \
  --ported <ported-component-dir> --legacy <legacy-component-dir>
```

Checks locale coverage (every `t('operate.*')` key exists in en/de/fr/es) and tracking carry-over (every legacy `eventName` has an `operate:<name>` counterpart). Non-zero exit = a gate failure; fix before continuing.

**Judgment (LLM flagger — flag, never approve):** the two checks a script cannot make. Emit a reviewable diff for the engineer; never assert "looks faithful."

1. **No inlined shared logic.** For each shared hook/util/type the legacy component imports, confirm the port imports the same shared module — not a per-consumer copy. List any logic that was duplicated instead of shared.
2. **1:1 behavior.** Walk the legacy component's branches, effects, and tracking calls; list any the port adds, drops, or alters. Output a `legacy → port` diff of observable behavior. The engineer decides; you do not.

Per the verification rule: a script saying "key X missing from de.json" is trusted; an LLM saying "looks faithful" is not. The flagger produces evidence, the engineer rules.

## PR conventions

- Reviewer: assign the team reviewer on every PR
- Size: ≤ 500 lines diff — split if larger. Plan the split **before writing code**, not after. Shared components (EmptyState, InstancesBar, etc.) can be PR A; page logic PR B. Visual snapshot regeneration commits inflate diffs — account for them when estimating size.
- Note in PR description any features currently being built in old Operate that must be mirrored in the unified app
- Commit message: `feat: migrate Operate <PageName> page to unified app`

## Pre-flight checklist

1. **Dependencies** — grep `package.json` for any package you import; add if missing, never rely on transitive deps.
2. **Route files** — `beforeLoad` = auth/guards only; `loader` = data prefetch (see `docs/monorepo-docs/frontend/data-loading.md`).
3. **Data placement** — colocated `<feature>.queries.ts` exporting `queryOptions`; `shared/http/queries.ts` is cross-app only.
4. **Test fixtures** — check `shared-test-modules/api-mocks/` first; new mocks go in `shared-test-modules/mock-handlers.ts` only.
5. **Global types** — check `tsconfig.browser.json` `types` before touching `global.d.ts`; `vite/client` covers `*.svg`.
6. **Zod schemas** — check `@camunda/camunda-api-zod-schemas/8.10` before writing a custom endpoint.
7. **Pagination** — default is infinite scroll with `useSuspenseInfiniteQuery`; trust `hasMoreTotalItems`, prefer cursor over offset.
8. **Eventually consistent** — `x-eventually-consistent` in the spec → add `refetchInterval` (1s fresh, 5s batch, slower otherwise); pessimistic UI.
9. **Long-running op** — POST returns a key; poll `GET /v2/batch-operations/{key}`. Submit toast, poll in background, never block the page.
10. **Permissions** — actions: button stays visible, 403 → toast + re-enable; data loads: render a forbidden state.
11. **Tenant-aware** — render tenant UI only when multi-tenancy is on; always pass the active tenant.

## URL as state

| Kind | Use for |
|------|---------|
| Route params (`$key`) | Entity identity (`/processes/$processKey`) |
| Search params (`validateSearch` + Zod) | View state: filters, sort, cursor, selection, active tab, modal-open flag |
| Local React state | Ephemeral UI only: open menu, input draft, hover, focus |

Validate every search/path param with Zod via `validateSearch` / `parseParams`. Reuse `@camunda/camunda-api-zod-schemas` shapes when they map to an API contract.

## Tracking events

Never drop a tracking event when porting. Carry every `tracking.track` call across, namespaced: legacy `foo` → `operate:foo`. The fidelity script enforces this.

## Feature flags

Gate unfinished features in `src/shared/feature-flags.ts` (`SCREAMING_SNAKE_CASE`, default `false`). Gate at the highest level (route, page, nav item), not deep inside modules. Remove the flag in a dedicated cleanup PR once the feature ships.
