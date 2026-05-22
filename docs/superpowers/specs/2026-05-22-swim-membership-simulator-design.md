# SWIM Membership Simulator — Design Spec

## Goal

An interactive, browser-based simulator embedded in the monorepo docs site that lets engineers
explore how Zeebe's SWIM membership protocol gossips both liveness changes and property updates
across a cluster, and how each parameter affects detection speed, convergence time, and message
volume.

## Placement

`Architecture → Components → Orchestration Cluster → Membership Simulator (SWIM)`

File: `docs/monorepo-docs/architecture/components/orchestration-cluster/swim-membership-simulator.md`

Same page structure as the Partition Distributor: one-sentence intro → simulator component →
explanatory sections below (text to be written with the author post-implementation).

---

## What the Simulator Models

### Two types of gossiped updates (faithful to Zeebe)

**1. Property update** — simulates a `BrokerInfo` change (e.g. a partition leadership change,
health status flip). The originating node queues the update; it spreads via gossip only
(fanout + interval, or to all peers if `broadcastUpdates=true`). No probing involved. Each node
tracks a simplified "property version" counter per peer (standing in for the full SBE-encoded
`BrokerInfo` blob). The event log and stale-view indicator show when each node learns of the change.

**2. Node crash** — removes a node from the network. Other nodes probe it, fail to get a response;
after `suspectProbes` consecutive failures they mark it suspect; after `failureTimeout` in suspect
state it is declared dead. Failure news then propagates via gossip. If `broadcastDisputes=true` the
suspect/dead notification is immediately sent to all peers instead of waiting for the next gossip
round. If `notifySuspect=true` the suspected node is pinged directly and can refute the suspicion
by incrementing its incarnation number.

**3. Network partition** — a directed link between two nodes is blocked (messages in that
direction are dropped). Asymmetric partitions are supported: A→B can be blocked while B→A is open.
Multiple partition pairs can be active simultaneously. Gossip can route around single-link
partitions through other nodes; the simulator makes this visible.

### Anti-entropy sync

Every `syncInterval` ms each node picks a random peer and exchanges its full membership view
(bidirectional). This is the catch-up mechanism that matters after a partition heals: the
simulator shows the time-to-convergence improvement from sync.

### Incarnation numbers

Simulated. When `notifySuspect=true` and the suspected node is alive and reachable, it responds
with an incremented incarnation number, overriding the suspicion. This is visible in the event log.

### Simulation fidelity

Illustrative, not production-accurate. The algorithm's behavioral structure is correct (probe
pipeline, gossip fanout, suspect/dead state machine, incarnation-number refutation, sync
anti-entropy). Message sizes, serialization overhead, and network jitter are not modeled. The
goal is parameter intuition, not performance benchmarking.

---

## Parameters

All parameters are exposed in the UI. Defaults match `SwimMembershipProtocolConfig.java`.

### Gossip
| Parameter | Default | Range | Notes |
|-----------|---------|-------|-------|
| `gossipInterval` | 250 ms | 50–2000 ms | |
| `gossipFanout` | 2 | 1–N-1 | capped to node count minus 1 |
| `broadcastUpdates` | false | toggle | sends to ALL peers; disables fanout |

### Failure detection
| Parameter | Default | Range | Notes |
|-----------|---------|-------|-------|
| `probeInterval` | 1000 ms | 100–5000 ms | |
| `probeTimeout` | 100 ms | 50–2000 ms | must be < probeInterval |
| `suspectProbes` | 3 | 1–10 | total probers: 1 direct + (N-1) indirect |
| `failureTimeout` | 10 000 ms | 1000–30 000 ms | |
| `broadcastDisputes` | true | toggle | |
| `notifySuspect` | false | toggle | |

### Anti-entropy
| Parameter | Default | Range | Notes |
|-----------|---------|-------|-------|
| `syncInterval` | 10 000 ms | 1000–60 000 ms | |

### Cluster
| Parameter | Default | Range |
|-----------|---------|-------|
| Node count | 6 | 2–16 |

---

## Simulation Engine

### Clock model

