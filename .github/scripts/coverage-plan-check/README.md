# Coverage plan check

Validates the coverage plans under [`qa/coverage/`](../../../qa/coverage/) against
this repository's test suite, so a plan cannot claim coverage that does not exist.

A coverage plan is a reviewed, machine-readable list of the automated cases an
epic needs — one YAML file per epic, written and reviewed by a human before any
test is generated. The coverage gate in
[`camunda/c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
reads these plans and reports their statuses onto product PRs. Nothing writes a
status back when a case lands, and until this check nothing verified one, so a
case still marked `automated` after its test was deleted or renamed was reported
as coverage that exists.

See camunda/camunda#65183 and camunda/team-test-automation#83.

## Used by

|                                Workflow                                |                                              Trigger                                               |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| [`check-coverage-plans.yml`](../../workflows/check-coverage-plans.yml) | PRs touching a plan or the suite paths plans point at; every push to `main`; weekdays at 06:00 UTC |
| [`coverage-writeback.yml`](../../workflows/coverage-writeback.yml)     | Tuesdays at 07:41 UTC; manual dispatch                                                             |

## Running it locally

From the **repository root** — the validator resolves each spec `path` against
the working directory, which is what lets it check a plan against the suite
rather than only against itself:

```bash
npm ci --prefix .github/scripts/coverage-plan-check
node .github/scripts/coverage-plan-check/coverage-plan.mjs validate qa/coverage --no-case-ids
node .github/scripts/coverage-plan-check/coverage-plan.mjs summary qa/coverage/<plan>.yml
node .github/scripts/coverage-plan-check/coverage-writeback.mjs qa/coverage
```

The write-back EDITS the plans in place, so run it on a clean tree and read the
diff before committing — the weekly workflow exists so a human reviews that
diff rather than having it applied silently.

`--no-case-ids` is not optional here, and the workflow passes it too — see
below. Without it the command reports six errors against the valid checked-in
plan, because these suites carry no case ids in their test titles.

## What it checks

Schema validity of every plan, plus drift between a plan and the suite:

|                                     Drift                                     |      Severity       |                     Why                      |
|-------------------------------------------------------------------------------|---------------------|----------------------------------------------|
| A case marked `automated` or `stub` whose spec file does not exist            | **error**           | The plan claims coverage that does not exist |
| A case marked `automated` or `stub` whose id appears nowhere in its spec file | **error**, off here | See below                                    |
| A case marked `planned` whose id already appears in its spec file             | warning, off here   | The suite is ahead of its paperwork          |

### Why the id check is off here (`--no-case-ids`)

The validator can require each case id to appear in its spec file. Over a suite
**generated from the plan** that is a real check: the generator writes the id as
the first token of the test title, so a hit means a test by that name exists.
That is how it runs in `camunda/c8-cross-component-e2e-tests`.

This repository's suites predate the plans that point at them — the JUnit ITs,
the vitest component tests, and the hand-written Playwright API specs alike. No
test title carries an id, and only a comment could put one there. A comment
makes the grep pass while binding to no test that runs, so the check would read
as verification while verifying nothing, and the price of switching it on is a
case-id comment pasted into every product test file a plan names.

So this repository runs the existence check alone. It is the drift that is real
for such a suite, and the one a comment cannot fake: a test file deleted, moved
or renamed is exactly how a plan entry pointing at pre-existing tests goes
stale.

If a future suite here **is** generated from its plan, drop the flag for it.

## Writing a status back

`coverage-writeback.mjs` closes the other half of the same gap. Nothing flipped
a case's status when its test landed, so a plan goes on saying `planned` while
the gate reports that stale status to every product PR linking the epic — which
is how `qa/coverage/product-hub-3526.yml` came to under-report its own epic by
47 cases.

It only moves `planned` → `automated`. The reverse is an error the validator
already raises, and the fix for it is usually to restore the test rather than to
quietly downgrade the claim; a `stub` is never promoted, because its id is
present while its test is still skipped. The edit is textual rather than a YAML
round-trip, so the reviewer comments that make an exclusion auditable survive it.

**Its evidence is a test title, not a mention.** The id has to sit immediately
inside a quote — `test('ID: ...')`, `@DisplayName("ID: ...")` — so a
`// ID: still to write` comment does not count. That narrowing is the only
reason this is safe to run here. The validator settles for a bare substring and
is given `--no-case-ids` because of it (above); the same bare substring in a job
that *edits* the plan would commit a coverage claim nobody made, on the strength
of one comment. Verified against this repository's own plans: with the bare
form, a single planted comment line flips `BOM-UI-12` in
`product-hub-3066.yml` to `automated`.

Over today's suites it therefore finds nothing, every week, and that is the
intended resting state rather than a misconfiguration. It starts doing work the
moment a plan points at a suite generated from it.

## Vendoring

`coverage-plan.mjs` and `coverage-writeback.mjs` are **vendored**, not written
here. Their source of truth, the schema and their tests live in
`camunda/c8-cross-component-e2e-tests` (`scripts/`). [`vendor.json`](vendor.json)
lists both copies and records the one revision they came from.

Do not edit the copy. A local edit makes this repository enforce a schema no plan
author is writing against, and the next re-sync silently reverts it. Change it
upstream, then re-sync:

```bash
# from a checkout of camunda/c8-cross-component-e2e-tests at the new revision
dest=<camunda>/.github/scripts/coverage-plan-check
cp scripts/coverage-plan.mjs      "$dest/coverage-plan.mjs"
cp scripts/coverage-writeback.mjs "$dest/coverage-writeback.mjs"
# restore the VENDORED FILE header at the top of each copy, then record the
# revision in BOTH headers and in vendor.json's source_ref
git -C . rev-parse HEAD
```

Re-sync both together. `vendor.json` carries one `source_ref` for the pair, and
`coverage-writeback.mjs` imports `coverage-plan.mjs`, so a half-sync pins a
revision that describes neither copy. Pin a revision that is on the source
repository's `main`: the drift guard resolves the pin there, and one that was
squashed away reads as `unknown-ref`.

The tradeoff vendoring buys and what it costs: the check needs no credential for
a private repository and no network at validation time, so it runs on every PR
including forks — but the copy drifts from the schema it enforces unless someone
re-syncs. `vendor.json` is machine-readable so the source repository can watch
this copy for drift from the side that knows when the source changed.
