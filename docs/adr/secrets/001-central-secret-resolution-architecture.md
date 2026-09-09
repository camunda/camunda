# Central secret resolution: placeholders on the log, values injected at hand-out, stores own the cache

**DRI**: Berkay Can

**Status**: Accepted (8.10)

**Deciders**
- Berkay Can
- Remco Westerhoud

**Purpose**: Record the architecture of centralized secret resolution as it is implemented: where a
`camunda.secrets.<name>` reference is detected, what travels on the log, which component reads a
secret store, how a value is cached, how a job waits and how a failure surfaces, and how all of it
is configured per physical tenant.

**Audience**: Engineers working on the engine's job activation path, the secret stores, the v2
secret endpoints or the cluster distribution; operators configuring a secret store; AI agents
reasoning about why a job is not handed out or why a secret value is not where they expected it.

## Context

Before this feature, a connector's credentials lived in the environment of the connector runtime.
Every runtime that might execute a connector needed every secret, secrets were rotated by
redeploying runtimes, and the cluster had no record of which process referenced which secret. The
goal is to let a process author write `camunda.secrets.<name>` in a model, keep the value in a store
the cluster owns, and hand the resolved value only to the worker that receives the job.

Four constraints shape every decision below.

1. **The stream processor must not block.** Resolution reads a file, an AWS Secrets Manager API or a
   GCP Secret Manager API. Doing that on the processing thread stalls the partition.
2. **A secret value must never become durable.** The log is replicated, snapshotted, replayed and
   exported. A value that reaches it is a value in backups, in secondary storage and in support
   bundles.
3. **A reference the engine resolves must never be taken from runtime data.** If a request-body
   variable or a JSON payload could name a secret, any user who can set a variable can exfiltrate
   any secret the cluster holds, because the engine path grants no permission per reference (D9).
   The gateway path takes the caller's own references and checks a permission on each instead.
4. **A cluster hosts several physical tenants.** Stores, credentials and caches belong to a tenant,
   and one tenant must never resolve against another tenant's store.

## Decision

### D1. Two resolution paths over one store abstraction

|      path       |                     who reads the store                      |                           what it resolves for                           |                     authorization                     |
|-----------------|--------------------------------------------------------------|--------------------------------------------------------------------------|-------------------------------------------------------|
| engine (broker) | `SecretResolutionScheduler` on the partition's IO task group | secret references recorded on a job, injected when the job is handed out | none per reference (see D9)                           |
| gateway         | `SecretServices`, synchronously in the request               | `POST /v2/secrets/resolve` and `POST /v2/secrets/list`                   | `SECRET:REVEAL` per reference, `SECRET:READ` for list |

Both go through the same `SecretStore` SPI and the same per-tenant `SecretStoreRegistry`. The engine
path exists because a job worker must receive a usable value without knowing anything about secrets.
The gateway path exists because a connector runtime evaluates its own expressions and needs to
resolve what those expressions referenced, under its own permissions.

The evaluate-expression endpoint bridges the two: it reports the references an evaluation touched
(`EvaluateExpressionResponse.getReferencedSecrets()`), and the caller then resolves them itself
through `/v2/secrets/resolve`. It reports only what the engine itself recognised as a reference,
never one a `JSON` cluster variable happened to spell out, see D2.

### D2. The engine resolves only references established at authoring or write time

`camunda.secrets.<name>` is recognised in exactly three places:

- **an input mapping source**, detected on the parsed FEEL AST rather than raw text
  (`SecretReference.parse`, reached only from `VariableMappingTransformer`) and pinned onto the
  element with the JSON pointer of the leaf it belongs to. A reference inside a string literal
  stays a literal, so a runtime value that merely looks like a reference is never resolved.
- **a `SECRET_REFERENCE`-kind cluster variable**, scanned once at write time
  (`ClusterVariableSecretReferenceScanner`) and pinned onto the record with the JSON pointer of the
  leaf it was found in. A `JSON`-kind cluster variable contributes nothing even if its value spells
  out a reference.
