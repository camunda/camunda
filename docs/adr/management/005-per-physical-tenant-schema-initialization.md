# Per-physical-tenant schema initialization: isolated, retried, gated on a serviceable tenant

**DRI**: Houssain Barouni

**Status**: Proposed (8.10)

**Deciders**
- Houssain Barouni
- Lena Schönburg
- Deepthi Akkoorath

**Purpose**: Define how a physical tenant's secondary-storage schema is initialized when one cluster hosts several tenants: what startup waits for, what "ready" asserts, and what happens to a tenant whose storage cannot be reached.

**Audience**: Engineers working on the distribution, the schema managers and the data layer; operators of multi-tenant clusters.

## Context

Schema initialization runs every tenant sequentially on one thread, with an unbounded retry *inside* each attempt. One tenant whose storage is unreachable starves every tenant behind it without ever throwing, and on a node with an embedded gateway it blocks the whole context refresh — for every tenant — for as long as the outage lasts. A failure that escapes takes the node down.

Both consumers of an initialized schema already tolerate an uninitialized tenant, per tenant: the exporter refuses to open and retries per partition; v2 REST endpoints needing secondary storage are rejected per request with `503` + `Retry-After`. Neither needs startup to hold the context refresh.

Startup holds it for a different reason: the listening socket is the only admission control that works without an orchestrator, and it protects the ordinary case where nothing is wrong and initialization is merely unfinished. A browser reaching a node in that state loads the webapp, gets 503 from every secondary-storage endpoint, and its login misbehaves rather than erroring, because the session write is skipped while the tenant is uninitialized.

Two situations a readiness signal cannot separate therefore have to be: initialization progressing against healthy storage, where the wait is bounded by the work and the port should stay shut; and a tenant's storage unreachable, where the wait is unbounded and holding the port serves nobody. The outcome is per-tenant isolation plus a startup **gate** that discriminates between them.

### Tenant states

|      term       |                                           means                                            |
|-----------------|--------------------------------------------------------------------------------------------|
| **serviceable** | the schema described in the running code has been applied, so the tenant can be served     |
| **settled**     | the tenant produced at least one outcome — initialized, or failed once. Not a health claim |
| **trying**      | a task is still running that could still make this tenant serviceable                      |
| **terminal**    | a failure classified unrepairable by retrying; it stops that tenant's loop and is recorded |
| **stopped**     | the task ended, for any reason — success, terminal failure, exhausted retries, shutdown    |
| **degraded**    | not serviceable: this tenant's requests are rejected while every other tenant is served    |

Two distinctions carry the design. *Settled* is not *serviceable*, so settling never opens the gate alone — a tenant that settles by failing is still trying, and only another tenant being serviceable can release it. And *stopped* is not *terminal*: every way of stopping clears `trying` alike, but only a terminal classification records a failure, which is what D3's abort rests on.

## Decision

**D1. Each tenant's schema initializes independently, and no single tenant's failure aborts the application context.**

One task per tenant, run concurrently, each retrying in the background until it succeeds. While the node can serve some tenant, or could still come to serve one, it stays up.

**D2. Only a node with an HTTP gateway holds startup. A node without one does not wait at all.**

The gate protects the HTTP surface — webapp, session store, v2 REST endpoints — which a node without an HTTP gateway does not serve; its exporter already retries per partition. The condition is the one `HealthConfigurationInitializer` already uses for the schema readiness indicator, so there is one condition rather than two that can drift.

**D3. The gate releases once every tenant has settled and either one is serviceable or none is still trying — except when nothing is serviceable because every tenant is terminal, which aborts startup.**

Requiring a *serviceable* tenant rather than a first outcome keeps a node whose storage is still starting from opening its port on the first connect timeout and then serving 503s. Requiring it only while some tenant can still make progress keeps the gate off a condition nothing can satisfy, which would hang startup silently and forever.

|                             situation                              |                                         behaviour                                          |
|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| storage healthy, initialization or migration in progress           | held until every tenant completes, keeping a rolling-upgrade node out of the load balancer |
| one tenant's storage unreachable, another serviceable              | released; the degraded tenant keeps retrying in the background                             |
| one tenant failed, one serviceable, one still in its first attempt | held until the third settles too — no outcome yet is indistinguishable from migrating      |
| no tenant serviceable and every tenant still retrying              | held, retrying, exactly as a single-tenant node does today                                 |
| no tenant serviceable and every tenant terminal                    | startup aborts, the node exits non-zero, as it does today                                  |
| no tenant serviceable and none still trying, for any other reason  | released not-ready, with logs, metrics and actuator available for diagnosis                |

Node readiness is unchanged throughout — it stays "at least one serviceable tenant" per [001](001-physical-tenant-health-status-topology.md) D2, so a node serving some tenants and not others reports ready and refuses the rest per request. Every tenant terminal is the one state releasing cannot help: it is a diagnosis rather than a transient state, and a node released into it serves nothing, becomes ready never, and — because the gate withholds the HTTP surface and not the engine — goes on exporting into the schema the classification just refused. The other ways of stopping are not diagnoses: an exhausted retry budget is an operator's configured give-up, a task that could not start is our own defect. The abort belongs to the gate, so by D2 only nodes holding one reach it.

**D4. A tenant's readiness is a one-way latch asserting that the schema in the source code was applied — not that its storage is currently reachable.**

Initialization performs real reads and writes against the tenant's own indices, so success is authenticated, writable, tenant-scoped proof. Detecting runtime degradation is a separate mechanism, and this interface is its seam.

