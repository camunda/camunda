---
name: frontend-operate-migrator
description: Use when migrating any Operate page from operate/client/ to the orchestration cluster webapp, including end-to-end execution from a migration ticket number. Always read frontend-migrator first — this skill adds Operate-specific overrides, the migration loop protocol, and page-by-page context.
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
| `notifications.tsx` | Toast queue | `notificationsStore` from `#/shared/notifications/notifications.store` — already exists, reuse |

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

**Reference implementation:** `operate/pages/Dashboard/useRunningInstancesCount.ts` — exports both `runningInstancesCountQuery()` (used in the route `loader` for prefetching — data goes in `loader`, never `beforeLoad`, which is reserved for guards/redirects; see `routes/_auth/operate/index.tsx` and `docs/monorepo-docs/frontend/data-loading.md`) and `useRunningInstancesCount()` (used in the component). The route imports the query function; the component imports the hook. `queries.ts` stays thin.

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

## Writes (operations, mutations)

The codebase does not use TanStack Query's `useMutation`. Follow the Tasklist patterns by write complexity:

- **Simple write, then refresh:** call `request(endpoints.xxx(...))` in the event handler, then `queryClient.invalidateQueries({queryKey: [...]})` for affected lists.
- **Write with a lifecycle** (the API returns 202/accepted and the resource transitions through pending states before settling): model it as an XState machine (`setup` + `fromPromise` actors) that receives `queryClient` as input. Reference: `tasklist/modules/task-details/taskCompletionMachine.ts` —
  - optimistic update via `queryClient.setQueryData(...)`, with rollback on failure
  - poll the resource via `queryClient.fetchQuery(queries.xxx())` until it leaves the transitional state
  - `queryClient.invalidateQueries(...)` for affected list queries on completion
  - the route can also poll transitional states via `refetchInterval` (see `POLLING_STATES` in `routes/_auth/tasklist/_tasks/$userTaskKey/route.tsx`)

Operate's batch operations (cancel/retry/delete, batch modification) follow the accepted → pending → completed lifecycle, so expect the machine pattern there. Do not put write logic in `queries.ts` — it stays a read-only registry.

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
import {mockSomeEndpoint} from '#/shared-test-modules/mock-handlers';
import {createSomeEntity} from '#/shared-test-modules/api-mocks/some-entities';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';

// worker is injected and auto-managed by the `it` fixture — no beforeAll/afterAll needed