- **an expression evaluated through the expression endpoint**, where `ReferencedSecretCollector`
  records what the caller's own expression names, plus whatever a `SECRET_REFERENCE` cluster
  variable the expression read carried in. A `JSON`-kind variable contributes nothing here either.

The first two are provenance: the engine resolves a reference because of where it was written, and
a worker receives the value without holding any secret permission (D9). The third is not, and must
not be read as if it were. An expression endpoint caller names its own references, which is runtime
data by any measure, and what stands in for provenance there is authorization: the caller resolves
them through `/v2/secrets/resolve` under a per-reference `SECRET:REVEAL` check, and could have
called that endpoint directly with the same names. The collector's job on that path is narrower
than it looks, and is about the second source rather than the first: it stops an expression from
laundering an untrusted reference out of a `JSON` cluster variable and having it come back as a
reported one. Constraint 3 above is therefore the rule for the engine path. The gateway path
substitutes a permission check for it.

Two deploy-time validators keep authored references unambiguous and resolvable.
`SecretReferenceLiteralValidator` rejects a quoted reference in a source the engine may evaluate,
covering both a `zeebe:input` source and a `zeebe:property` value. A `zeebe:property` is never
scanned for references at all, so one written there resolves nowhere and would reach a worker as
raw text; the guard is what stops that from shipping silently.
`SecretReferenceLeafPrecisionValidator` rejects the shapes (a FEEL list literal, a context literal
produced by a branch) that would record the reference at an enclosing path instead of its own leaf
and therefore could never resolve. It runs on `zeebe:input` only, and covers those two shapes
rather than every way a FEEL expression can produce a container, with `JobSecretInjector`'s runtime
guard as the safety net for the rest.

The reference name is a single token of `[\p{Alnum}_-]+`, at most 256 characters. The same string is
the FEEL identifier, the authorization resource id and the store lookup key, never one of them
escaped or rewritten. Dots are excluded because FEEL reads them as path separators.

### D3. The log and the state carry the placeholder, the value goes only into the hand-out

Input-mapping evaluation resolves `camunda.secrets.token` to the literal text
`camunda.secrets.token` (`SecretReferenceEvaluationContext`), so the variable that lands in state is
the placeholder. The job record carries the references and their JSON pointers, not values. At
hand-out, the resolved value replaces the placeholder text on a response-only copy of the batch
(long poll, `JobSecretInjector`) or on the pushed job (`BpmnJobActivationBehavior#publishWork`).

Nothing else sees a value. `SecretReference` records are not projected into secondary storage: no
handler exists for them in the camunda or RDBMS exporters, and the Elasticsearch and OpenSearch
exporters fall through to `default -> false`. Incident messages name the placeholder and the JSON
pointer only, never the value and never the underlying failure's own message
(`JobSecretInjectionIncident`). No meter is tagged by secret name or value.

`SecretReferenceEvaluationContext` delegates rather than shadows: a real `camunda` variable keeps
precedence, and every other lookup (process variables, `camunda.vars.*`) is forwarded unchanged.

### D4. On activation the engine reads only what a store already holds, and parks on a miss

`LocallyCachedSecretStore.lookupLocal` is a memory-only read that must neither block nor fail. A job
whose references are all held is injected and activated. A job with a miss is skipped without
consuming a batch slot, so jobs behind it still activate, and one `RESOLUTION_REQUESTED` event is
appended per missing reference. Its applier records the reference as pending and parks the job in
`WAITING_FOR_SECRET_RESOLUTION` (see [zeebe ADR
0007](../../../zeebe/docs/adr/0007-810-job-waiting-for-secret-resolution-state.md)).

Two budgets bound the blast radius of a cold cache. The collector stops after
`MAX_UNCACHED_SECRET_JOBS_SKIPPED_PER_ACTIVATION` (100) skipped jobs and marks the batch truncated
so the client re-polls immediately, and every `RESOLUTION_REQUESTED` append is capacity-checked
against the record batch, with the references that do not fit left for the next activation rather
than overflowing and rolling back the whole command.