**D5. Failure classification decides whether a tenant keeps retrying, and through that whether the gate releases, aborts, or holds.**

Unrepairable failures stop that tenant's loop and log at ERROR; everything else retries and logs at WARN. Calling a terminal failure retryable holds a gateway node at the gate indefinitely; calling a retryable failure terminal costs one degraded tenant — or, where *every* tenant is misclassified that way, a crash loop instead of an outage the node would have retried through. That cost is new with D3's abort and the largest, so the bar is "certainly not repairable without operator action".

**D6. One storage-agnostic per-tenant component owns retry, state and observability; each schema manager exposes a single-attempt operation.**

Bounded transient retries stay inside an attempt, where they already live. The unbounded outer loop, the gate, per-tenant state and the transition logs live in the shared component, used by Elasticsearch/OpenSearch and RDBMS ([#54299](https://github.com/camunda/camunda/issues/54299)) alike. Degradation is surfaced through its transition logs and the existing per-tenant readiness gauge, satisfying 001 D2 without a new state metric.

## Consequences

- A rolling upgrade takes as long as the slowest tenant's migration. In exchange, no tenant is served 503s by a node that has not migrated it yet.
- A single-tenant node whose storage is unreachable behaves exactly as today, so 001 D2's single-tenant clause is met rather than refined.
- A tenant that fails once during a rolling upgrade has settled, so the node can be admitted while that tenant is still migrating, and its requests are rejected meanwhile.
- A node whose tenants are all terminally misconfigured aborts and crash-loops, as today; one such tenant among several no longer does.
- A node whose tenants all stopped for a reason that is *not* terminal comes up not-ready where it previously aborted. This is the one direction in which a node that used to crash stays up, and it is deliberate.
- While the gate is held the management context has not started, so logs are the only startup diagnostic — as today.
- A tenant that stays degraded never advances its exporter position, so its partitions' logs keep growing. Degradation has a disk cost with a long fuse.
- Until the search-engine readiness indicators are replaced by the unified indicator ([#51861](https://github.com/camunda/camunda/issues/51861)), a degraded *default* tenant still fails the readiness group, so isolation is observable only for non-default tenants.
- The shared component must be able to express a tenant terminal from birth, because RDBMS builds each tenant's object graph once at startup.

## Alternatives considered

- **Block startup until every tenant is ready.** Today's behaviour generalized. Rejected: one tenant's outage then withholds the entire node indefinitely, the defect this ADR removes.
- **No gate; let the readiness probe govern admission.** Rejected: readiness only withholds traffic where something consumes it, and a node reached directly — a browser against a single-node deployment, a load balancer with a TCP check — is served a broken webapp.
- **Release each tenant's share on its first outcome alone.** Rejected: the Elasticsearch connect timeout is one second, so a node started alongside its storage releases a second in and serves 503s until the storage boots.
- **Come up not-ready when every tenant is terminal, rather than aborting.** Rejected, having first been decided: nothing about that state is transient, the failure is already in the container log, and the node would still export into the schema the classification refused.
- **Abort on every node, gateway or not.** Rejected for now: a node without a gateway never reaches the gate, so it needs asynchronous self-termination after the context refreshed — a heavier mechanism for a node whose exporter retries per partition by design.
- **Bound the wait with a grace period or a plain timeout.** Rejected: a grace period only matters when the failing tenant settles last, exactly where waiting for a serviceable tenant is simpler; a timeout also abandons legitimate long migrations.
- **Derive readiness from live per-tenant or cluster-wide storage health.** Rejected: acting on a probe affects every tenant on the node, so one tenant's check can withhold traffic for all — the coupling this work removes. A cluster-wide check also makes an extra storage privilege a hard startup requirement.

## Future improvements

- **Rolling-upgrade detection through cluster gossip.** Gossip already carries broker versions, so an upgrading node could require *all* its tenants serviceable before reporting ready, closing the window where a tenant that failed once is admitted mid-migration. New-version nodes only, or no node ends up ready.
- **Storage-observed schema-version readiness ([#51861](https://github.com/camunda/camunda/issues/51861)).** Comparing the version in the tenant's metadata index against the running version is more truthful than D4's latch. Needs unreadable tenants ignored as a conjunct with 001 D2's readiness rule rather than replacing it, the same predicate backing request-time rejection, and the result latched once confirmed.
- **Splitting `IndexSchemaValidationException` before classifying any part terminal.** A changed field type is unrepairable, but an ambiguous mapping is what a peer mid-migration transiently looks like. Until then validation stays retryable, so a node whose only tenant cannot be migrated is held at startup, as today.
- **The RDBMS retry cap.** RDBMS gives up after three attempts where Elasticsearch/OpenSearch retry unboundedly. Acknowledged as an inconsistency and left unchanged; whether the cap is intentional is still to be verified.
- **The one-shot `StandaloneSchemaManager` job's contract.** The gate aborts on *every* tenant terminal, whereas a job wants "block until all are done, then fail if any failed" — so a partly-failed multi-tenant job still exits 0.
- **Operator guidance for back-to-back upgrades.** Upgrading again without first confirming every tenant is serviceable can produce terminal migration failures.

## Source

- [#57025](https://github.com/camunda/camunda/issues/57025) — isolate per-tenant ES/OS schema initialization; [#60105](https://github.com/camunda/camunda/pull/60105) — implementation; [#57007](https://github.com/camunda/camunda/issues/57007) — parent epic.
- [001 — Health, readiness, and status semantics for multi-physical-tenant clusters](001-physical-tenant-health-status-topology.md), implemented by this ADR.