it('should display process instances', async ({worker}) => {
  worker.use(
    mockSomeEndpoint({successResponse: HttpResponse.json({items: [createSomeEntity()], totalCount: 1})}),
  );

  const screen = await renderWithRouter(SomePage, {path: '/operate'});

  await userEvent.click(screen.getByRole('button', {name: 'Expand'}));
  await expect.element(screen.getByText('Dashboard')).toBeVisible();
});
```

**Add new endpoint mocks to `shared-test-modules/mock-handlers.ts`** using `createEndpointMock()`. Never inline `http.post(...)` directly in test files.

**Response fixtures come from factories in `shared-test-modules/api-mocks/`** (`createBatchOperation`, `createUserTask`, …). Add a factory there when mocking a new entity — never build response literals inline in tests.

**POST/PUT/PATCH mocks must validate the request payload:** pass `{schema, successResponse, failureResponse}` — the mock returns `failureResponse` when the request body fails the Zod schema, so tests catch malformed payloads instead of green-lighting them. See `mockCompleteTaskEndpoint` usage in `tasklist/pages/TaskDetailsTaskPage.test.tsx` (it extends the API schema to pin exact expected variables).

**Interactions use `userEvent` from `'vitest/browser'`** (`userEvent.click`, `userEvent.fill`, `userEvent.keyboard`); direct `locator.click()` is acceptable for simple clicks.

## Ticket-driven execution contract

An explicit `/frontend-operate-migrator <ticket-number>` request means: execute the PR-sized
migration end to end and return only when its draft PR has converged. This contract applies only to
an execution request, not when the user is asking for analysis or planning.

Unless the user narrows the permissions, the execution request authorizes local edits, branch
creation, commits, pushes, draft PR creation, reviewer requests, and handling review threads. It does
not authorize marking the PR ready, merging it, or changing unrelated code. Later user instructions
replace the relevant permission; never carry permissions from another invocation.

Before editing:

1. Read the ticket and its live parent/subissue hierarchy. Determine whether the supplied number is
   the PR-sized inner issue or a page issue. If it is a page issue, select only a clearly identified,
   unblocked inner issue; never silently migrate the whole page or combine multiple PR-sized units.
2. Inspect linked PRs and branches so work is not duplicated. Rebase the feature branch onto its base
   branch; never create a merge commit.
3. Build an acceptance matrix from the ticket and legacy implementation. Cover observable branches
   and effects, API calls, URL state, loading/empty/error/forbidden states, permissions, tenancy, and
   accessibility. Record intentional omissions such as tracking.
4. Estimate the diff before implementation. Split the work before writing code when it cannot stay
   within the 500-line PR limit.

Keep an internal convergence ledger for the acceptance matrix, fidelity findings, code-review
findings, Copilot threads, CI checks, and Git/PR state. This is task state, not a repository artifact:
do not commit a ledger file.

## Definition of Done (9 gates)

Run from `webapp/client`. Gates 1–6 are local; 7–9 gate the PR (9 is CI-authoritative — verify locally, never push regenerated snapshots). Use the existing package scripts — gates 1, 2, 6 also run together via `npm run lint`.

1. **Prettier** — `npm run lint:prettier`
2. **ESLint** — `npm run lint:eslint`
3. **Typecheck** — `npm run typecheck -w @camunda/orchestration-cluster-webapp`
4. **Unit** — `npm run test:unit -w @camunda/orchestration-cluster-webapp`
5. **Build** — `npm run build -w @camunda/orchestration-cluster-webapp`
6. **Knip** — `npm run lint:knip`
7. **Integration** — `npm run test:integration -w @camunda/orchestration-cluster-webapp`
8. **a11y** — `npm run test:a11y -w @camunda/orchestration-cluster-webapp`
9. **Visual** — CI is authoritative; never regenerate snapshots locally.

## Migration loop

Iterate against feedback signals in three tiers, by cost. Loop on the cheapest tier that can fail; graduate only when green.

| Tier | Gates | Loop on it when | Max iterations |
|------|-------|-----------------|----------------|
| **edit** | 1 Prettier · 2 ESLint · 3 Typecheck | after every meaningful edit (seconds) | 5 |
| **component** | + 4 Unit · 5 Build · 6 Knip | a component is done (minutes) | 5 |
| **PR** | 7 Integration · 8 a11y · 9 Visual (CI) | before returning — drive with `ci-fix-failure` | 3 |

**Stop condition (guardrail).** Each tier loop is bounded. If a tier is not green within its max iterations, **stop and report** — do not keep iterating. A loop with no bound spins forever and burns budget on a problem it cannot converge on; the cap forces escalation to the engineer instead. An iteration that makes zero progress (same failure, same fix attempted) counts double — bail early.

### Full ticket-to-PR loop

```
ticket + live hierarchy → acceptance matrix → smallest migration diff
→ [edit tier] → deterministic fidelity check
→ independent behavior-fidelity review → fix valid findings → recheck
→ [component tier]
→ independent code review → fix valid findings → re-review changed diff
→ commit + push → open DRAFT PR → request Copilot review
→ handle Copilot threads → push fixes → [PR tier]
→ refresh threads + CI until converged → final closure check → report
```

#### 1. Implement and validate locally

Implement the smallest complete migration diff. Run the edit tier after meaningful edits, then the
deterministic fidelity script and component tier. Fix at the cheapest tier that exposes the problem;
do not defer known local failures to CI.

#### 2. Independent behavior-fidelity review

After the edit tier is green, spawn a fresh read-only frontend reviewer. Give it the ticket,
acceptance matrix, exact legacy source, migrated source, and diff. Its only task is to produce an
evidence-backed `legacy → migrated` list of added, removed, or changed observable behavior and any
shared logic that was copied instead of reused. Tracking-only differences are ignored.

The implementing agent adjudicates every finding:

- Fix a valid discrepancy with the smallest change, then rerun the affected local tier and fidelity
  review.
- Reject an invalid finding only with concrete code or test evidence in the convergence ledger.
- Stop with a precise blocker when product intent is genuinely ambiguous; never invent behavior to
  make the review pass.

Repeat until there is no unexplained observable difference.

#### 3. Independent code review

Once fidelity and the component tier are green, spawn a fresh read-only code reviewer for the final
diff. Give it the ticket, acceptance matrix, repository instructions, and relevant migration skills.
Review correctness, regressions, architecture, type safety, tests, accessibility, and unnecessary
scope. Do not duplicate the behavior-fidelity pass.

Fix valid findings, rerun the cheapest affected tier, then re-review the changed diff. Do not
blindly implement speculative suggestions or expand the ticket scope.

The behavior-fidelity and code-review phases each have a maximum of 3 iterations. An iteration with
the same finding and no new evidence counts double.

#### 4. Draft PR and Copilot convergence

Create the commit with the engineer as sole author; never add an AI co-author trailer. Push and open
the PR as a draft using the repository template. Put `closes #<inner-subissue>` under the exact
`## Related issues` heading, assign the team reviewer, and request Copilot review. Keep the PR draft
after convergence; the engineer decides when it is ready.

