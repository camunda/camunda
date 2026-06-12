---

name: tasklist-frontend
description: Use when building, changing, or testing features in the Tasklist pod area of the orchestration cluster webapp at webapp/client/apps/orchestration-cluster-webapp/src/tasklist/. Trigger whenever someone is adding or modifying Tasklist pages, modules, components, hooks, stores, or Tasklist routes — even for small changes like adding a column, filter, panel, or schema. Also use when someone asks about Tasklist pod structure, conventions, or where Tasklist code belongs.

---

# Tasklist Frontend

The Tasklist pod area of the orchestration cluster webapp lives at `src/tasklist/`
(`webapp/client/apps/orchestration-cluster-webapp/src/tasklist/`). It is owned by the
**Employee Engagement & Tasklist** pod.

This skill covers only Tasklist-specific structure and boundaries. For all general
orchestration cluster webapp conventions — Carbon, `#/` path aliases, URL-as-state,
data loading (TanStack Router + Query), forms, naming, exports, and local checks —
defer to the `frontend-feature` skill and `docs/monorepo-docs/frontend/data-loading.md`.
Don't duplicate those rules here; follow them.

## Project structure

`src/tasklist/modules/` holds the building blocks the pod is assembled from, split by
**meaningful unit — not by feature**. A module owns one small concern that one or more
pages reuse — for example, a layout module shared by sibling pages, or a filters module
those pages share.

Cross-cutting concerns (http, errors, theme, tracking, etc.) are not Tasklist modules —
they live in `src/shared/`. See "The shared folder boundary" below.

Small, self-contained pages can live entirely inside a single module folder. The `login`
module is an example of this shape.

Keep each module's internal structure **flat**. React components live in a `components/`
subfolder; everything else (hooks, utilities, stores, schemas, etc.) sits at the module
root.

A real reference is the `available-tasks` module:

```
src/tasklist/modules/available-tasks/
  searchSchema.ts                 # non-component code at the module root
  components/
    CollapsiblePanel.tsx          # React components in components/
    CollapsiblePanel.module.scss
    CollapsiblePanel.test.tsx
    Filters.tsx
    NoTasks.tsx
    Options.tsx
```

## Pages and routes

- **Pages** are assembled at `src/tasklist/pages/`. Name them `PascalCase` + `Page` suffix
  (e.g., `TasklistIndexPage`); co-locate styles and tests with the same name
  (`TasklistIndexPage.module.scss`, `TasklistIndexPage.test.tsx`). A page is an **assembly
  point**: it composes the building blocks from `src/tasklist/modules/` into a screen. Keep
  pages and the components they render **presentational — they receive their data via props,
  they don't fetch it**.
- **Routes** are plugged in at `src/routes/_auth/tasklist/`. Route files are **thin
  wrappers** — they import the page component and wire it into TanStack Router, with no
  feature logic. File path = URL path; Tasklist routes are auth-gated under `_auth/`.

```tsx
// src/routes/_auth/tasklist/processes.tsx
import {TasklistProcessesPage} from '#/tasklist/pages/TasklistProcessesPage';
import {createFileRoute} from '@tanstack/react-router';

export const Route = createFileRoute('/_auth/tasklist/processes')({
	component: TasklistProcessesPage,
});
```

The `_auth/tasklist/route.tsx` file owns the pod-level concerns (component availability
check, `errorComponent`, `notFoundComponent`, page title). Add new Tasklist pages as new
route files under `src/routes/_auth/tasklist/`.

### Data loading

Plug data loading into the **route**, not into the page or its leaf components. The route
loader prefetches the server state and the route reads it and threads it down to the page
and components via props. Don't fetch inside leaf components when the data can be loaded at
the route edge and passed through props — this keeps pages and modules presentational and
easy to test. Follow the documented order of preference for the mechanics (route loader +
`useSuspenseQuery`, `pendingComponent`, streamed slices) — see
`docs/monorepo-docs/frontend/data-loading.md` and the `frontend-feature` skill.

`queryOptions` and mutations live in the shared dictionary (`#/shared/http/queries.ts`).
Keep each entry **simple and free of business logic** — just the cache key plus the
fetcher/mutation — so other pods can reuse it. Any Tasklist-specific shaping or business
logic belongs in the pod (page or module), not in the dictionary entry.

## The shared folder boundary

Cross-pod infrastructure lives in `src/shared/` (http, auth, config, errors, theme, i18n,
tracking, feature flags, shared pages).

**For Tasklist work, do NOT unilaterally change anything inside `src/shared/`.** Reuse what
is already there. If a Tasklist feature seems to need a new or changed shared module, stop
and surface it to the engineer — only modify `src/shared/` when an engineer explicitly tells
you to. Shared changes affect every pod, so they are an engineer decision, not an agent one.

Keep new Tasklist code inside `src/tasklist/` (modules and pages) and its routes inside
`src/routes/_auth/tasklist/`.

The one expected exception is the shared query/mutation dictionary
(`#/shared/http/queries.ts`): contributing a **simple, business-logic-free** entry that
other pods can reuse is normal collaboration, not a unilateral shared change. Substantive
shared *infrastructure* changes still need engineer sign-off.

## Boundaries

- **Build** Tasklist features inside `src/tasklist/modules/` (building blocks) and
  `src/tasklist/pages/` (assembled pages).
- **Plug** routes in as thin wrappers under `src/routes/_auth/tasklist/`.
- **Keep modules flat** — components in `components/`, everything else at the module root.
- **Load data on the route** (loader) and pass it to pages/components via props — keep them
  presentational. Follow `docs/monorepo-docs/frontend/data-loading.md`.
- **Keep shared query/mutation dictionary entries simple and business-logic-free** so they
  stay reusable across pods.
- **Never** modify `src/shared/` for Tasklist work without explicit engineer instruction.
- **Defer** to `frontend-feature` for general OC-webapp conventions, and to
  `frontend-unit-test` / `frontend-integration-test` for testing patterns.
