# Per-physical-tenant secondary-storage schema initialization: isolated, retried, non-fatal

**DRI**: Houssain Barouni

**Status**: Proposed (8.10)

**Deciders**
- Houssain Barouni
- Lena Schönburg

**Purpose**: Define how a physical tenant's secondary-storage schema is initialized when one orchestration cluster hosts several tenants: what startup waits for, what "ready" asserts, and what happens to a tenant whose storage cannot be reached.

**Audience**: Engineers working on the broker/gateway distribution, the schema managers and the data layer, and operators of multi-tenant clusters.

## Context

Schema initialization today runs every physical tenant sequentially on a single thread, with an unbounded retry *inside* each attempt. A first tenant whose storage is unreachable therefore starves every tenant behind it without ever throwing, and when an HTTP gateway is enabled the whole Spring context refresh blocks on that queue. Any failure that does escape aborts the context and takes the node down, for all tenants.

The two consumers of an initialized schema already tolerate a tenant that is not initialized, each per tenant: the exporter verifies the schema when opening and its director retries opening indefinitely with backoff, per partition; the v2 REST endpoints that need secondary storage are rejected per request for a degraded tenant. Startup therefore does not need to hold the context refresh in order to protect them.

The outcome is per-tenant isolation: tenants initialize concurrently, a failure degrades only that tenant and is retried in the background, and the node serves whichever tenants are usable. This refines [001](001-physical-tenant-health-status-topology.md) D2, whose single-tenant clause changes as recorded in Consequences.

## Decision

**D1. Each physical tenant's schema initializes independently, and no tenant's failure aborts the application context.**
One task per tenant, run concurrently, each retrying indefinitely in the background until it succeeds. No storage failure terminates startup — not for one tenant, and not for all of them. A node that cannot initialize any tenant starts and reports itself not-ready, which withholds traffic exactly as an aborted startup would while keeping the management endpoints, metrics and logs available for diagnosis.

**D2. Startup blocks until every tenant has produced a first outcome, not until one tenant is ready.**
A tenant has *settled* once it has either initialized or failed at least once. This distinguishes the two operational situations that a readiness signal alone cannot: during a rolling upgrade with healthy storage every tenant is still migrating, so the node stays out of the load balancer until all migrations finish and traffic remains on the un-upgraded nodes; during a storage outage the affected tenant settles within its client's connect or socket timeout, so the node comes up promptly and serves the tenants it can. The barrier is bounded by one attempt, never by the retry budget: raising `max-retries` or the backoff extends how long a degraded tenant keeps trying in the background, and never how long startup waits.

**D3. A tenant's readiness is a one-way latch asserting that the schema described in the source code was applied — not that its storage is currently reachable.**
Initialization performs real reads and writes against the tenant's own indices, so success is authenticated, writable, tenant-scoped proof. The latch never reverts, and probes consequently do not observe a storage outage that begins after initialization has succeeded. Detecting runtime degradation is a separate mechanism with its own requirements, and the readiness interface is the seam through which it would later be supplied.

**D4. Node readiness stays "at least one serviceable tenant", and no storage-liveness check participates in it.**
Unchanged from 001 D2. This ADR additionally rules out gating initialization, and therefore readiness, on a storage cluster-health check.

**D5. Failure classification is observational, not control flow.**
Whether a failure is retryable or terminal decides only whether retrying continues and at what log level. Neither outcome aborts startup or changes node-level behavior, so the classification carries no availability risk and does not need to be exhaustive.