When Copilot finishes, fetch every unresolved thread and classify each comment as valid, invalid,
already handled, or out of scope. Fix valid comments, reply with concise evidence where useful,
resolve handled threads, rerun the cheapest affected tier, and push. Refresh review threads after
every push because a new review can introduce findings.

#### 5. CI convergence

Run the PR tier after the draft PR is open. Use `ci-fix-failure` to diagnose a failing check; the
implementing agent owns applying the valid fix, validating it locally, and pushing it. After every
push, refresh both CI and unresolved review threads.

The outer Copilot/CI loop has a maximum of 3 iterations. An iteration with the same failure and no
new evidence counts double. At the cap, report the exact blocker instead of retrying blindly or
claiming completion.

### State lives in GitHub, checked live

Epic [#51305](https://github.com/camunda/camunda/issues/51305) → page subissue (sibling naming `Migrate Operate <Page> page to unified webapp`) → inner subissue per PR (conventional-commit naming, one PR each). Never cache issue/PR state in a file; query it:

- `gh issue view <n> --repo camunda/camunda --json title,body,state`
- `gh api repos/camunda/camunda/issues/<n>/sub_issues`
- `gh pr list --repo camunda/camunda --search "<page>"`

### Finishing step — draft PR + Copilot review

Do not return merely because the PR exists or one CI pass is green. Return only when:

- every ticket requirement is implemented or explicitly documented as out of scope;
- the behavior-fidelity review has no unexplained observable difference;
- all valid independent-review and Copilot findings are handled;
- no actionable Copilot thread remains unresolved;
- all 9 gates are green on the latest pushed SHA;
- the intended commit shape has the engineer as sole author;
- the local branch, remote branch, and draft PR head are synchronized; and
- the PR is correctly linked, remains draft, and has the team reviewer assigned.

- Create the PR with `gh pr create --draft`, a conventional-commit title, and a completed repository
  template.
- Request Copilot review using the configured reviewer mechanism, then verify that the request was
  registered before waiting for the review.

Update the page issue with in-progress PR state when its checklist requires it. Do not mark the
inner issue or page migration complete before the PR is merged.

Report only the draft PR URL, what migrated, meaningful behavior decisions, and blockers. Routine
green checks and test counts are implicit.

Every recurring failure mode becomes a rule, not a one-off fix: encode it in this skill or shared
agent memory so it cannot recur.

## Fidelity checks (the 1:1 oracle)

Run **after the edit tier, scoped to the just-ported component** — not across the whole `operate/` dir, or you flag not-yet-ported features.

**Deterministic (script, always trusted):** run from the **repo root** (the script and its default locales path are repo-root relative — not `webapp/client`):

```bash
node .claude/skills/frontend-operate-migrator/scripts/fidelity.mjs \
  --ported <ported-component-dir>
```

Checks locale coverage (every `t('operate.*')` key exists in en/de/fr/es). Non-zero exit = a gate failure; fix before continuing.

**Judgment (LLM flagger — flag, never approve):** the two checks a script cannot make. Emit an evidence-backed diff for the implementing agent; never assert "looks faithful."

1. **No inlined shared logic.** For each shared hook/util/type the legacy component imports, confirm the port imports the same shared module — not a per-consumer copy. List any logic that was duplicated instead of shared.
2. **1:1 behavior.** Walk the legacy component's branches and effects; list any observable behavior the port adds, drops, or alters. Ignore tracking-only behavior. Output a `legacy → port` diff of observable behavior. The implementing agent adjudicates clear findings; escalate genuine product ambiguity.

Per the verification rule: a script saying "key X missing from de.json" is trusted; an LLM saying
"looks faithful" is not. The flagger produces evidence. The implementing agent fixes clear
discrepancies and escalates only genuinely ambiguous product behavior.

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

The orchestration cluster webapp does not use Mixpanel tracking. Do not port legacy tracking events, tracking-only state, or tracking tests. If a callback combines tracking with feature behavior, preserve the feature behavior and remove only the tracking code.

## Feature flags

Gate unfinished features in `src/shared/feature-flags.ts` (`SCREAMING_SNAKE_CASE`, default `false`). Gate at the highest level (route, page, nav item), not deep inside modules. Remove the flag in a dedicated cleanup PR once the feature ships.