### D5. Resolution is a durable record chain on the partition, driven by a woken scheduler

`SecretResolutionScheduler` runs per partition on `AsyncTaskGroup.SECRET_RESOLUTION` (IO-bound), so
store IO never touches the processing thread. Each cycle reads up to `batch-resolution-limit` (20)
pending references from state, resolves them one batch per store through `resolveFromStore` (the
authoritative read, see D6), and writes `RESOLUTION_COMPLETE` or `RESOLUTION_FAIL` commands. Their
processors append `RESOLUTION_COMPLETED` or `RESOLUTION_FAILED` and, atomically with it, a
`BATCH_REACTIVATE_JOBS` or `BATCH_CREATE_INCIDENTS` command that drains the waiting jobs 100 at a
time, each batch chaining the next until the waiting index is empty.

Cadence is request-driven rather than a fixed poll. An activation that parked a job calls
`stayAwake()`, and a cycle that resolved anything or was woken since it last ran reschedules at
`wake-delay` (50ms) instead of the `interval` (5s); an idle cycle backs off geometrically. The flag
deliberately does not cancel and re-schedule a pending execution: under a sustained request rate the
chain reaches the fast cadence on its own, and cancel-and-reschedule at that frequency is an
indirection that already stalled a live partition once (see Alternatives).

Two failure modes are separated. A per-secret `Failed` result (`NOT_FOUND`, `ACCESS_DENIED`,
`INVALID_REF`) is permanent: `RESOLUTION_FAIL` immediately, no retry, no cache write. A
`SecretStoreUnavailableException` is transient: the whole store is retried with exponential backoff
(`retry-max-attempts` 3, `retry-initial-delay` 1s, `retry-max-delay` 30s, `retry-backoff-factor` 2),
and only after the attempts are exhausted does every pending reference of that store fail. Retry
state is in-memory per store id and deliberately resets on restart or failover.

A permanent failure, and an injection that fails at hand-out, both raise a `SECRET_RESOLUTION_ERROR`
incident on the job. The job stays parked while the incident stands, and resolving the incident is
what makes it activatable again (`makeActivatableAfterSecretResolution`), so a broken reference
cannot spin through repeated activations.

Pending references and the two waiting indexes are `PARTITION_LOCAL` column families
(`PENDING_SECRET_REFERENCES`, `SECRET_REFERENCES_BY_JOB`, `JOBS_BY_SECRET_REFERENCE`), so each
partition tracks and resolves its own references.

### D6. The store owns its cache, and the registry hands out nothing else

`SecretStoreRegistry` hands out only `LocallyCachedSecretStore`. A store that caches natively is
handed out as it is; any other store is wrapped in a `CachingSecretStore` with its own
`SecretCache`. A caller therefore resolves through a single `resolve` call and cannot read past the
cache by accident, and no caller drives a cache of its own.

Three reads, with different trust:

- `resolve(names)` is cache-first, and its cache-miss read never invalidates: it only ever reaches
  the store for a name the cache does not hold.
- `resolveFromStore(names)` ignores what is held and treats the store's answer as authoritative. It
  is the one read that clears a stale value, and only on a permanent failure code. `UNREADABLE` is
  transient and leaves the value in place rather than dropping a healthy secret over a read blip.
- `lookupLocal(name)` never reaches the store at all (D4).

The default cache is `CaffeineSecretCache`: `expireAfterWrite` at `camunda.secrets.cache.ttl`
(default 20m, minute granularity enforced) and `maximumSize` at `camunda.secrets.cache.max-size`
(default 1000). One instance per configured store, never shared: the cache is keyed by the bare
secret name, so a shared instance would let one store's value answer for another store's secret of
the same name, and the registry rejects that at construction. Expiry is driven by an injected
`InstantSource` wired to the actor clock, so `/actuator/clock` time travel reaches the cache and
tests advance time instead of sleeping.

