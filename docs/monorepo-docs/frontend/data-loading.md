# Data loading

Data fetching in `@camunda/orchestration-cluster-webapp` uses
[TanStack Router](https://tanstack.com/router/latest) for route-level
loaders and [TanStack Query](https://tanstack.com/query/latest) for
server-state caching. This page defines the order of preference for
data-loading patterns in the app. For background, see the
[TanStack Router data-loading guide](https://tanstack.com/router/latest/docs/framework/react/guide/data-loading).

## Stack

- **TanStack Router** — route loaders, link preloading, suspense integration.
- **TanStack Query** — server-state cache, suspense queries, mutations.

## Order of preference

The tiers below are listed in descending order of preference. Pick the
highest option that fits the page; drop a tier only when a constraint
forces it.

### 1. Route loader + suspense queries

The route loader prefetches via `queryClient.ensureQueryData`; the
component reads the same options via `useSuspenseQuery`.

```tsx
// src/routes/_auth/profile.tsx
import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { currentUserQueryOptions } from "#/shared/http/queries";
import { MyUserComponent } from "#/operate/components/MyUserComponent";

export const Route = createFileRoute("/_auth/profile")({
  loader: ({ context: { queryClient } }) => {
    // route will only render once this promise resolves, so we can await or return it
    return queryClient.ensureQueryData(currentUserQueryOptions);
  },
  component: Profile,
});

function Profile() {
  const { data: user } = useSuspenseQuery(currentUserQueryOptions);
  return <MyUserComponent user={user} />;
}
```

`queryOptions` constants live in `#/shared/http/queries.ts` (or
co-located with the owning pod). Loader and component import the
same reference so the cache key and fetcher stay in sync.

### 2. Route loader + `pendingComponent`

Same shape as Tier 1, plus `pendingComponent` so the router falls back
to a placeholder instead of freezing the previous page when the loader
takes too long.

```tsx
// src/routes/_auth/dashboard.tsx
import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { dashboardQueryOptions } from "#/shared/http/queries";
import { DashboardSkeleton } from "#/operate/components/DashboardSkeleton";
import { MyDashboard } from "#/operate/components/MyDashboard";

export const Route = createFileRoute("/_auth/dashboard")({
  loader: ({ context: { queryClient } }) => {
    queryClient.ensureQueryData(dashboardQueryOptions);
  },
  pendingComponent: DashboardSkeleton,
  component: Dashboard,
});

function Dashboard() {
  const { data } = useSuspenseQuery(dashboardQueryOptions);
  return <MyDashboard data={data} />;
}
```

Tune `pendingMs` (default 1000ms) and `pendingMinMs` per route, or set
`defaultPendingComponent` and `defaultPendingMs` on `createRouter` to
apply globally.

### 3. Route loader + streamed promises + granular skeletons

The loader awaits the fast slice via `ensureQueryData` and
fires-and-forgets the slow one via `prefetchQuery`. The slow slice is
wrapped in its own `<Suspense>` so the fast content paints immediately
and a skeleton stands in for the slow part until it resolves.

```tsx
// src/routes/_auth/dashboard.tsx
import { Suspense } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import {
  dashboardSummaryQueryOptions,
  dashboardMetricsQueryOptions,
} from "#/shared/http/queries";
import { MetricsPanelSkeleton } from "#/operate/components/MetricsPanelSkeleton";
import { Metrics } from "#/operate/components/Metrics";

export const Route = createFileRoute("/_auth/dashboard")({
  loader: async ({ context: { queryClient } }) => {
    // await the fast query
    await queryClient.prefetchQuery(dashboardSummaryQueryOptions);

    // fire-and-forget the slow query
    queryClient.ensureQueryData(dashboardMetricsQueryOptions);
  },
  component: Dashboard,
});

function Dashboard() {
  const { data: summary } = useSuspenseQuery(dashboardSummaryQueryOptions);
  return (
    <main>
      <h1>{summary.title}</h1>
      <Suspense fallback={<MetricsPanelSkeleton />}>
        {/*.Consumes dashboardMetricsQueryOptions */}
        <Metrics />
      </Suspense>
    </main>
  );
}
```

Pick this over Tier 2 only when the slow slice is isolated and the rest
of the page renders fast — otherwise Tier 2 is simpler.

## Error handling

The default is `errorComponent` on the route. The `queryFn` throws on
failure, so Query rejects, the loader rejects, and the router renders
`errorComponent` in place of the route component.

```tsx
// src/routes/_auth/profile.tsx
import {
  createFileRoute,
  type ErrorComponentProps,
} from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { currentUserQueryOptions } from "#/shared/http/queries";

export const Route = createFileRoute("/_auth/profile")({
  loader: ({ context: { queryClient } }) =>
    queryClient.ensureQueryData(currentUserQueryOptions),
  errorComponent: ProfileError,
  component: Profile,
});

function ProfileError({ error, reset }: ErrorComponentProps) {
  return (
    <main>
      <h1>Could not load profile</h1>
      <p>{error.message}</p>
      <button onClick={reset}>Retry</button>
    </main>
  );
}

function Profile() {
  const { data: user } = useSuspenseQuery(currentUserQueryOptions);
  return <h1>Hello, {user.displayName}</h1>;
}
```

Set `defaultErrorComponent` on `createRouter` for a global fallback.
Reach for a component-level `<ErrorBoundary>` only when the rest of
the page should keep rendering. 401s are handled centrally by the
`request()` wrapper (cache clear + login redirect), so `errorComponent`
covers everything else.

## References

- [TanStack Router — Data Loading](https://tanstack.com/router/latest/docs/guide/data-loading)
- [TanStack Query — Suspense](https://tanstack.com/query/latest/docs/framework/react/guides/suspense)