**D6. One storage-agnostic per-tenant initialization component owns retry, state and observability; each schema manager exposes a single-attempt operation.**
Bounded transient retries stay inside an attempt, where they already live. The unbounded outer loop, the settle barrier, per-tenant state and the transition logs live in the shared component, which both Elasticsearch/OpenSearch and RDBMS ([#54299](https://github.com/camunda/camunda/issues/54299)) use.

**D7. Per-tenant degradation is surfaced through transition logs and the existing binary per-tenant readiness gauge; no new state metric or state vocabulary is introduced.**
This satisfies 001 D2's requirement that degraded state be visible per tenant in logs and in metrics. A tenant that will not recover without operator action is distinguished in the logs, not by a metric value; the operationally decisive alert — a tenant not ready for longer than some period — is already expressible over the existing gauge.

## Alternatives considered

- **Block startup until every tenant is ready.** Today's behavior generalized to many tenants. Rejected: one tenant's storage outage then withholds the entire node indefinitely, which is the defect this ADR exists to remove.
- **Admit the node as soon as any tenant is ready.** Equivalent whether implemented as a barrier or by having no barrier at all and letting the readiness probe govern admission, since a pod joins its service only once ready. Rejected: during a rolling upgrade it admits the node after the first tenant migrates, so the remaining tenants' requests are rejected by the new node while un-upgraded nodes could still have served them.
- **Derive readiness from live per-tenant storage health.** Rejected: acting on a probe affects every tenant on the node, so a live check for one tenant can withhold traffic for all of them, and it reproduces the restart cascades caused by the module-specific search-engine indicators.
- **Gate initialization on a cluster-wide storage health check.** Rejected: a cluster-wide answer is identical for every tenant, so one tenant's unassigned shards would degrade all tenants — the coupling this work removes — and it makes an additional storage privilege a hard startup requirement.

## Consequences

- A rolling upgrade takes as long as the slowest tenant's migration, because the upgraded node is withheld until every tenant has settled. In exchange no tenant is served 503s by a node that has not migrated it yet.
- A single-tenant node whose storage is unreachable now starts and reports not-ready, where it previously blocked startup. This refines 001 D2's "single-tenant clusters keep today's operational behavior": the Kubernetes-visible outcome is unchanged — no traffic is routed either way — while actuator, metrics and logs become available during the outage.
- A node without an embedded gateway now also waits for the barrier, where its schema initialization previously ran entirely in the background and startup never waited at all. The wait is one attempt long, and it buys a single startup path: the alternative is a second, gateway-less path that no multi-tenant deployment exercises.
- A fatal misconfiguration no longer crash-loops the pod. It presents as a permanently not-ready node with an ERROR log naming the tenant and cause, which is a quieter signal than a restart loop.
- No probe observes a storage outage that begins after a tenant was initialized. Requests to that tenant fail on the read path rather than being rejected as degraded.
- A tenant that stays degraded never advances its exporter position, so the logs of its partitions keep growing. Degradation has a disk cost with a long fuse.
- Until the module-specific search-engine readiness indicators are replaced by the unified indicator ([#51861](https://github.com/camunda/camunda/issues/51861)), a degraded *default* tenant still fails the readiness group, so isolation is observable only for non-default tenants. This ADR assumes that replacement lands and does not depend on it.
- The shared component must be able to express a tenant that is terminal from birth, because RDBMS builds each tenant's object graph once at startup and never rebuilds it lazily.
- While the barrier is held the management context has not started, so logs are the only startup diagnostic. Any failure releases the barrier within the storage client's connect or socket timeout, which bounds that window.

## Source

- [#57025 — Isolate per-tenant ES/OS schema initialization (no startup abort, background retry)](https://github.com/camunda/camunda/issues/57025)
- [#57007 — Physical tenants: Day 2 operations / management endpoints](https://github.com/camunda/camunda/issues/57007) (parent epic)
- [001 — Health, readiness, and status semantics for multi-physical-tenant clusters](001-physical-tenant-health-status-topology.md) (refined by this ADR)
- [#54299 — prevent cross-tenant RDBMS failure cascades](https://github.com/camunda/camunda/issues/54299)
- [#51861 — replace operate/tasklist search engine health indicators with unified indicator](https://github.com/camunda/camunda/issues/51861)

