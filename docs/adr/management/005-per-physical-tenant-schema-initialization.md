# Per-physical-tenant schema initialization: isolated, retried, gated on a serviceable tenant

**DRI**: Houssain Barouni

**Status**: Accepted (8.10)

**Deciders**
- Houssain Barouni
- Lena Schönburg
- Deepthi Akkoorath

**Purpose**: Define how a physical tenant's secondary-storage schema is initialized when one cluster hosts several tenants: what startup waits for, what "ready" asserts, and what happens to a tenant whose storage cannot be reached.

**Audience**: Engineers working on the distribution, the schema managers and the data layer; operators of multi-tenant clusters.

## Context

Schema initialization runs every tenant sequentially on one thread, with an unbounded retry *inside* each attempt. One tenant whose storage is unreachable starves every tenant behind it without ever throwing, and on a node with an embedded gateway it blocks the whole context refresh — for every tenant — for as long as the outage lasts. A failure that escapes takes the node down.

Both consumers of an initialized schema already tolerate an uninitialized tenant, per tenant: the exporter retries per partition, and v2 REST endpoints are rejected per request with `503` + `Retry-After`. Startup holds the context refresh for a different reason — the listening socket is the only admission control that works without an orchestrator, and it protects the ordinary case where initialization is merely unfinished.

That leaves two situations a readiness signal cannot separate: initialization progressing against healthy storage, where the wait is bounded and the port should stay shut; and a tenant's storage unreachable, where the wait is unbounded and holding the port serves nobody. The outcome is per-tenant isolation plus a startup **gate** that tells them apart.

### Tenant states

|      term       |                                                                                means                                                                                 |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **serviceable** | the schema described in the running code has been applied, so the tenant can be served                                                                               |
| **settled**     | the tenant produced at least one outcome — initialized, or failed once. Not a health claim, so it never opens the gate alone                                         |
| **trying**      | a task is still running that could still make this tenant serviceable                                                                                                |
| **terminal**    | a failure classified unrepairable by retrying; it stops that tenant's loop and is recorded, which is what D3's abort rests on                                        |
| **stopped**     | the task ended for any reason — success, terminal failure, exhausted retries, shutdown. Every way of stopping clears `trying`, but only *terminal* records a failure |
| **degraded**    | not serviceable: this tenant's requests are rejected while every other tenant is served                                                                              |

## Decision

**D1. Each tenant's schema initializes independently, and no single tenant's failure aborts the application context.**

One task per tenant, run concurrently, each retrying in the background until it succeeds; while the node can serve some tenant, or could still come to serve one, it stays up. On RDBMS this applies from two tenants up — a node with exactly one keeps today's synchronous fail-fast, which is what keeps a one-shot process such as `RestoreApp` terminating instead of retrying at the gate forever.

**D2. Only a node with an HTTP gateway holds startup. On RDBMS every node holds, gateway or not.**

The gate protects the HTTP surface — webapp, session store, v2 REST endpoints — which a gateway-less node does not serve, and it reuses `HealthConfigurationInitializer`'s condition so there is one rather than two that can drift. RDBMS is the exception because holding is not incidental to D3's abort, it *is* the abort: only a node that reaches the gate can raise it, so a gateway-less broker would otherwise come up and silently export nothing where today it exits non-zero.

**D3. The gate releases once every tenant has settled and either one is serviceable or none is still trying — except when nothing is serviceable because every tenant is terminal, which aborts startup.**

Requiring a *serviceable* tenant rather than a first outcome keeps a node whose storage is still starting from opening its port on the first connect timeout and then serving 503s; requiring it only while some tenant can still make progress keeps the gate off a condition nothing can satisfy.

|                             situation                              |                                         behaviour                                          |
|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| storage healthy, initialization or migration in progress           | held until every tenant completes, keeping a rolling-upgrade node out of the load balancer |
| one tenant's storage unreachable, another serviceable              | released; the degraded tenant keeps retrying in the background                             |
| one tenant failed, one serviceable, one still in its first attempt | held until the third settles too — no outcome yet is indistinguishable from migrating      |
| no tenant serviceable and every tenant still retrying              | held, retrying, exactly as a single-tenant node does today                                 |
| no tenant serviceable and every tenant terminal                    | startup aborts, the node exits non-zero, as it does today                                  |
| no tenant serviceable and none still trying, for any other reason  | released not-ready, with logs, metrics and actuator available for diagnosis                |

Every-tenant-terminal is the one state releasing cannot help: it is a diagnosis, not a transient state, and a node released into it would go on exporting into the schema the classification just refused. The other ways of stopping are not diagnoses — an exhausted budget is a configured give-up, a task that could not start is our own defect — so they release not-ready.

**D4. A tenant's readiness is a one-way latch asserting that the schema in the source code was applied — not that its storage is currently reachable.**

Initialization performs real reads and writes against the tenant's own indices, so success is authenticated, writable, tenant-scoped proof. Detecting runtime degradation is a separate mechanism, and this interface is its seam.

**D5. Failure classification decides whether a tenant keeps retrying, and through that whether the gate releases, aborts, or holds.**

Unrepairable failures stop that tenant's loop and log at ERROR; everything else retries and logs at WARN. The asymmetry sets the bar: a terminal failure called retryable holds a gateway node indefinitely, while a retryable failure called terminal costs one degraded tenant — or, where *every* tenant is misclassified that way, a crash loop instead of an outage the node would have retried through. That last cost is the largest and is new with D3's abort, so the bar is "certainly not repairable without operator action".

