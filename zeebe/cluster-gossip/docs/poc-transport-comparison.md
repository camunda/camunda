# Gossiper transport: proof-of-concept comparison

**Status**: Results of two proofs of concept, to decide ADR 0001 D2 (transport choice). Not a
decision yet.

**Code**: `zeebe/cluster-gossip/poc` — `SwimClusterGossiper` and `AntiEntropyClusterGossiper`, both
built on `AbstractClusterGossiper`, so they share the exact same semantics and differ only in how
state travels. `ClusterGossiperPocTest` runs the ADR 0001 contract against both on real
three-member clusters; `GossiperPocComparisonTest` produced the numbers below.

## The two proofs of concept

**SWIM** (`SwimClusterGossiper`) carries all state in SWIM member properties.

- A MEMBER value is a property of its owner. It disappears with the owner's SWIM membership, which
  already handles restarts through the member's instance id.
- Every member republishes its whole merged view of GLOBAL values in its own properties, and
  readers merge everyone's copies. Republishing is what lets a value outlive its writer.
- No new protocol: SWIM gossips a member's whole property set on every change (UDP, fanout 2),
  sends it with every probe and probe reply (TCP, every probe interval), and in full syncs (TCP,
  every 10 s). Re-delivery comes for free, because properties are state that SWIM keeps.

**Own transport** (`AntiEntropyClusterGossiper`) uses SWIM only to know the members.

- Push: changes go as deltas to 2 random peers in each of the next 3 push rounds; peers whose state
  changed forward them (rumor mongering).
- Pull: every 10 s, each member exchanges its full state with one random peer, over TCP.
- MEMBER values carry the owner's run (start time + nonce) and a per-run version. A member announces
  its run on start, so a restart replaces its old values even if SWIM never reports a removal.

Both use the same timing as SWIM's production defaults (250 ms gossip/push, fanout 2, 10 s sync).

## Results

Measured on localhost, all members in one JVM, no packet loss; see "Caveats".

### Convergence latency (5 members, production timing, 10 samples)

| Transport |  GLOBAL p50 / max  |  MEMBER p50 / max  | Messages / bytes per update |
|-----------|--------------------|--------------------|-----------------------------|
| SWIM      | 314 ms / 502 ms    | 495 ms / 2693 ms   | not observable (UDP)        |
| Own       | 486 ms / 515 ms    | 443 ms / 488 ms    | 36 / ~3 KB                  |

Both converge within one or two gossip rounds. With a single push per change, the own transport
regularly missed a member and fell back to the 10 s sync (max ~9 s in a first run); retransmitting
each change for 3 rounds removed that. SWIM hides the same weakness of its UDP gossip because every
1 s probe also carries full member state.

### Background traffic with nothing changing (5 members, one GLOBAL value)

TCP request bytes per second for the whole cluster, from Atomix messaging metrics. Atomix does not
record responses, and SWIM's UDP gossip is not observable, so SWIM's real numbers are higher.

| GLOBAL value | SWIM gossiper | Own transport (SWIM probes + own sync) |
|--------------|---------------|----------------------------------------|
| none         | 2.4 KB/s      | 2.5 KB/s                               |
| 10 KB        | 143 KB/s      | 7.6 KB/s                               |
| 100 KB       | 1.4 MB/s      | 53 KB/s                                |
| 1 MB         | 13.6 MB/s     | 0.5 MB/s                               |

On SWIM, each byte of GLOBAL state costs ~14 bytes per second in a 5-member cluster, forever: every
member republishes the value, and every probe sends the full properties of both prober and probed.
The cost grows with cluster size and probe frequency. The own transport costs ~0.5 bytes per second
per byte of state, all of it from the full-state sync; a digest-based sync (exchange versions, send
only what differs) would bring it close to zero.

### Value size

Both delivered a single GLOBAL value of up to 1 MB. SWIM manages that only because probes and syncs
travel over TCP: its UDP gossip cannot carry more than one datagram (~64 KB) per member, and
`NettyUnicastService` does not check the result of the send, so oversized gossip is dropped
silently. A member whose properties exceed a datagram is then disseminated only by probes (1 s) and
syncs (10 s) — including SWIM's own membership updates about that member.

### Membership changes (3 members, production timing)

| Transport | GLOBAL state | MEMBER value dropped after stop | … after restart |
|-----------|--------------|---------------------------------|-----------------|
| SWIM      | none         | 11.0 s                          | 0.8 s           |
| Own       | none         | 10.8 s                          | 1.2 s           |
| SWIM      | 100 KB       | 12.3 s                          | 0.8 s           |
| Own       | 100 KB       | 12.0 s                          | 1.5 s           |

Both depend on SWIM's failure timeout (10 s) for stopped members. Restarts are fast on both: SWIM
through its instance id, the own transport through its run announcement. A 100 KB value did not
measurably slow down failure detection in a 3-member cluster, where probes are direct.

## What the proofs of concept taught beyond the numbers

**SWIM**

- No new protocol, no new failure modes, and MEMBER lifetime is exactly SWIM membership.
- Gossip state and failure detection share one channel: every byte gossiped is resent by every
  probe, and a large state pushes SWIM's own updates out of its UDP gossip without any error.
  Gossip load cannot be tuned or rate-limited separately from failure detection.
- GLOBAL keys are expensive by construction, because survival requires every member to republish.
- Properties are `String → String`: values are base64 (+33%), and the existing `putAll` merge never
  removes a property, so the deferred deletes would need a change in SWIM itself.

**Own transport**

- Costs are proportional to change rate plus a tunable sync; the sync interval, fanout and
  retransmits are independent of failure detection, and TCP removes the size cliff.
- It needs its own run identity, because SWIM does not expose its instance id. Run order relies on
  the member's own clock not jumping backwards across a restart, and the gossiper has to guess
  whether a SWIM `MEMBER_REMOVED` is a restart (removed and re-added at once) or a real departure.
  Exposing SWIM's instance id on `Member` would remove both.
- More code to own: message format and versioning, retransmission, sync. A production version needs
  SBE or protobuf with a version header, metrics, and a digest-based sync.

## Recommendation

Use the **own transport for both scopes**, with SWIM only for membership.

- GLOBAL keys on SWIM cost an order of magnitude more traffic, continuously, and the cost lands on
  the failure detector. That alone rules SWIM out for GLOBAL keys.
- MEMBER keys on SWIM are cheaper than GLOBAL ones (no republishing), but still resent with every
  probe and still bounded by one datagram. Splitting the scopes across two transports would double
  the code and the failure modes for a small gain.
- The own transport's weak points are fixable: digest-based sync for idle traffic, and SWIM's
  instance id instead of the own run identity.

Before productionizing, validate on a real network with packet loss and a larger cluster (for
example in a load-test environment), where SWIM's unobservable UDP traffic and the own transport's
retransmits matter more.

## Caveats

- Localhost and a single JVM: no packet loss, no real latency, CPU shared by all members.
- Five members at most; SWIM's background cost grows with cluster size, the own transport's with
  cluster size squared for the full sync (until it is digest-based).
- SWIM's UDP traffic and all TCP responses are not measured; SWIM's numbers are lower bounds.
- The proofs of concept skip production concerns: message versioning for rolling updates, metrics,
  backpressure, and size limits.

## Reproduce

```bash
./mvnw verify -pl zeebe/cluster-gossip/poc -Dquickly -DskipTests=false \
  -Dtest=GossiperPocComparisonTest -Dgossip.poc.compare=true -Dsurefire.failIfNoSpecifiedTests=false
```

The report is written to `zeebe/cluster-gossip/poc/target/gossip-poc-comparison.md`.