The TTL is a staleness budget, not a correctness mechanism: a secret rotated in the store is picked
up within the TTL without a restart, and until then the previous value is still served.

### D7. One registry per physical tenant, built in `dist`, shared by the partitions and the local gateway

`SecretStoreConfiguration` builds one `SecretStoreRegistry` per physical tenant from that tenant's
effective configuration and publishes them as `SecretStoreRegistries`. The broker takes its tenant's
registry through `PhysicalTenantContext` down to every partition of that tenant, and
`CamundaServicesConfiguration` hands the same object to `SecretServices`. One JVM therefore holds
one registry, and one cache per store, per tenant: all partitions on that node and a co-located
gateway share it, and nothing is shared across nodes or across tenants.

Configuration lives under `camunda.secrets.*`, overridable per tenant at
`camunda.physical-tenants.<id>.secrets.*`; the engine scheduler is separate, under
`camunda.processing.engine.secrets.*`. Exactly one store per tenant is supported and its id must be
`default`, because `camunda.secrets.<name>` carries no store dimension and the store lookup is
exact, so a store under any other id could never be addressed. A tenant with no store configured
gets `NoopSecretStore`, which answers every name `NOT_FOUND`.

Configuration rules are checked before any store is constructed (store count, store id, cache
bounds), so an operator sees the configuration error rather than whatever an eagerly built AWS or
GCP client fails with first, and a failure partway through startup closes every store already built.
Cache meters are registered on a registry wrapped with the tenant tag, since a store id is unique
only within a tenant.

### D8. The SPI is a batched `resolve` plus `list`, with per-secret failures as values

```java
Map<String, SecretResolutionResult> resolve(Set<String> names);  // Resolved | Failed per name
List<String> list();
```

Per-secret failures are values (`SecretErrorCode` in `NOT_FOUND`, `ACCESS_DENIED`, `INVALID_REF`,
`UNREADABLE`), so one bad name never fails a batch. Store-wide failures are
`SecretStoreUnavailableException`, which is what the scheduler's retry-and-backoff reacts to.
`Resolved.toString()` prints `***`, and implementations must keep values out of messages and causes.
`SecretErrorCode` is switched over exhaustively without a `default`, because every store and every
caller ships from this repository in the same release, so the compiler enforces classifying a new
code everywhere.

Three implementations ship: a directory store (one file per secret, matching how Kubernetes projects
a Secret volume, with symlink containment inside the directory), AWS Secrets Manager (one secret per
reference, optionally batched, or every reference as a key in one JSON container secret), and GCP
Secret Manager (one by one, or the same JSON container mode). Adding a backend is a module plus one
binding in `SecretStoreConfiguration`; the per-tenant checks and the registration loop are
type-agnostic.

### D9. The engine path carries no per-reference authorization, the gateway path carries all of it

`SecretServices.resolve` checks `SECRET:REVEAL` per reference and `list` checks `SECRET:READ`
against the `SECRET` resource type; a denied reference is reported as an error inside the batch
rather than failing it. The engine path checks nothing: its secret reference processors are
`@ExcludeAuthorizationCheck`, and a worker receives an injected value without holding any secret
permission.

The trust boundary is deployment. A reference can only enter the engine path by being authored in a
model or in a `SECRET_REFERENCE` cluster variable (D2), both of which are already permissioned
writes, so the authority over which processes may use which secret is the authority over what may be
deployed. Re-checking at activation would authorize the worker, which is the wrong subject: the
worker did not choose the reference.

### D10. Observability is per store and per cycle, never per secret

`camunda.secret.cache.{result,evictions,size}` are tagged with the physical tenant and the store id
and are emitted only for a cache this codebase created (a natively caching store and the noop store
emit none, by design). `camunda.secret.resolution.{duration,outcome,cycle.error,cycle.delay}` come
from the engine and say whether a slow resolution is a slow store or a cache that is not holding
what callers ask for. No meter is tagged by secret name, which would be both unbounded cardinality
and customer data on the metrics endpoint.

