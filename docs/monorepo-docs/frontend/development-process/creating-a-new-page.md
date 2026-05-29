# Creating a new page

How to build a new page in `@camunda/orchestration-cluster-webapp`.
Read [Before starting](./before-starting.md) first; loader patterns
live in [Data loading](../data-loading.md).

## Overview

A page spans three folders in `apps/orchestration-cluster-webapp/src/`:

- `modules/`: components, hooks, stores, utilities
  (see [Modules](../orchestration-cluster-webapp.md#modules)).
- `pages/`: one file per page, wires modules together.
- `routes/`: [TanStack Router](https://tanstack.com/router/latest)
  mount points. Owns the loader, `pendingComponent`, and
  `errorComponent`.

Build modules, compose a page, plug it into a route.

## Default to a new route

If a user can navigate to it, it gets its own URL. Routes are cheap,
the payoff is large: deep-linkable views, working back/forward,
shareable filter state, decoupled components, refresh that preserves
intent.

Skip a route only for:

- Transient overlays such as toasts, snackbars, command palette, and hover
  cards.
- Modals that should not be linkable, like confirmations and inline edit
  dialogs.
- In-page tabs on a page already gated by the same data. Encode the
  active tab as a search param (`/_auth/processes?tab=completed`).

Anything else, justify it in the PR description.

## Step 1: Build the modules

Modules live in `src/modules/<thing>/`. Each owns one focused concern
(`http`, `errors`, `theme`, `login`).

Layout:

- `components/`: React components, optionally a subfolder per
  component for co-located styles and tests.
- Module root: hooks, stores, utilities, types.

Reuse before building. For what counts as a module, see the
[Modules section](../orchestration-cluster-webapp.md#modules).

## Step 2: Compose the page

Page lives at `src/pages/<PageName>.tsx` with co-located styles and tests:

```
src/pages/
├── DashboardPage.tsx
├── DashboardPage.module.scss
└── DashboardPage.test.tsx
```

Pages are glue: receive data as props, orchestrate module components.
No `fetch`, no business logic. Prefer prop-passing — the route reads
the loader query and passes data down. Reach for `useSuspenseQuery`
inside the page only when prop drilling through a deep tree hurts more
than it helps. See [Data loading](../data-loading.md) for the
loader/query patterns.

```tsx
// src/pages/DashboardPage.tsx
import { DashboardHeader } from "#/modules/dashboard/components/DashboardHeader";
import { Metrics } from "#/modules/dashboard/components/Metrics";
import type { Dashboard } from "#/modules/http/queries";
import styles from "./DashboardPage.module.scss";

export function DashboardPage({ data }: { data: Dashboard }) {
  return (
    <main className={styles.page}>
      <DashboardHeader title={data.title} />
      <Metrics metrics={data.metrics} />
    </main>
  );
}
```

## Step 3: Plug into the router

Route lives at `src/routes/<path>.tsx`. File path = URL path. Place
under `_auth/` when auth is required.

```tsx
// src/routes/_auth/dashboard.tsx
import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { dashboardQueryOptions } from "#/modules/http/queries";
import { DashboardPage } from "#/pages/DashboardPage";

export const Route = createFileRoute("/_auth/dashboard")({
  loader: ({ context: { queryClient } }) => {
    queryClient.ensureQueryData(dashboardQueryOptions);
  },
  component: Dashboard,
});

function Dashboard() {
  const { data } = useSuspenseQuery(dashboardQueryOptions);
  return <DashboardPage data={data} />;
}
```

`queryClient.ensureQueryData` is the preferred loader. Skip
`pendingComponent` / `errorComponent` only when a router-level default
applies. Full tier breakdown in [Data loading](../data-loading.md).

## Use the URL as state

The URL holds the page's state. Components read from it, not from each
other.

- **Route params**: entity identity (`/_auth/processes/$processKey`).
- **Search params**: view state including filters, sort, cursor, selection,
  active tab, and modal-open flag.
- **Local React state**: ephemeral UI only, such as open menu, input draft,
  hover, and focus.

Payoff: deep-linkable, refresh preserves intent, back/forward work,
siblings stay decoupled.

```tsx
function StateFilter() {
  const { state } = useSearch({ from: "/some-router" });
  const navigate = useNavigate();

  return (
    <select
      value={state}
      onChange={(e) =>
        navigate({
          search: (prev) => ({ ...prev, state: e.target.value || undefined }),
        })
      }
    >
      <option value="">All</option>
      <option value="active">Active</option>
      <option value="completed">Completed</option>
    </select>
  );
}
```

Lifting state to a common parent? Route it through the URL first.

## Validate URL inputs with Zod

URLs are user input. Pin every search-param and path-param shape with
[Zod](https://zod.dev/).

```tsx
// src/routes/_auth/processes.tsx
import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";

const searchSchema = z.object({
  state: z.enum(["active", "completed", "canceled"]).optional(),
  cursor: z.string().optional(),
  limit: z.number().int().min(1).max(100).default(50),
});

export const Route = createFileRoute("/_auth/processes")({
  validateSearch: searchSchema,
  // loader, component, ...
});
```

`validateSearch` for search params, `parseParams` for path params.
Reuse schemas from `@camunda/camunda-api-zod-schemas` when the URL
slice maps to an API contract
([Camunda API Zod schemas](../camunda-api-zod-schemas.md)). Otherwise
co-locate the schema in the route file.

`useSearch()` and `useParams()` return the validated,
typed output.

## Keep it flat

Three layers is the budget. Don't nest.

- **No page-shaped modules** — unless the page is small and
  self-contained. A `modules/<page>/` mirroring a large page means
  page logic leaked. A tiny feature that fits in one module folder
  is fine (e.g. `login`).
- **No deep module trees.** Cap depth at
  `modules/<thing>/components/<Component>/`.
- **No abstractions for hypothetical use.** Wait until the shape is forced.

Two data-flow rules:

- **API data → props.** The route reads the loader query and passes
  data down through props. This keeps pages testable and dependencies
  explicit.
- **View state → URL.** Filters, sort, cursor, selection live in
  search params. Children read the URL directly via `useSearch` —
  no parent → child plumbing for view state.

Three similar lines beats a premature wrapper.

## Checklist

Before opening the PR:

- [ ] Modules created or existing ones reused; no business logic in
      `src/pages/`.
- [ ] Page in `src/pages/<PageName>.tsx` (PascalCase + `Page` suffix); styles + tests co-located and named to match.
- [ ] Route in `src/routes/<path>.tsx`; URL matches file path;
      auth-gated routes under `_auth/`.
- [ ] Loader uses `queryClient.ensureQueryData`; `pendingComponent` +
      `errorComponent` wired or a router-level default applies.
- [ ] Search params validated via `validateSearch` + Zod; path params
      via `parseParams`.
- [ ] View state lives in the URL.
- [ ] No deeply-nested module folders.
- [ ] Dev server boots the new route (`npm run dev:oc`).
