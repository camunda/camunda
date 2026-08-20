# Things to consider before starting

Before planning a feature in `@camunda/orchestration-cluster-webapp`,
work through the questions below. Each one touches a Camunda-specific
behavior that shapes the page architecture. The answers below are our
defaults. Deviate only with a good reason.

## Are the endpoints already in `@camunda/camunda-api-zod-schemas`?

The orchestration-cluster webapp consumes the API through
`@camunda/camunda-api-zod-schemas`, which is currently hand-written.
Generation from the OpenAPI spec is on the roadmap but not in place
yet. Before you start, check whether the endpoints your feature needs
are already exported. If they aren't, add them to the package as part
of your work. See
[Camunda API Zod schemas](../camunda-api-zod-schemas.md) for the
package layout and conventions.

## Does the data paginate?

Most list endpoints in the Camunda API paginate. Check the OpenAPI spec
for the endpoints you'll consume. If they do, our default is infinite
scrolling with `useSuspenseInfiniteQuery` from TanStack Query, and virtualized
rows when the list can grow unbounded. For other styles of pagination we must consult with the design team. The
API supports three cursor shapes: offset (`from` / `limit`),
cursor-forward (`after`), and cursor-backward (`before`). The default
`limit` is 100, with a max of `10000`. Trust `hasMoreTotalItems`, not
`totalItems`. Prefer cusror-based pagination when possible, as it's more robust due to performance reasons.

## Does the feature need permission handling?

Authorization is enforced server-side. Our convention splits
by interaction type:

- **Actions**: leave the button visible. If the call returns 403,
  surface a toast and re-enable the control.
- **Data loads**: render a forbidden state. Use a page-level forbidden
  view when the entire page is gated; use a section-level forbidden
  view when only one panel is.

A 403 can also mean the feature is disabled for this deployment
(secondary storage off, license missing, OIDC-managed users). The
forbidden state handles both shapes equally well.

## Is the read eventually consistent?

Commands (Zeebe) are strongly consistent. Reads from secondary storage
(Elasticsearch, OpenSearch, RDBMS) are eventually consistent. The
OpenAPI spec flags each operation with `x-eventually-consistent`. If
yours does, plan to poll: there is no SSE or WebSocket today. Use
`refetchInterval` on the relevant query and tune the cadence to the
endpoint (~1s for fresh-task polling, ~5s for batch progress, slower
otherwise).

Default to pessimistic UI. Reach for optimistic updates only with an
explicit reconciliation plan, because the next read may not show your write
yet.

## Does the page need live data?

Not every page needs to poll. Default to static loads — fetch once on
mount. Add `refetchInterval` on the `useSuspenseQuery` only when the
page must reflect server-side changes without user interaction (e.g., a
running process instance view, a task inbox). Polling adds complexity
to tests because assertions must account for ongoing background
requests. Keep the interval as slow as acceptable. See
[Data loading](../data-loading.md) for the query patterns.

## Does it trigger a long-running operation?

Batch operations don't complete inline. The POST returns a
`batchOperationKey`, and the FE polls
`GET /v2/batch-operations/{key}` for the state (`PENDING`,
`PROCESSING`, `COMPLETED`, `FAILED`, `CANCELED`). Cancellation is a
separate `POST /v2/batch-operations/{key}/cancellation`. Our default
is to show a toast confirming the action has been submitted, poll the
backend in the background, and surface the final result via toast or
inline once it lands. Never block the page on the poll. Reach for
optimistic updates to the list or page only when necessary. The poll
is the source of truth.

## Is the feature tenant-aware?

Multi-tenancy is a deployment-level toggle. The default tenant is
`<default>`, and every list endpoint accepts a tenant filter. Branch
your UI on based on the cluster configuration: render the tenant
picker, columns, and filters only when it's on. Always pass the active
tenant in requests when it is.