## Alternatives considered

- **Resolve synchronously on the activation path.** Rejected. Activation runs on the stream
  processor; a file read or an AWS call there stalls the partition. The park-and-resolve loop is the
  price of keeping that thread free.
- **One broker-wide cache in front of all stores, wired separately from the stores.** Rejected in
  review and replaced by store-owned caching (#59191). A cache the caller drives lets a caller read
  past it, and a single cache keyed by bare secret name cannot keep two stores apart.
- **Warm the resolution at job creation for the long-poll path** (#61509, PR #61510). Implemented,
  A/B benchmarked twice, and not merged. With saturated pollers the head start is near zero; after
  the wake fix shortened the idle wait it moved p90/p99 by roughly 4%, which is smaller than the
  7-8% run-to-run noise measured in the same session, against a permanent doubling of cache lookup
  volume for every poll-eligible job with a reference.
- **Wake the scheduler by cancelling and re-scheduling the pending cycle** (#61389). Rejected. The
  cancel is deferred rather than synchronous, and doing it at request frequency stalled a live
  partition. The shipped design is a flag consulted by whichever cycle runs next, plus a short
  reschedule delay after a productive cycle.
- **Let a JSON cluster variable or a request variable name a secret.** Rejected as a secret
  injection vector. Only references the engine detected and pinned at write time are trusted.
- **Put the resolved value on the job record or export the resolution records.** Rejected. It would
  put secret values in snapshots, backups and secondary storage. Only the incident is externally
  visible.
- **Check `SECRET:REVEAL` on the engine path too.** Rejected, see D9: it authorizes the worker
  rather than the author.
- **Support several stores per tenant now.** Deferred, see below.

## Consequences

- **A cold cache costs the first activation of every reference.** The cache is in-memory and
  node-local, so a restart, a failover or a leader change starts empty and the first jobs for each
  reference are parked. Cluster warm-up shows an elevated activation latency that decays as the
  cache fills.
- **Resolution work is duplicated per partition.** Pending state is partition-local, so N partitions
  leading on one node resolve the same reference separately, though they then share the cache the
  store holds.
- **A rotated secret is served stale for up to the TTL**, and a deleted secret keeps working until
  its entry expires or a `resolveFromStore` observes the permanent failure and invalidates it.
- **A non-monotonic clock can delay expiry.** Caffeine expects a monotonic ticker; a clock stepped
  backwards can let an entry outlive its TTL. It can only delay expiry, never corrupt a value, and
  only a movement comparable to the TTL matters, which in practice means the controlled-clock tests
  rather than production.
- **Only the leaf a reference was authored at is injected; a placeholder copied onward stays
  literal.** Input mappings evaluate one after another, so a later mapping can read an earlier
  mapping's target and carry the placeholder text with it. In `t = camunda.secrets.token` followed
  by `copy = t`, deploy time recorded a reference at `/t` and nowhere else, so `/t` is injected and
  `copy` reaches the worker as the literal `camunda.secrets.token`. This follows from D2 rather
  than working around it: `copy`'s source is `t`, a variable, and a value that merely holds a
  reference is runtime data. Resolving it would mean resolving whatever a variable happens to spell
  out, which is the injection vector constraint 3 exists to exclude. A reference has to be written
  where its value is meant to be used. Nothing reports the difference, so a worker that receives a
  raw `camunda.secrets.<name>` has been handed a copy rather than an authored reference.
- **A new operator-facing failure surface exists**: `SECRET_RESOLUTION_ERROR` incidents, raised
  either by a permanent resolution failure or by a failed injection, both of which keep the job
  parked until the incident is resolved.
- **Downgrading a broker that used the feature is not possible**, in line with the 8.10 state
  compatibility rule: new column families (153 to 155), a new job state, a new value type and new
  intents.
- **Per-secret observability is deliberately absent.** An operator can see how many secrets a tenant
  holds and how often resolutions fail, but not which secret is failing, except through the incident
  on the affected job.
- **Adding a store backend is cheap** (a module plus one binding), but every store must be
  thread-safe and must classify its failures into the four error codes.

## Deferred

- **Several stores per tenant, and a store-qualified reference syntax.** The registry, the records
  and the state already key everything by store id, and `camunda.secrets.<name>` resolves against
  `default`. Only the configuration cap and the reference syntax stand in the way.
- **Parallel or pipelined store IO within a resolution cycle.** The cold-start transient is a
  scheduler throughput problem (how many distinct references one cycle can drain), which a
  serial-IO fix would attack directly.
- **Re-evaluating creation-time warming under a poll-starved load profile**, where a real gap
  between job creation and the first poll exists. No such scenario has been built.

## Source

- [EPIC: Centralized Secret Resolution via Zeebe (camunda/camunda#56555)](https://github.com/camunda/camunda/issues/56555)
- [Solution proposal (Google Doc)](https://docs.google.com/document/d/1yfsN_y75jEvdmJZs2OJ1srYHRyzXmCwFfEYPg2QIbik)
- [Centralized Secret Resolution via Zeebe, design doc (Google Doc)](https://docs.google.com/document/d/1gjrdnVucxyUwmBrCJ5u3ZX8Nby92HBVU8yluODwVrzw)
- [Connector Credential Manager (camunda/product-hub#3396)](https://github.com/camunda/product-hub/issues/3396)
- [Strong Tenant Isolation in Camunda 8 OC, Self-Managed (camunda/product-hub#3430)](https://github.com/camunda/product-hub/issues/3430)
- [Job state for jobs waiting for secret resolution (zeebe ADR 0007)](../../../zeebe/docs/adr/0007-810-job-waiting-for-secret-resolution-state.md)
- [Detect missing secret references on activation and emit RESOLUTION_REQUESTED (camunda/camunda#57846)](https://github.com/camunda/camunda/issues/57846)
- [Background task: resolve pending secrets from the secret store (camunda/camunda#57848)](https://github.com/camunda/camunda/issues/57848)
- [Async secret resolution at job activation, job push (camunda/camunda#56564)](https://github.com/camunda/camunda/issues/56564)
- [Make the secret store own caching and resolving (camunda/camunda#59191)](https://github.com/camunda/camunda/issues/59191)
- [Caffeine-backed secret cache with TTL and bounded size (camunda/camunda#59314)](https://github.com/camunda/camunda/issues/59314)
- [Wake the secret resolution scheduler on request instead of polling (camunda/camunda#61389)](https://github.com/camunda/camunda/issues/61389)
- [Warm secret resolution at job creation for the long-poll path, not merged (camunda/camunda#61509)](https://github.com/camunda/camunda/issues/61509)
- [Detect cluster-variable references in input mappings (camunda/camunda#58930)](https://github.com/camunda/camunda/issues/58930)
- [Scan and store secret references in SECRET_REFERENCE cluster variables (camunda/camunda#58931)](https://github.com/camunda/camunda/issues/58931)
- [Gateway REST API: POST /v2/secrets/list (camunda/camunda#56568)](https://github.com/camunda/camunda/issues/56568)
- [Wire the secret store into the gateway (camunda/camunda#58784)](https://github.com/camunda/camunda/issues/58784)
- [Identity/Admin: grant SECRET permissions (camunda/camunda#56570)](https://github.com/camunda/camunda/issues/56570)
- [Report referenced secrets from the expression evaluation endpoint (camunda/camunda#60384)](https://github.com/camunda/camunda/issues/60384)
- [Revisit file-based secret store (camunda/camunda#57199)](https://github.com/camunda/camunda/issues/57199)
- [ADR: central secret resolution architecture (camunda/camunda#56572)](https://github.com/camunda/camunda/issues/56572)

