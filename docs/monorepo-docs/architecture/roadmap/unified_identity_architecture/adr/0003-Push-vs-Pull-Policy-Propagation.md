# ADR-0003: Push vs Pull Policy Propagation (Hub ↔ Orchestration Clusters)

## Status

Accepted

## Context

The unified identity architecture defines Hub as the authoritative source of truth (SoT) for identity and policy, and each Orchestration Cluster (OC) as a cluster-local projection and enforcement layer.

We need to decide how policy changes flow from Hub to OCs.

Two main models are on the table:

- Push-based propagation: Hub sends policy changes to OCs when they occur.
- Pull-based propagation: OCs periodically ask Hub whether there is a new policy and, if so, retrieve the changes needed to catch up.

This ADR only concerns Hub ↔ OC policy propagation. OC ↔ Engine propagation remains an internal concern of the OC.

Additional non-functional constraints from current SaaS scale:

- Approximately 47k organizations and approximately 43k created clusters must be supported without degrading Hub or OC performance.
- Propagation must avoid synchronized load spikes (for example, retry storms or polling herd effects).
- Rollout visibility must remain operationally useful at large cluster counts (version, status, error, retry state per OC).
- Propagation paths must include overload protection (rate limiting) and failure isolation (circuit breaking).

## Decision

We will:

- Use a push-based propagation model from Hub to OCs, implemented with a transactional outbox:
  - Hub persists policy-related data and a corresponding outbox event in a single transaction.
  - A Hub-local outbox dispatcher later sends either a policy snapshot or a policy diff to each affected OC.
- Allow OCs to request a re-sync (asking for a snapshot) when they detect gaps or inconsistencies, but not as the primary propagation mechanism.
- Not implement a general pull-based model where OCs periodically query Hub for new policy versions and then fetch the corresponding changes.

The only fixed constraint is that Hub is the policy SoT. Using a transactional outbox and push-based delivery is a design choice made in this ADR.

## Options considered

### Option 1 – Push-based propagation from Hub to OCs (chosen)

Shape:

- When policy changes in Hub are committed, Hub also records events that describe what must be sent to which OCs.
- A background dispatcher in Hub reads these events and sends policy updates (either complete snapshots or compact diffs) to the corresponding OCs.
- Each OC has an entry point that accepts these updates, applies them to its local policy projection, and tracks the last applied policy version.

Pros:

- Clear relationship between writing policy and scheduling its delivery:
  - Recording the policy change and the fact that it needs to be delivered happens together.
- Good central visibility:
  - Hub can keep track, per OC, of which policy version it intended to deliver and which version an OC has confirmed.
  - This makes it easier to answer questions like “which clusters are on policy version X?”.
- Efficient for infrequent configuration changes:
  - Policy changes are relatively rare compared to runtime authorization decisions.
  - OCs do not waste resources asking Hub for changes when there are none.
- Simple mental model for operators:
  - “When I change policy in Hub, Hub will push it out to all relevant clusters and tell me if something failed.”
- Better central rollout visibility at scale:
  - Hub can expose delivery progress and failure states per OC without adding extra OC reporting paths.

Cons:

- Hub must be able to reach OCs over the network in a controlled and secure way.
- The dispatcher needs robust retry and error handling to deal with temporary OC unavailability.
- Hub must implement dispatcher backpressure controls:
  - Rate limiting, bounded concurrency, and jittered retries are required to avoid overwhelming OCs.
- Hub-to-OC delivery clients need circuit breaking to isolate repeatedly failing OCs and prevent cascading impact on overall rollout throughput.
- Snapshot-based propagation (without diffs) increases per-call payload size and apply time:
  - Larger payloads can increase latency per delivery and require stronger timeout, batching, and throughput controls.

### Option 2 – Pull-based propagation (OC asks Hub for new versions and changes)

Shape:

- Hub maintains a sequence of policy versions per cluster and, for each version, enough information to:
  - Reconstruct the full effective policy at that version (snapshot), and
  - Provide the changes between versions (diffs).
- Each OC tracks the last policy version it has applied.
- Periodically, an OC:
  - Asks Hub what the current policy version is for its cluster.
  - If Hub reports a higher version than the OC’s stored version, the OC asks Hub for:
    - Either the full snapshot of the current policy, or
    - The sequence of diffs needed to move from its current version to the latest version.
  - The OC applies the received changes and updates its stored version.

In other words, both push and pull require Hub to maintain versioned policy data and the ability to produce snapshots and diffs. The main difference is who initiates the transfer and who has a precise view of rollout state.