**D6. One storage-agnostic per-tenant component owns retry, state and observability; each schema manager exposes a single-attempt operation.**

Bounded transient retries stay inside an attempt, where they already live. The unbounded outer loop, the gate, per-tenant state and the transition logs live in the shared component, used by Elasticsearch/OpenSearch and RDBMS alike. Degradation is surfaced through its transition logs and the existing per-tenant readiness gauge, satisfying [001](001-physical-tenant-health-status-topology.md) D2 without a new state metric.

## Alternatives considered

- **Block startup until every tenant is ready.** Today's behaviour generalized. Rejected: one tenant's outage then withholds the entire node indefinitely, the defect this ADR removes.
- **No gate; let the readiness probe govern admission.** Rejected: readiness only withholds traffic where something consumes it, and a node reached directly — a browser against a single-node deployment, a load balancer with a TCP check — is served a broken webapp.
- **Come up not-ready when every tenant is terminal, rather than aborting.** Rejected, having first been decided: nothing about that state is transient, the failure is already in the container log, and the node would still export into the schema the classification refused.
- **Abort on every node, gateway or not.** Rejected for Elasticsearch/OpenSearch, realized for RDBMS. On the search engine a gateway-less node never reaches the gate, so aborting there needs asynchronous self-termination after the context refreshed — a heavier mechanism for a node whose exporter retries per partition by design. On RDBMS the same outcome is free, because such a node's context refresh already blocks on schema initialization.
- **Apply D1 to RDBMS whatever the tenant count.** Rejected: a restore job would hold at the gate and retry forever instead of exiting non-zero in seconds — and forcing it not to hold has it write exporter positions against a schema that may not exist yet.

## Consequences

- A rolling upgrade takes as long as the slowest tenant's migration. In exchange, no tenant is served 503s by a node that has not migrated it yet.
- A single-tenant node whose storage is unreachable behaves exactly as today, so 001 D2's single-tenant clause is met rather than refined.
- A node whose tenants are all terminally misconfigured aborts and crash-loops, as today; one such tenant among several no longer does.
- A node whose tenants all stopped for a reason that is *not* terminal comes up not-ready where it previously aborted. This is the one direction in which a node that used to crash stays up, and it is deliberate.
- A tenant that stays degraded never advances its exporter position, so its partitions' logs keep growing. Degradation has a disk cost with a long fuse.
- A tenant that asserts serviceability with no I/O at all satisfies the gate without proving anything, and one such tenant makes the gate vacuous for its still-migrating peers — `create-schema=false` on Elasticsearch/OpenSearch, `auto-ddl=false` on RDBMS. Both are left as they are so that the two storages agree.
- Until the search-engine readiness indicators are replaced by the unified indicator ([#51861](https://github.com/camunda/camunda/issues/51861)), a degraded *default* tenant still fails the readiness group, so isolation is observable only for non-default tenants.
- Mapping validation stays retryable until `IndexSchemaValidationException` is split, so a node whose only tenant cannot be migrated is held at startup rather than told why.
- Single-tenant RDBMS never retries in the background, where single-tenant Elasticsearch/OpenSearch always does. Closing that asymmetry needs a one-shot-job contract — "block until all settled, then fail if any failed" — that neither shape offers today.
- While the gate is held the management context has not started, so logs are the only startup diagnostic. That bites hardest on a gateway-less RDBMS node, where every tenant failing *retryably* now holds startup instead of dying, and an orchestrator reads the hold as a liveness failure rather than a not-ready pod.

## Deferred

- **Rolling-upgrade detection through cluster gossip.** Gossip already carries broker versions, so an upgrading node could require *all* its tenants serviceable before reporting ready, closing the window where a tenant that failed once is admitted mid-migration.
- **Storage-observed schema-version readiness** ([#51861](https://github.com/camunda/camunda/issues/51861)). Comparing the version in the tenant's metadata index against the running version is more truthful than D4's latch; it needs unreadable tenants ignored as a conjunct with 001 D2 rather than replacing it.
- **Harmonizing the hold-startup condition across the two storages.** Either RDBMS stops holding on gateway-less nodes and gains the asynchronous self-termination its abort would then need, or Elasticsearch/OpenSearch starts holding everywhere and gets the abort for free. Direction undecided.
- **A one-shot-job contract, plus operator guidance for back-to-back upgrades.** A partly-failed multi-tenant `StandaloneSchemaManager` run still exits 0, and upgrading again without first confirming every tenant is serviceable can produce terminal migration failures.

## Source

- [#57025](https://github.com/camunda/camunda/issues/57025) — isolate per-tenant Elasticsearch/OpenSearch schema initialization; [#60105](https://github.com/camunda/camunda/pull/60105) — implementation.
- [#54299](https://github.com/camunda/camunda/issues/54299) — the same isolation for RDBMS, which realizes D6's second adapter and narrows D1 and D2 as above; [#57007](https://github.com/camunda/camunda/issues/57007) — parent epic.
- [001 — Health, readiness, and status semantics for multi-physical-tenant clusters](001-physical-tenant-health-status-topology.md), implemented by this ADR.
- [#60888](https://github.com/camunda/camunda/issues/60888) — schema-manager retry configuration for both storages, which D6 currently hardcodes.

