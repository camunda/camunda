# ADR-0006: Propagating Physical-Tenant Context Across Async Authorization Reads

## Status

Proposed

> Supersedes the execution-context premise of [ADR-0005](./0005-physical-tenant-routing-of-authorization-reads.md) §A.1 — specifically the claim that membership "is resolved once at authentication time and persisted into `CamundaAuthentication`", and Alternative #1's conclusion that a thread-local PhysicalTenantContext "is correct for the request-scoped reads (membership + control-plane)". The rest of ADR-0005 — routing the authorization layer per physical tenant, the instance-bound data-plane read (§B), and CSL physical-tenant-agnosticism — stands unchanged.

## Deciders

- Sebastian Bathke ([@megglos](https://github.com/megglos)) — proposer
- Ben Sheppard ([@Ben-Sheppard](https://github.com/Ben-Sheppard))
- Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))

## Context

ADR-0005 routes request-scoped authorization reads (identity & membership resolution, the control-plane permission check) to the in-context physical tenant via `PhysicalTenantContext.current()`. `current()` reads the PT from the request — it is stamped onto the Spring `RequestContextHolder` request attributes by ADR-0003's pre-security filter, and it **throws `IllegalStateException` when there is no request scope on the current thread**.

ADR-0005 §A.1 justified `current()` being sufficient for these reads with this premise:

> "Memberships are resolved once at authentication time and persisted into `CamundaAuthentication` … so this single request-time resolution is sufficient."

That premise does not hold against the implementation. Membership is **lazy** (introduced in #53759): `CamundaAuthentication` carries `Supplier`-backed group/role/tenant/mapping-rule lists that resolve on **first access**, not at authentication time. First access happens inside the authorization check of a read — and several read services offload that read to an **async executor thread**:

```java
// UsageMetricsServices.search(...) — request thread builds the security context, then:
CompletableFuture.supplyAsync(() ->                 // common ForkJoinPool
    authClient.usageMetricStatistics(query));        // the ResourceAccessController (RAC) fires the lazy membership supplier HERE
```

The chain that fails:

```
[http-nio request thread] builds CamundaAuthentication (lazy suppliers, PT in request scope)
  → CompletableFuture.supplyAsync(...)               // hops to a ForkJoinPool worker thread
    → CamundaSearchClients read → ResourceAccessController → AuthorizationChecker
      → LazyList.resolve() → membership supplier → DefaultMembershipService.groupIds()
        → PhysicalTenantContext.current()            // no request scope on the worker → throws
```

This surfaced when #55440 replaced `DEFAULT_PHYSICAL_TENANT_ID` with `current()` in `DefaultMembershipService`. On `main` today the async reads work only because membership still uses the hardcoded `"default"` constant; the PT-routing change is what exposes the gap. It is not a pre-existing bug — it is a missing piece of the ADR-0005 design.

Scope of the gap (from a monorepo audit of async read offloads):
- **Authorized-read offloads that resolve membership:** `ResourceServices.fetchFromSecondaryStorage()` (uses the managed `ApiServicesExecutorProvider` executor) and `UsageMetricsServices.search()` (uses a bare `CompletableFuture.supplyAsync` on the common ForkJoinPool, bypassing the managed executor).
- **Not affected:** broker-request response handlers and `RequestExecutor` (response mapping, not RAC reads); document-store paths (no RAC); and — confirmed — the **engine/exporter actor threads do not call membership or RAC reads**, so this is distinct from ADR-0005 §B's instance-bound data-plane read.
- **No existing context-propagation mechanism** exists (no `TaskDecorator`, `DelegatingSecurityContextExecutor`, inheritable request attributes, or context-snapshot wrapper). The Spring Security context is *not* relied upon on these threads — authorization is carried explicitly via `securityContextProvider.provideSecurityContext(...)` + `client.withSecurityContext(...)`. The *only* request-scoped state missing on the worker thread is the PT.

## Decision

**Adopt context propagation: the in-context physical tenant must be carried across the request→async-executor boundary, so `PhysicalTenantContext.current()` resolves on the worker thread that runs an authorization read.** `current()` remains the resolution mechanism for request-scoped reads; ADR-0005 §A is corrected to read "request-*initiated* reads" rather than "reads that execute on the request thread."

Concretely:

1. `PhysicalTenantContext` gains a thread-bound (propagated) tenant that `current()` falls back to when no request scope is present, plus a non-throwing `currentOrNull()`.
2. Introduce a `PhysicalTenantPropagatingExecutorService` decorator that, at task-submission time, captures the submitting thread's tenant **by value** (`currentOrNull()`) and binds that value on the worker thread for the duration of the task, clearing it afterwards.
3. Apply it at the single chokepoint `ApiServicesExecutorProvider.getExecutor()`, so every `ApiServices`-based async read inherits propagation; route `UsageMetricsServices.search()`'s bare `supplyAsync` calls through that managed executor too (otherwise the chokepoint misses them).
4. Capturing the tenant **by value** — rather than re-binding the live servlet `RequestAttributes` — makes propagation independent of the servlet request lifecycle. This is necessary, not merely cleaner: not all offloads are join-style. `UsageMetricsServices.search()` joins its futures, but gateway REST endpoints return the `CompletableFuture` to Spring MVC async dispatch, where the servlet request can already be inactive when the worker runs — re-binding the live attributes there throws `"Cannot ask for request attribute - request is not active anymore"`. The value-capture approach is correct for both. (Confirmed empirically: the PoC on #55614 first failed exactly this way with live-attribute re-binding, then passed once switched to value capture.)

This preserves the lazy-membership optimization (#53759) — membership still resolves only when an authorization check needs it — and generalises: any future request-scoped state needed by async reads rides the same rail.

The data-plane read (ADR-0005 §B) is unchanged: it stays instance-bound to the executing `CamundaSearchClients`'s PT, because it also runs in the engine where no request (and no propagated request scope) exists.

## Consequences

- Async-offloaded authorization reads resolve the correct PT, so the per-physical-tenant membership and control-plane reads work end-to-end. Single-PT deployments are unaffected (the propagated PT is `default`).
- A new, small piece of shared infrastructure (the propagating executor) must be maintained, and **every** async offload of an authorization read must go through the managed executor — a bare `supplyAsync` that bypasses it silently reintroduces the bug. This constraint is guarded in layers: an ArchUnit rule banning the executor-less `CompletableFuture.supplyAsync`/`runAsync` overloads in the `service` module (forcing every offload to pass an executor — exactly the bug class `UsageMetricsServices` had); per-read integration tests that exercise each authorized async read under a non-default physical tenant; and `current()`'s fail-fast as a runtime backstop that turns a missed site into a loud failure rather than a silent `default` read. The single chokepoint (`ApiServicesExecutorProvider`) plus review ensures the executor passed is the propagating one — which no static rule can prove.
- `current()` keeps its fail-fast contract (it still throws when neither a request scope nor a propagated scope is present), so genuinely context-less callers (e.g. engine/exporter actor threads) continue to fail loudly rather than silently reading `default`.
- The lazy-membership optimization is retained.

## Alternatives Considered

1. **Eagerly materialise membership on the request thread.** Force the lazy suppliers to resolve at authentication time on the request thread — making reality match ADR-0005's original wording — so the async read only ever reads already-materialised lists and never calls `current()`. *Simpler* (no executor infrastructure, no propagation to maintain, no "must use the managed executor" constraint), but it **gives up the #53759 lazy optimization**: every authenticated request resolves group/role/tenant/mapping-rule membership from secondary storage even when no authorization check needs it. Viable if the deciders judge the lazy optimization not worth the propagation machinery. **This is the primary trade-off to align on: keeping lazy membership with context propagation (the decision above) vs. eager materialisation (this alternative).**

2. **Thread the PT through CSL** — capture `current()` when the supplier closure is built on the request thread and pass it into `MembershipPort`. Rejected: it requires a PT parameter on the CSL `MembershipPort` API, breaking the CSL physical-tenant-agnosticism that ADR-0005 commits to.

3. **Bind the thread-bound tenant explicitly at each offload site** (e.g. an `executeWithPhysicalTenant(...)` wrapper callers invoke). The decision already adds the thread-bound fallback to `PhysicalTenantContext`; what is rejected here is requiring every offload site to remember to wrap its task. The executor-decorator applies that binding at one chokepoint instead, so a new offload that uses the managed executor is covered automatically — only a bare `supplyAsync` that bypasses the executor (as `UsageMetricsServices` did) needs attention, which a test/review guards.

4. **A dedicated `ApiServicesExecutorProvider` per physical tenant**, wired into that tenant's scoped services, each provider pinning its own `physicalTenantId` onto its threads — so threads inherently carry their tenant and neither propagation nor clean-up is required. Attractive, and potentially a lever against noisy-neighbour effects (per-tenant pool isolation). Rejected for now on sizing complexity: each provider derives its pool size from the node's `availableProcessors`, so N providers multiply the thread count by N. Dividing that budget across tenants either over-utilises (small divisor) or limits each tenant unnecessarily (large divisor), and only works at all at small PT cardinality. Worth revisiting specifically if per-tenant isolation/fairness becomes a hard requirement.

## References

- ADR-0005 — Physical-Tenant Routing of the Authorization Layer (superseded in part; see Status)
- ADR-0003 — pre-security physical-tenant filter (stamps the PT onto the request)
- #53759 — lazy per-field membership resolution
- #55440 — the change that exposed the gap
- #55252 — Physical-Tenant authorization routing (epic)
