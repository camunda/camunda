# Job lease: an opaque per-activation fencing token

**DRI**: Nico Korthout

**Status**: Accepted (8.10)

**Purpose**: Defines the job lease — what the engine guarantees about the token, which commands it
fences, and how it behaves across dispatch, rolling upgrades, and secondary storage.

**Audience**: Zeebe engineers working on job processing, the gateway, or clients, and AI agents
reasoning about job activation, fencing, or the agent-history commit lifecycle.

## Context

A job that fails or times out is re-activated by another worker while the original worker may still
be running. Work products of the superseded activation — e.g. the AI Agent Connector's incremental
conversation-history reports — then arrive interleaved with the new activation's, and the engine
cannot tell which activation's items should commit when the job completes. The race has been known
since 2020; all prior mitigations were deadline-based (job timeout, `UpdateJobTimeout`), never
identity-based, and some customers already bridge the gap by abusing timeout updates as ownership
probes.

The job lease closes this gap: an opt-in (`withLease`), engine-generated opaque token identifying
one activation of one job. At most one live lease exists per job at any instant; the lease resolves
*temporal staleness* between activations — it does not arbitrate concurrent physical execution,
which remains the worker's responsibility (at-least-once delivery is unchanged). It is a fencing
token, not an authorization token: it fences races, not malice. Any principal with job-read plus
job-update permission can forge fenced commands — consistent with existing authorization, where
those permissions already allow completing arbitrary jobs by key.

## Decision

**D1. The lease is a random opaque token; clients may rely on presence and equality only.**
Generated per leased activation. Guarantees: unique across activations of the same job; collisions
do not occur in practice (sufficient entropy; the engine does not verify inequality). Opacity is
deliberate: the token carries no internal state, so clients cannot construct, compare, or interpret
tokens, and the engine stays free to change how tokens are produced. The per-job uniqueness
guarantee is intentionally minimal — it is exactly what the fence needs, nothing more.

**D2. Replay safety by write-to-event.**
The token is generated exactly once, at command-processing time, and written into the `ACTIVATED`
event; appliers persist only what the event carries. This is what makes nondeterministic generation
safe in a deterministic engine: replay re-applies the recorded event and restores the identical
token, so deterministic *derivation* is not required — the insight that freed the token from
encoding internal state (D1). The token must never be derived or re-generated anywhere else; any
second generation site would break replay.

**D3. The lease is monotonic for the life of the job.**
Retained when the job fails or times out (the job returns to activatable with the token stored),
advanced only by a leasing re-activation in the same `ACTIVATED` event, removed only when the job
is deleted. Each of the three properties closes a specific hole: *retain on release* — clearing on
timeout would leave every timed-out job unfenced until re-activation; *skip on non-leasing
activation* — otherwise a non-leasing worker could re-hand-out a leased job as an unfenced
activation for a stale command to race; *overwrite on leasing activation* — advancing the token in
the same event that re-hands-out the job leaves no empty-lease window. All other engine paths
(migration, modification, incident resolution) preserve or delete the lease; none re-hands-out a
leased job outside a leasing activation.

**D4. The fence covers worker lifecycle transitions; property updates validate a lease only if
supplied.**
Complete, fail, and throw-error on a leased job require the matching token (`INVALID_STATE`
otherwise, non-retryable) — these are the commands that commit or discard an activation's work,
which is precisely what staleness corrupts. Property updates (update-timeout, update-retries,
priority, generic/bulk `UPDATE`) never require a lease: they are recoverable, commit no work, and
are issued by workers and operators through the same commands, so a hard fence would break operator
rescue with no way to tell the actors apart. A *supplied* lease on a property update must match,
which preserves the worker's ownership probe: a rejected timeout extension is a definitive "you
have been superseded" signal. Engine-internal timeout and yield are never fenced — they *are* the
supersession mechanism, so there is nothing to match against; cancel likewise acts from outside the
worker protocol.

**D5. Leasing activations match all jobs; non-leasing activations match only unleased jobs.**
Activation is a batch operation, so a leased job must not fail the activation of unrelated jobs: a
non-leasing activation silently *skips* leased jobs (a per-job filter, not a rejection), on both
the poll and push paths; on push, a leased job with no matching leasing stream is demoted to the
notify path. Because the skip is silent, starvation under mixed fleets would otherwise have no
error surface — so skips and demotions increment a shared lease-skip metric tagged by path and job
type, letting operators distinguish "no jobs available" from "jobs invisible to this worker".

**D6. Once leased, a job is permanently lease-only; there is no un-lease command.**
Serving a leased job to a non-leasing worker would create an unfenced activation that a stale
command could race — the exact hole the fence closes — so the restriction follows directly from
D3's skip property. An un-lease command is deferred as not yet needed (not as unsound): adequate
rescue paths already exist for stranded leased jobs (e.g. after a leasing fleet is rolled back) —
re-activation by any leasing worker, or process-instance modification, where terminating the stuck
element and activating it again yields a fresh, unleased job. User docs recommend a homogeneous
fleet per job type.

