import Veil

/-! # Dynamic-config gossip/merge core — Veil proof-of-concept (milestone M1)

This file models the *stable core* of `zeebe/dynamic-config`: the gossip
protocol and the version-driven merge of `ClusterConfiguration`, abstracting
away everything else (partitions, routing, exporters, the change plan).

**Status: PoC sketch — not yet compiled.** This environment has no Lean
toolchain. The syntax follows `Examples/Tutorial/Ring.lean` from
https://github.com/verse-lab/veil (checked 2026-07-24), but expect small
fixes on first `lake build` (in particular the `function` point-wise
assignment syntax in `gossip_send` / `gossip_recv_adopt`). See
`../PLAN.md` for how this file fits the overall roadmap.

## What is modeled (and the Java it corresponds to)

| Model element        | Java counterpart (`src/main/java/io/camunda/zeebe/dynamic/config/`)        |
|----------------------|-----------------------------------------------------------------------------|
| `type node`          | `MemberId`; totally ordered, coordinator = least (`ConfigurationChangeCoordinatorImpl#isCoordinator`) |
| `type cversion`      | `ClusterConfiguration.version` — as an *uninterpreted total order*, not an integer, to stay in Veil's decidable fragment; `+1`/`+2` become "strictly greater" |
| `type mversion`      | `MemberState.version`, same treatment                                        |
| `type mdata`         | the *content* of a `MemberState` (state enum, partitions, …), fully opaque   |
| `type snap`          | one gossiped copy of the configuration; immutable once sent, so the network may duplicate, reorder, and delay for free |
| `self_update`        | an applier's `init`/`apply` mutating the member's *own* `MemberState` (single-writer rule, see comment on `MemberState`) |
| `bump_config`        | minting a higher config version: change start / completion (`advance`) / `cancelPendingChanges` (+2) |
| `gossip_recv_adopt`  | `ClusterConfiguration#merge` fast path: higher version wins **wholesale**    |
| `gossip_recv_tie`    | `MemberState#merge` on equal config versions: higher member version wins, per member |

Deliberate abstraction: the Java tie-merge is atomic over the whole member
map, while `gossip_recv_tie` merges one member record at a time. Because
per-member merges are independent (no cross-member reads), the interleaved
version reaches a *superset* of the atomic version's states — sound for
proving safety, but a counterexample trace must be re-checked for realism.

## What we expect the checker to say

The safety property `member_version_determines_content` is the model-level
statement of the invariant that `MemberState#merge` *assumes* by throwing
`IllegalStateException` on "equal version, different content".

We expect `#check_invariants` to produce a **counterexample to induction**,
and the bounded query `member_record_regression` below to be **satisfiable**:
nothing in this abstract model stops a node from minting a higher config
version while holding a *stale* record of some member; wholesale adoption
then regresses that member's own record, and its next self-update re-mints
an already-used member version with different content.

