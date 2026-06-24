# Creating a new page

How to build a new page in `@camunda/orchestration-cluster-webapp`.
Read [Before starting](./before-starting.md) first; loader patterns
live in [Data loading](../data-loading.md).

## Overview

Each pod owns its area of the source tree:

- `src/operate/` — Operate pod
- `src/tasklist/` — Tasklist pod
- `src/admin/` — Admin pod

Pod areas are **autonomous**: pods decide their own internal folder
structure, naming conventions, and patterns. There is no prescribed
layout inside a pod.

`src/shared/` holds cross-cutting infrastructure (http, auth, config,
errors, theme, i18n, tracking) that all pods can import. Changes to
shared code require cross-pod coordination.

`src/routes/` is shared routing infrastructure. Route files are thin
wrappers that import pod-owned components and wire them into the router.

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

## Step 1: Build the feature in your pod's area

Create your page component and any supporting code inside your pod's
directory (`src/operate/`, `src/tasklist/`, or `src/admin/`). How you
organise that code internally is your pod's decision.

For cross-cutting concerns, reuse what's already in `src/shared/`:

- HTTP requests and query options → `#/shared/http/`
- Authentication state → `#/shared/auth/`
- App configuration → `#/shared/config/`
- Error types → `#/shared/errors/`
- i18n → `#/shared/i18n/`
- Theme → `#/shared/theme/`

Example page component (Operate pod, structure is illustrative):

```tsx
// src/operate/pages/DashboardPage.tsx
import {DashboardHeader} from '#/operate/components/DashboardHeader';
import {Metrics} from '#/operate/components/Metrics';
import type {Dashboard} from '#/shared/http/queries';
import styles from './DashboardPage.module.scss';

export function DashboardPage({data}: {data: Dashboard}) {
  return (
    <main className={styles.page}>
      <DashboardHeader title={data.title} />
      <Metrics metrics={data.metrics} />
    </main>
  );
}
```

Pages are glue: receive data as props, orchestrate components. No
`fetch`, no business logic. Prefer prop-passing — the route reads the
loader query and passes data down. Reach for `useSuspenseQuery` inside
the page only when prop drilling through a deep tree hurts more than it
helps. See [Data loading](../data-loading.md) for the loader/query
patterns.

## Step 2: Plug into the router

Add a route file under `src/routes/_auth/{pod}/`. File path = URL path.

```tsx
// src/routes/_auth/operate/dashboard.tsx
import {createFileRoute} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {dashboardQueryOptions} from '#/shared/http/queries';
import {DashboardPage} from '#/operate/pages/DashboardPage';

export const Route = createFileRoute('/_auth/operate/dashboard')({
  loader: ({context: {queryClient}}) => {
    queryClient.ensureQueryData(dashboardQueryOptions);
  },
  component: Dashboard,
});

function Dashboard() {
  const {data} = useSuspenseQuery(dashboardQueryOptions);
  return <DashboardPage data={data} />;
}
```

`queryClient.ensureQueryData` is the preferred loader. Skip
`pendingComponent` / `errorComponent` only when a router-level default
applies. Full tier breakdown in [Data loading](../data-loading.md).

## Use the URL as state

The URL holds the page's state. Components read from it, not from each
other.

- **Route params**: entity identity (`/_auth/operate/processes/$processKey`).
- **Search params**: view state including filters, sort, cursor, selection,
  active tab, and modal-open flag.
- **Local React state**: ephemeral UI only, such as open menu, input draft,
  hover, and focus.

Payoff: deep-linkable, refresh preserves intent, back/forward work,
siblings stay decoupled.

```tsx
function StateFilter() {
  const {state} = useSearch({from: '/some-router'});
  const navigate = useNavigate();

  return (
    <select
      value={state}
      onChange={(e) =>
        navigate({
          search: (prev) => ({...prev, state: e.target.value || undefined}),
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
// src/routes/_auth/operate/processes.tsx
import {createFileRoute} from '@tanstack/react-router';
import {z} from 'zod';

const searchSchema = z.object({
  state: z.enum(['active', 'completed', 'canceled']).optional(),
  cursor: z.string().optional(),
  limit: z.number().int().min(1).max(100).default(50),
});

export const Route = createFileRoute('/_auth/operate/processes')({
  validateSearch: searchSchema,
  // loader, component, ...
});
```

`validateSearch` for search params, `parseParams` for path params.
Reuse schemas from `@camunda/camunda-api-zod-schemas` when the URL
slice maps to an API contract
([Camunda API Zod schemas](../camunda-api-zod-schemas.md)). Otherwise
co-locate the schema in the route file.

`useSearch()` and `useParams()` return the validated, typed output.

## Checklist

Before opening the PR:

- [ ] Feature code lives in the pod's area (`src/{operate,tasklist,admin}/`).
- [ ] Shared cross-cutting concerns reused from `src/shared/` where applicable.
- [ ] Route file in `src/routes/_auth/{pod}/{path}.tsx`; URL matches file path;
      auth-gated routes under `_auth/`.
- [ ] Loader uses `queryClient.ensureQueryData`; `pendingComponent` +
      `errorComponent` wired or a router-level default applies.
- [ ] Search params validated via `validateSearch` + Zod; path params
      via `parseParams`.
- [ ] View state lives in the URL.
- [ ] Dev server boots the new route (`npm run dev:oc`).