**D7. Version skew is handled by a documented worker-side null check — the sole mechanism.**
`withLease` degrades silently over gRPC against older brokers or gateways (proto3 drops the
unknown field; REST rejects with 400). Rejecting the unknown field on gRPC, as REST does, is not
attainable for this boundary: the rejection would have to be performed by the older,
already-released server, whose ignore-unknown-fields behavior is fixed — and adopting
reject-unknown-fields going forward would trade proto3's rolling-upgrade tolerance for a permanent
availability cliff on every future field addition. Only the worker application knows whether the
fence is *required* or merely *preferred* — so the contract sits with the worker: check
`leaseToken` per activated job and, when absent and the fence is required, fail the job with a
backoff while preserving retries (a decremented retry would burn a transient infrastructure state
into an incident). This converges as partition leaders upgrade; against a permanently old cluster
it manifests as a visible fail loop rather than silent unfenced execution — the intended,
observable failure mode. The same contract covers broker-side skew during a rolling update:
leadership can move to a not-yet-upgraded broker after a newer leader has issued leases; such a
broker ignores the token in commands and re-hands-out jobs without one, so the null check fires on
those activations and enforcement degrades at worst to pre-lease behavior — never to spurious
rejections — until the update completes. No client-API affordance and no gateway-side capability
filtering are built: both would be permanent surface for a strictly-decaying one-version-boundary
condition. Two thinner layers align with standard practice: older log records deserialize with an
empty lease, and exporters strip the token from previous-version job records so the previous
minor's strict indices keep accepting documents.

**D8. The token is exported to secondary storage; surfacing it in the Get/Search job APIs is the
intended end state.**
The token is not a secret (per the threat model in Context), so exporting it costs nothing
security-wise and buys root-causing value — in particular, correlating lease-skip metrics (D5) to
the specific job an operator is investigating, and keeping the query APIs complete relative to
what Activate Jobs returns. Query APIs are eventually consistent — the fence is enforced only
against the engine's authoritative stored lease, never the exported copy. The token travels on
the exported per-job lifecycle events; activation itself is not an export point (job-batch
records are deliberately not exported, for volume), so a live lease becomes visible only
retroactively, from the job's next exported event. That covers the dominant
retroactive-investigation cases; seeing the lease immediately after activation is accepted as a
gap until the job APIs surface activation data at all.

## Alternatives considered

- **Encoded `(jobKey, activationCount)` token with an exposed activation counter.** Leaks internal
  state to clients that don't need it, lets clients construct and compare tokens, and cannot
  evolve without a protocol break. Deterministic derivation is also unnecessary for replay
  (see D2).
- **Expire the lease on timeout.** Reopens the hole the fence closes: every timed-out leased job
  would sit unfenced until re-activation. The deadline decides when the engine may re-hand-out the
  job; supersession happens at re-activation, and a persisting lease preserves benign late
  completion.
- **Change-set-aware fencing with a `batchOperationReference` bulk bypass** (the superseded first
  enforcement design: update-timeout hard-fenced, generic `UPDATE` fenced iff its changeset
  touched timeout, bulk operations exempted via internal command metadata). Actor-ambiguous on the
  generic `UPDATE` and dependent on an internal-metadata escape hatch; replaced by the
  lifecycle/property principle in D4, under which operators and bulk operations need no mechanism
  at all.
- **Client- or gateway-side version-skew mechanisms** (a `requireLease` client option; gateway
  capability filtering by broker version). Permanent API or dispatch surface guarding a
  one-version-boundary condition whose relevance strictly decays — and neither removes the
  documented null-check contract, since an old gateway drops the flag before any new-side logic
  runs.

## Consequences

- A job type served by a mix of leasing and non-leasing workers can starve leased jobs with no
  error surface; the lease-skip metric is the operator signal, and homogeneous fleets per job type
  are the recommendation.
- Workers must treat a lease-mismatch rejection as the expected outcome of losing an activation
  race — drop the work, log at debug — not as an error.
- At-least-once delivery and worker-side idempotency obligations are unchanged; the lease adds
  activation identity, not exactly-once execution.
- The lease applies uniformly to all job kinds (service tasks, execution/task listeners, ad-hoc
  sub-process jobs) and is orthogonal to logical and physical multi-tenancy.
- The first consumer is the agent-history commit lifecycle; the mechanism is generic and carries
  no AI-specific semantics.

## Source

- [Introduce job lease as a fencing token for activated jobs (epic camunda/camunda#54840)](https://github.com/camunda/camunda/issues/54840)
- [Document ADR for job lease (camunda/camunda#56098)](https://github.com/camunda/camunda/issues/56098)
- [Epic AI Agent Visibility & Explainability | Kickoff and Solution](https://docs.google.com/document/d/1iMgG-ESVCuwpr-3vuOdPBthTEyvaGWSGtLy0HY79ywA) (internal)
- [AI Agent Visibility & Explainability (product-hub #3462)](https://github.com/camunda/product-hub/issues/3462)

