# Formally verifying `dynamic-config` with Lean 4 + Veil — plan of attack

**Status:** exploratory proposal (no team commitment yet).
**Scope:** the `zeebe/dynamic-config` module, starting from its stable core (gossip, versioned
merge, coordinator-serialized change plans).
**Tooling:** [Veil](https://github.com/verse-lab/veil) — an Ivy-style DSL embedded in Lean 4 for
specifying, model-checking, and proving state transition systems, with push-button SMT
verification and interactive Lean proofs as fallback
([CAV'25 paper](https://dl.acm.org/doi/10.1007/978-3-031-98682-6_2),
[lessons-learned paper](https://verse-lab.org/papers/veil-dafny26.pdf)).

## Goal and non-goals

**Goal.** A machine-checked model of the dynamic-config protocol that (a) surfaces design-level
bugs cheaply via counterexamples, (b) proves the core safety invariants the Java code currently
*assumes* (most importantly "equal version ⇒ equal content", which `MemberState#merge` enforces by
throwing), and (c) documents the protocol's implicit assumptions precisely enough that future
refactorings (multi-partition-group, ADR 0001) can be checked against them.

**Non-goals.**

- *Verifying the Java code itself.* Veil verifies a hand-written model; the model-to-code gap is
  narrowed by conformance tests (M4), never closed formally.
- *Liveness.* Veil is safety-only today. Known liveness gaps (a permanently failing `init` stalls
  the plan forever; coordinator failure mid-plan stalls phase advancement) stay documented, not
  proven.
- *Modeling the in-flux new model now.* The Phase-2 port (`CurrentClusterConfiguration`,
  `PhasedChangePlan`) is a moving target; it becomes milestone M5 once the design settles. The
  merge/gossip/versioning core we model first has been stable throughout the churn.

## Where this lives, toolchain

Everything under `zeebe/dynamic-config/formal/`, outside the Maven build. Setup (M0):

```bash
# one-time: install elan (Lean toolchain manager), then in formal/:
lake init dynamicconfig
# lakefile.toml:
#   [[require]]
#   name = "veil"
#   git = "https://github.com/verse-lab/veil.git"
#   rev = "main"        # pin to a tag once chosen
lake build              # SMT solvers (z3, cvc5) are fetched automatically by Veil
```

CI: a small GitHub Actions job running `lake build` on changes under `formal/` only. Not wired
into `./mvnw`.

> **Note:** `poc/DynamicConfigCore.lean` is a *sketch written without a Lean toolchain available*.
> Its syntax follows Veil's `Examples/Tutorial/Ring.lean` closely, but M0 includes making it
> actually compile.

## Modeling strategy

Keep the spec inside Veil's decidable-fragment sweet spot for as long as possible:

| Java construct | Model construct | Rationale |
|---|---|---|
| `MemberId` (sorted; coordinator = least active) | uninterpreted `type node` + `TotalOrder` | ACTIVE-filtering of the coordinator deferred to M2 |
| `ClusterConfiguration.version : long` | uninterpreted `type cversion` + `TotalOrder` | `+1`/`+2` become "strictly greater"; avoids integer arithmetic, which SMT handles poorly here |
| `MemberState.version : long` | uninterpreted `type mversion` + `TotalOrder` | same |
| `MemberState` content (state enum, partitions, …) | opaque `type mdata` | refined only in M3, where replica counting needs it |
| gossip payload (whole config) | `type snap` + immutable payload functions | message duplication/reordering/delay for free |
| `ClusterConfiguration#merge` fast path | atomic wholesale-adoption action | higher version wins wholesale, including the receiver's record of itself |
| `MemberState#merge` tie path | per-member merge action | interleaved per-member merging over-approximates the atomic map merge — sound for safety proofs; counterexamples need a realism check |
| `ClusterChangePlan` (M2) | head-operation relation + per-op "completed" marker | full sequences are awkward in FOL; head-of-queue eligibility is all the code uses |

## Milestones

| # | Scope | Exit criteria | Effort (1 person, FM newcomer) |
|---|---|---|---|
| **M0** | Toolchain bring-up: `formal/` lake project, Veil pinned, `Ring.lean` and the PoC compile | `lake build` green locally + in a CI job | 0.5–2 days |
| **M1** | Gossip/merge core (the PoC file): LWW fast path, per-member tie merge, single-writer actions | `#check_invariants` runs; the expected counterexample to `member_version_determines_content` is reproduced and *interpreted*; `sat trace [member_record_regression]` evaluated | 2–4 days |
| **M1b** | Make the safety property inductive: add the protocol discipline as explicit `require`s (e.g. "a version bump happens only on a node holding every member's latest record"), discover auxiliary invariants (owner-supremacy, snapshot dominance) via the CTI loop | `#check_invariants` fully green, push-button; a written list of the assumptions M2 owes proofs for | 1–2 weeks |
| **M2** | Change-plan lifecycle: at-most-one pending plan, head-of-queue op eligibility, `advance()` at completion, cancel (+2). Prove M1b's assumptions from plan discipline — *except* for cancel, where we expect a genuine gap (see below) | Safety proof modulo an explicit, minimal assumption set; issue(s) filed for any confirmed real gap | 2–3 weeks |
| **M3** | Replica safety with concrete ops (join / leave / force-reconfigure over a partition relation): no partition below `minimumAllowedReplicas`, force-scale-down never reaches zero replicas | Proven, likely with some interactive Lean proofs (counting = arithmetic) | 3–5 weeks |
| **M4** | Conformance bridge: jqwik model-based tests replaying model traces against the real appliers/merge (`TestTopologyChangeSimulator`, `ClusterTopologyDomain` are reusable), asserting the proven invariants at runtime | Invariant-checking property test suite in the module's normal build | 1–2 weeks, parallelizable with M2/M3 |
| **M5** | *(deferred until the Phase-2 design settles)* New-model `PhasedChangePlan` (ADR 0001 D6/D7): phase-activation idempotence, no overlap between concurrent per-group plans, coordinator-restart recovery determinism | — | est. 3–4 weeks |

M1–M2 are the value inflection point: most protocol bugs of this class fall out of the CTI loop
and bounded traces there, long before full proofs exist.

## The first formal question is already visible

`ClusterConfiguration#merge` adopts a higher-versioned config
[**wholesale**](https://github.com/camunda/camunda/blob/3fd26c648ab04ba778f68a21ad47cb0ce2d96858/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/state/ClusterConfiguration.java#L253-L257)
— including the receiver's record of *itself* — while
[`cancelPendingChanges`](https://github.com/camunda/camunda/blob/3fd26c648ab04ba778f68a21ad47cb0ce2d96858/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/state/ClusterConfiguration.java#L511-L529)
mints `version + 2` from the *canceller's local view*, guarded only by an operational assumption
its own comment states: *"A conflict would not happen if the cancel is only called when the
operation is truly stuck."*

If a cancel races an in-flight member update, the wholesale adoption can regress a member's own
record; its next self-update then re-mints an already-used member version with different content,
and the next tie-merge hits the
[`IllegalStateException` in `MemberState#merge`](https://github.com/camunda/camunda/blob/3fd26c648ab04ba778f68a21ad47cb0ce2d96858/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/state/MemberState.java#L138-L151).
The 8.5→8.6 upgrade special-case in the same method is evidence that equal-version conflicts have
occurred in practice before.

`poc/DynamicConfigCore.lean` encodes exactly this as a 7-step bounded reachability query
(`sat trace [member_record_regression]`). Whether the abstract trace maps to a real Java execution
depends on details the model deliberately omits (when appliers may run relative to a cancel);
answering that — with either a formalized operational constraint or a bug report — is the M2 exit
criterion, and a good early test of whether this whole effort pays for itself.

## Risks and mitigations

- **Model–code drift while the module is in flux.** Scope to the stable core; M4 conformance
  tests fail loudly when semantics change; the abstraction map above is the review artifact.
- **SMT fragility (arithmetic, quantifier alternation).** Versions stay uninterpreted total
  orders; arithmetic is quarantined in M3 with interactive proofs as fallback.
- **Veil maturity** (1.x stable, 2.0 in preview, safety-only, small community). The model is
  Ivy-style throughout, so porting to Ivy or TLA+ is mechanical if Veil becomes a blocker.
- **Learning curve.** M0/M1 are deliberately sized as learning vehicles; the
  [Ring tutorial](https://github.com/verse-lab/veil/blob/main/Examples/Tutorial/Ring.lean) covers
  every construct the PoC uses.

## References

- [Veil (GitHub)](https://github.com/verse-lab/veil) · [veil.dev](https://veil.dev/) ·
  [CAV'25 paper](https://dl.acm.org/doi/10.1007/978-3-031-98682-6_2) ·
  [Lessons from building Veil (Dafny'26)](https://verse-lab.org/papers/veil-dafny26.pdf)
- [MongoDB: formally verifying logless dynamic reconfiguration (TLA+)](https://arxiv.org/pdf/2109.11987)
  — closest prior art to this module
- Module docs: [`AGENTS.md`](../AGENTS.md),
  [ADR 0001 — multi-partition-group configuration](../docs/adr/0001-multi-partition-group-cluster-configuration.md)