Pros:

- OCs do not need to expose a dedicated apply endpoint for Hub; they are the ones initiating communication.
- Network topology is simple from the OC perspective: each OC only needs to know how to talk to Hub.
- The overall versioning and diff model is the same as in the push-based approach, so the conceptual data model is shared.

Cons:

- Polling trade-offs:
  - If OCs poll too frequently, they increase load on Hub with little benefit when there are no changes.
  - If they poll too infrequently, policy rollout can be noticeably delayed.
- Pull creates continuous background traffic by design:
  - Even without policy changes, OCs keep polling Hub, consuming network and compute capacity.
- Central visibility is weaker unless we add extra reporting:
  - By default, Hub only knows “the latest policy version is N”, not which version each OC is currently running.
  - To regain that information, OCs would need to report their applied version back to Hub, which reintroduces coordination complexity.
- No built-in notion of queued deliveries:
  - There is no concept of “this change is ready to be delivered to OC X but has not been pulled yet”.
  - Investigating “why is OC X still on an old policy?” becomes harder.
- More logic per OC:
  - Error handling (for example, failures while applying a chain of diffs) must be coordinated across two independent systems.
- Polling synchronization risk at large OC counts:
  - Without jitter and staggering, many OCs can poll simultaneously and overload Hub (thundering herd).
- Pull clients and Hub polling endpoints both require rate limiting and circuit breaking:
  - OCs should back off when Hub is degraded; Hub should protect itself from abusive or accidental poll bursts.

### Option 3 – Hybrid: push as default, pull only for recovery

Shape:

- Normal operation:
  - Same as Option 1: Hub records policy changes and pushes snapshots or diffs to OCs.
- Exceptional cases:
  - An OC can explicitly request a full snapshot from Hub if it detects that its state is inconsistent or too far behind.
  - This is used for recovery and bootstrapping, not as the main propagation path.

Pros:

- Retains the advantages of push-based propagation for day-to-day operation.
- Provides a recovery path if an OC loses state or detects an unrecoverable gap.

Cons:

- Requires a small additional API surface and operational guidance for recovery flows.

## Decision outcome

We choose push-based propagation from Hub to OCs (Option 1), with an optional recovery mechanism (Option 3). We do not implement a generic pull-based propagation model for policy.

Rationale:

- Respects the core constraint that Hub is the policy source of truth, while giving Hub clear visibility into rollout state per OC.
- Keeps propagation aligned with the moment policy is changed, rather than leaving coordination entirely to OCs.
- Avoids the steady overhead and complexity of periodic polling from many OCs when policy changes are relatively rare.
- Concentrates most of the complexity (ordering, retries, monitoring) on the Hub side, instead of duplicating it in every OC.
- Fits current SaaS scale better by preserving central rollout observability while avoiding synchronized poll traffic from many clusters.
- Push is event-driven and runs when needed (on policy changes), while pull introduces constant periodic traffic regardless of change volume.

## Consequences

- Hub must:
  - Provide a mechanism to record policy changes and schedule them for delivery to OCs.
  - Maintain versioned policy data per cluster and be able to derive snapshots and diffs.
  - Track, per OC, which policy version has been acknowledged as applied.
  - Surface this information via metrics or dashboards so operators can monitor rollout progress.
  - Enforce dispatcher backpressure controls (bounded concurrency, rate limits, and jittered retries).
  - Use circuit breaking on outbound Hub-to-OC propagation calls, with bounded retries and backoff.
  - Keep outbox polling/query paths efficient for large OC counts.
  - Protect inbound propagation-related endpoints (for example recovery/re-sync requests) with rate limiting.
- OCs must:
  - Provide a way to accept policy updates from Hub and apply them idempotently.
  - Track the last applied policy version locally.
  - Optionally, provide a way to initiate a re-sync (full snapshot) from Hub when local state is known to be inconsistent.
  - Handle repeated deliveries safely and expose enough status for operational diagnosis.
  - Respect rate limits and retry hints from Hub (for example 429/503 with backoff semantics) when initiating recovery flows.
- We do not:
  - Implement regular polling from OCs to Hub to discover new policy versions.
  - Model policy propagation after job polling patterns from Camunda 8 workers; policy changes are much less frequent and benefit from a more centrally visible rollout process.

- Common requirements regardless of propagation style:
  - Keep apply idempotent by `policyVersionId`.
  - Scope propagation to the affected organization/cluster.
  - Provide operational runbooks for rollout stalls, forced re-sync, and version-gap recovery.