Discrete-event simulation with simulated time. A priority queue holds scheduled events sorted by
`simTime`. The animation loop (requestAnimationFrame) advances the clock by
`tickMs × speedMultiplier` per frame (tickMs = 10 ms sim time). Speed multipliers: ×1, ×5, ×20.
Pause freezes the clock. Step advances by one `probeInterval` of sim time.

### Per-node state

```
{
  id: number,                           // 0-indexed
  state: 'alive' | 'suspect' | 'dead' | 'crashed',
  incarnationNumber: number,
  propertyVersion: number,              // bumps on injected property update
  membershipView: Map<id, {
    state, incarnation, propertyVersion, // what this node believes about each peer
  }>,
  gossipQueue: Array<{                  // updates pending for gossip
    memberId, state, incarnation, propertyVersion, sendCount
  }>,
  probeOrder: id[],                     // shuffled probe list; round-robined via probeCounter
  probeCounter: number,                 // incremented each probe tick, mod probeOrder.length
  suspectSince: Map<id, simTime>,       // when did we first suspect each peer
  gossipExcluded: boolean,              // injectable fault: other nodes skip this node in fanout
}
```

### Scheduled event types

| Event | Fires at | Action |
|-------|----------|--------|
| `GOSSIP_TICK(node)` | every `gossipInterval` | pick fanout peers, send gossip |
| `PROBE_TICK(node)` | every `probeInterval` | pick random peer, send direct probe |
| `PROBE_TIMEOUT(from, to)` | `probeSentAt + probeTimeout` | direct probe failed — send indirect probe requests to `suspectProbes-1` peers |
| `INDIRECT_TIMEOUT(coordinator, suspect)` | `probeTimeout × 2` after requests sent | all indirect probes also failed — mark suspect |
| `FAILURE_TIMEOUT(node, target)` | `suspectSince + failureTimeout` | declare target dead |
| `SYNC_TICK(node)` | every `syncInterval` | full view exchange with random peer |
| `MSG_DELIVER(from, to, payload)` | now (0 sim-time delay) | apply message |

Messages between partitioned pairs are dropped at send time (no `MSG_DELIVER` scheduled).

### Gossip update selection

Each node's gossip queue entries are sorted by `sendCount` ascending (least recently sent first).
On each gossip tick, pick `gossipFanout` random peers from the alive member list, **excluding any
node with `gossipExcluded=true`**. Send those peers the top entries from the gossip queue.
`sendCount` is incremented; entries are removed after `⌈log₂(N)⌉` sends.
If `broadcastUpdates=true`: send to all alive peers regardless of fanout (gossip-excluded nodes
are still excluded, as `broadcastUpdates` only bypasses the fanout count, not the exclude flag).
If `broadcastDisputes=true` and the queued entry is a suspect/dead state change: send to all peers
regardless of fanout (overrides fanout for that entry only; gossip-excluded nodes are still
excluded).

### Probe target selection (shuffle + round-robin)

Each node maintains a `probeOrder` list: all other known alive nodes in shuffled order.
The list is re-shuffled whenever membership changes (node added, removed, or declared dead).
On each `PROBE_TICK`, the next target is `probeOrder[probeCounter % probeOrder.length]`;
`probeCounter` increments each tick. This matches the actual implementation.

Dead nodes are removed from `probeOrder` when declared dead. However, they are re-added to a
separate `deadNodes` set. Each `PROBE_TICK` also probes one dead node (selected round-robin from
`deadNodes`) to detect resurrection — if it responds, it is re-added to `probeOrder` with alive
state and a new incarnation number. This mirrors how Zeebe re-probes discovery-service nodes that
are not present in the live members map.

### Probe pipeline (indirect probes — the core SWIM mechanism)