That is not (necessarily) a modeling artifact. In the real system, normal
version bumps happen on nodes that provably hold every member's latest
record (plan operations are serialized; each applier has seen the previous
operation's effects before becoming eligible). `cancelPendingChanges` has no
such guarantee — the code says so itself: *"A conflict would not happen if
the cancel is only called when the operation is truly stuck."* Milestone M2
models the plan discipline to prove the normal path safe and to turn the
cancel path into either a formalized operational assumption or a bug report.
-/

veil module DynamicConfigCore

type node
type cversion
type mversion
type mdata
type snap

/- Uninterpreted total orders keep the spec in the EPR-friendly fragment;
version arithmetic (`+1`, `+2`) is abstracted to "strictly greater". -/
instantiate nodeOrd : TotalOrder node
instantiate cvOrd : TotalOrder cversion
instantiate mvOrd : TotalOrder mversion

ghost relation cvLt (x y : cversion) := cvOrd.le x y ∧ x ≠ y
ghost relation mvLt (x y : mversion) := mvOrd.le x y ∧ x ≠ y

-- Per-node replica of the configuration (node → its local copy).
function cfgVer : node → cversion
function memVer : node → node → mversion   -- memVer n m: at node n, member m's record version
function memData : node → node → mdata     -- memData n m: at node n, member m's record content

-- Gossip payloads: immutable snapshots taken at send time.
relation snapUsed (s : snap)
relation inflight (s : snap) (dst : node)
function snapCfgVer : snap → cversion
function snapMemVer : snap → node → mversion
function snapMemData : snap → node → mdata

-- Arbitrary-but-uniform initial configuration shared by all nodes.
individual initCV : cversion
individual initMV : mversion
individual initMD : mdata

#gen_state

after_init {
  cfgVer N := initCV
  memVer N M := initMV
  memData N M := initMD
  snapUsed S := False
  inflight S N := False
}

/- A member mutates its own `MemberState` and bumps its own record version
(applier `init`/`apply`). Single-writer: only `m` writes `memVer _ m` at the
source. NOTE the require is against m's *current local* version — if that
was regressed by a wholesale adoption, `m` can re-mint a version number the
cluster has already seen, with different content. That is the hazard this
model probes. -/
action self_update (m : node) (v : mversion) (d : mdata) = {
  require mvLt (memVer m m) v
  memVer m m := v
  memData m m := d
}

/- Minting a higher config version (change start / completion / cancel),
restricted to the coordinator convention: least node id. Deliberately does
NOT require the coordinator to hold every member's latest record — in the
Java, normal completion bumps satisfy that by plan discipline (M2), but
`cancelPendingChanges` does not. Adding
  `require ∀ N M, mvOrd.le (memVer N M) (memVer c M)`
is the "repaired" variant under which the safety property should become
provable; that require is exactly the obligation M2 must discharge. -/
action bump_config (c : node) (v : cversion) = {
  require ∀ N, nodeOrd.le c N
  require cvLt (cfgVer c) v
  cfgVer c := v
}

/- Gossip send: snapshot the sender's current state. Never removed from
`inflight`, so duplication and arbitrary delay are included. -/
action gossip_send (src dst : node) (s : snap) = {
  require ¬ snapUsed s
  snapUsed s := True
  inflight s dst := True
  snapCfgVer s := cfgVer src
  snapMemVer s M := memVer src M
  snapMemData s M := memData src M
}

/- `ClusterConfiguration#merge`, fast path: strictly higher config version
wins wholesale — including the receiver's record of *itself*. Atomic. -/
action gossip_recv_adopt (n : node) (s : snap) = {
  require inflight s n
  require cvLt (cfgVer n) (snapCfgVer s)
  cfgVer n := snapCfgVer s
  memVer n M := snapMemVer s M
  memData n M := snapMemData s M
}

/- `MemberState#merge`, tie path: equal config versions, higher member
version wins, one member at a time (superset abstraction, see header).
The Java `throw new IllegalStateException` on equal-version-different-content
is *not* modeled as a transition — it is the safety property below. -/
action gossip_recv_tie (n m : node) (s : snap) = {
  require inflight s n
  require snapCfgVer s = cfgVer n
  require mvLt (memVer n m) (snapMemVer s m)
  memVer n m := snapMemVer s m
  memData n m := snapMemData s m
}

/- THE invariant `MemberState#merge` assumes when it throws on
"equal version, different content" — stated across node-local copies.
(Full statement also relates in-flight snapshot copies; add
`snapUsed S → …` clauses in M1b when strengthening to an inductive set.) -/
safety [member_version_determines_content]
  memVer N1 M = memVer N2 M → memData N1 M = memData N2 M

-- Bookkeeping invariants (expected to pass; smoke test for the setup).
invariant [inflight_snapshots_are_used] inflight S N → snapUsed S

#gen_spec

set_option veil.printCounterexamples true
set_option veil.smt.model.minimize true
set_option veil.vc_gen "transition"

#check_invariants

/- Sanity: the system has an initial state and can take steps. -/
sat trace [initial_state] { } by { bmc_sat }

sat trace [can_gossip] {
  gossip_send
  gossip_recv_tie
} by { bmc_sat }

/- The suspected `cancelPendingChanges` hazard, as a bounded reachability
query. Expected SAT in this abstraction. Walkthrough of the intended witness:
  1. self_update        -- member m bumps its own record: (v1, d)
  2. gossip_send        -- m's state, incl. (v1, d), heads to bystander n
  3. gossip_recv_tie    -- n now holds m's record (v1, d)  [same config version]
  4. bump_config        -- coordinator c, which has NOT seen (v1, d), mints a
                           higher config version around its stale copy of m
                           (this is the cancel-while-not-stuck case)
  5. gossip_send        -- c's higher-versioned config heads to m
  6. gossip_recv_adopt  -- m adopts wholesale; its OWN record regresses to initMV
  7. self_update        -- m re-mints v1 with different content d'
Now n holds (m ↦ v1, d) and m holds (m ↦ v1, d'): the next tie-merge between
them is the `IllegalStateException` in `MemberState#merge`. -/
sat trace [member_record_regression] {
  self_update
  gossip_send
  gossip_recv_tie
  bump_config
  gossip_send
  gossip_recv_adopt
  self_update
  assert (∃ N1 N2 M, memVer N1 M = memVer N2 M ∧ memData N1 M ≠ memData N2 M)
} by { bmc_sat }

end DynamicConfigCore