`suspectProbes` is the **total number of probers**: 1 direct probe (from A) plus
`suspectProbes - 1` indirect probe requests (sent by A to random peers C, D, … who then probe B
on A's behalf). A node is only marked suspect when *all* of these fail. This avoids false
positives from single-link network issues: if A cannot reach B but C can, B is not suspected.

```
PROBE_TICK fires for node A:
  pick next alive peer B via round-robin through shuffled probeOrder
  send P→ to B (direct probe, timeout = probeTimeout)
  if deadNodes is non-empty: also probe one dead node D (round-robin through deadNodes)
    if D responds: resurrect D (add to probeOrder, remove from deadNodes, new incarnation)

B receives P→:
  if B is alive: send P✓ back to A
  if B is crashed / link B→A partitioned: response is dropped

A receives P✓:
  update A's view of B; done

A's direct probe times out (no P✓ within probeTimeout):
  select (suspectProbes - 1) random peers C, D, … (excluding A and B)
  send PR→ to each (probe request: "please probe B for me")
  timeout for each indirect probe = probeTimeout × 2

C receives PR→(B):
  send P→ to B (on behalf of A)
  if B responds: C sends success back to A
  if B times out: C sends failure back to A

A collects indirect probe results:
  if ANY indirect probe succeeds: B is reachable, no action
  if ALL fail (or suspectProbes == 1, no indirect probers available):
    mark B suspect in A's view; suspectSince[B] = now
    add suspect entry to A's gossip queue
    if broadcastDisputes: send to all alive peers immediately
    schedule FAILURE_TIMEOUT(A, B)

FAILURE_TIMEOUT fires (A, B):
  if B is still suspect in A's view:
    mark B dead in A's view
    add dead entry to gossip queue
    if broadcastDisputes: send to all immediately
```

When `notifySuspect=true`: the suspect node receives the probe carrying its own suspect state,
increments its incarnation number, and broadcasts a correction overriding the suspicion.

### Receiving gossip

For each entry `(memberId, state, incarnation, propertyVersion)` in the message:
- If `incarnation > local incarnation` for that member: accept, update view, enqueue for re-gossip
- If same incarnation and state is "worse" (alive < suspect < dead): accept
- If `notifySuspect=true` and `memberId == self` and state is suspect and self is alive:
  increment own `incarnationNumber`, override with alive state, enqueue correction

### Convergence detection

After every event, check: do all alive (non-crashed) nodes hold identical membership views?
Record the sim time of first convergence after each fault injection. Reset on next fault.

---

## Layout

```
┌─────────────────────────────────────────────────────────────┐
│  PARAMETERS  (collapsible, 3 groups: Gossip | FD | Sync)    │
│  + Node count slider                                        │
└─────────────────────────────────────────────────────────────┘
┌───────────────────────────────────┬─────────────────────────┐
│                                   │  EVENT LOG              │
│       NODE GRAPH  (SVG)           │  (state transitions     │
│       ~380px, circular layout     │   only, auto-scroll,    │
│       animated message circles    │   max 100 entries)      │
│                                   │                         │
└───────────────────────────────────┴─────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  STATS: sim-time · round · msgs sent · detect · converge    │
├─────────────────────────────────────────────────────────────┤
│  [▶ Play] [⏸ Pause] [⏭ Step]  Speed: ×1 ×5 ×20   [Reset]  │
│  Inject: [Crash node ▼]  [Restore node ▼]                   │
│          [Property update ▼]  [Partition A→B ▼▼]           │
│          [Gossip exclude ▼]  [Clear all faults]             │
└─────────────────────────────────────────────────────────────┘
```

The node graph is an SVG element. Nodes are arranged in a fixed circle (center + radius computed
from node count). Edges are SVG lines drawn between all pairs; partitioned directed links are
rendered as dashed red lines with a direction indicator.

---

## Node Visual States

| State | Appearance |
|-------|-----------|
| Alive, view current | Filled circle, neutral (`--ifm-color-emphasis-200`), dark border |
| Alive, stale view | Same fill, dashed border — at least one peer's state differs from ground truth |
| Suspect | Orange border (`#f57c00`), light orange fill |
| Dead | Red fill (`var(--viz-leader)`), white label |
| Crashed | Dark grey fill, label struck-through |
| Gossip-excluded (overlay) | Yellow dashed outer ring — combinable with any liveness state |

Node label: `N{id}` (e.g. `N0`, `N1`). Small property-version badge below label.

---

## Message Visualization

Medium circles (~18px diameter) that travel along the SVG line between source and destination
over ~300ms wall time (independent of sim speed — always visible). Each carries a letter + symbol:

| Circle contents | Color | Meaning |
|----------------|-------|---------|
| `G~` | Grey | Routine gossip (heartbeat / no news) |
| `G↑` | Green (`#2e7d32`) | Gossip carrying a property update |
| `G✗` | Red (`var(--viz-leader)`) | Gossip carrying failure news |
| `P→` | Amber (`#f57c00`) | Direct probe outgoing |
| `P✓` | Green | Probe ACK |
| `P✗` | Red | Probe timeout (no ACK) |
| `PR→` | Orange (`#e65100`) | Indirect probe request (ask peer to probe suspect) |
| `S⇄` | Blue (`#1565c0`) | Full sync |

Multiple message circles can be in flight on the same edge simultaneously. Routine `G~` messages
use 60% opacity to visually de-emphasize background traffic. A small legend is rendered in the
bottom-right corner of the SVG.

---

## Stats Bar

| Stat | Description |
|------|-------------|
| Sim time | Current simulated time (ms) |
| Round | Number of gossip ticks fired |
| Messages sent | Total messages dispatched since last reset |
| Time to detect | Sim time from fault injection to first node marking the target suspect/dead |
| Time to converge | Sim time from fault injection to all nodes agreeing on the same view |
| Status chip | `Converged ✓` / `Converging…` / `Partitioned ⚠` |

---

## Fault Injection

### Crash / Restore
Dropdown selects target node. Crash: set state to `crashed`, stop all scheduled events for that
node, drop all in-flight messages to/from it. Restore: set state to `alive`, increment incarnation
number, restart `GOSSIP_TICK`, `PROBE_TICK`, `SYNC_TICK` events.

### Property update
Dropdown selects source node. Bumps that node's `propertyVersion`, adds update entry to its
gossip queue, records injection time for convergence measurement.

### Network partition
Two dropdowns: source node and target node. Adds a directed block `{from, to}`. Direction matters:
`A→B` blocked means A's messages to B are dropped, but B can still send to A (asymmetric).
Multiple pairs can be blocked. "Clear all partitions" removes all blocks.

Partitioned directed links are shown as dashed red SVG lines with a small arrow indicating
direction. Bidirectional partitions render two overlapping dashed lines.

### Gossip exclude
Dropdown selects a target node. Sets `gossipExcluded=true` on that node: all other nodes skip it
when selecting gossip fanout targets. The node can still be probed and synced — it is not
network-isolated, just systematically unlucky in gossip selection. This demonstrates the
worst-case scenario where a node never happens to be picked by any peer's random fanout, leaving
it stale until `syncInterval` fires or `broadcastUpdates=true`. Toggle off to restore normal
selection. Gossip-excluded nodes are visually marked with a dashed yellow outline in the graph.

---

## Files

| File | Action |
|------|--------|
| `monorepo-docs-site/src/components/SwimMembershipSimulator.js` | Create (~500 lines) |
| `monorepo-docs-site/src/components/SwimMembershipSimulator.module.css` | Create (~400 lines) |
| `docs/monorepo-docs/architecture/components/orchestration-cluster/swim-membership-simulator.md` | Create |
| `monorepo-docs-site/sidebars.js` | Modify — add entry under Orchestration Cluster |

Algorithm functions are inlined in `SwimMembershipSimulator.js` (no separate module — no test
suite exists for the docs site, consistent with PartitionDistributionVisualizer.js).

CSS follows the same pattern as `PartitionDistributionVisualizer.module.css`: CSS Modules with
`--ifm-*` Infima variables for structural elements, `--viz-*` custom properties (already defined
in `custom.css`) for the data viz palette, and `[data-theme='dark']` overrides.

---

## Color additions to `custom.css`

New `--viz-*` variables needed (append to existing block):

```css
:root {
  --viz-suspect: #f57c00;
  --viz-suspect-light: #fff3e0;
  --viz-msg-gossip-update: #2e7d32;
  --viz-msg-probe: #f57c00;
  --viz-msg-sync: #1565c0;
}
[data-theme='dark'] {
  --viz-suspect: #ffb74d;
  --viz-suspect-light: #3e2000;
  --viz-msg-gossip-update: #66bb6a;
  --viz-msg-probe: #ffb74d;
  --viz-msg-sync: #42a5f5;
}
```

---

## Open items (post-implementation)

- Prose in the doc page (intro paragraph, "How it works" and "Parameters" sections) — to be
  written with the author after implementation.
- Whether to add a "scenario presets" feature (e.g. "Show me an asymmetric partition") — deferred.
