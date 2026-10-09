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

## Running it locally

From the **repository root** — the validator resolves each spec `path` against
the working directory, which is what lets it check a plan against the suite
rather than only against itself:

```bash
npm ci --prefix .github/scripts/coverage-plan-check
node .github/scripts/coverage-plan-check/coverage-plan.mjs validate qa/coverage
node .github/scripts/coverage-plan-check/coverage-plan.mjs summary qa/coverage/<plan>.yml
```

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

## Vendoring

`coverage-plan.mjs` is **vendored**, not written here. Its source of truth, its
schema and its tests live in `camunda/c8-cross-component-e2e-tests`
(`scripts/coverage-plan.mjs`). [`vendor.json`](vendor.json) records the exact
revision this copy came from.

Do not edit the copy. A local edit makes this repository enforce a schema no plan
author is writing against, and the next re-sync silently reverts it. Change it
upstream, then re-sync:

```bash
# from a checkout of camunda/c8-cross-component-e2e-tests at the new revision
cp scripts/coverage-plan.mjs <camunda>/.github/scripts/coverage-plan-check/coverage-plan.mjs
# restore the VENDORED FILE header at the top of the copy, then record the revision
git -C . rev-parse HEAD   # paste into vendor.json's source_ref
```

The tradeoff vendoring buys and what it costs: the check needs no credential for
a private repository and no network at validation time, so it runs on every PR
including forks — but the copy drifts from the schema it enforces unless someone
re-syncs. `vendor.json` is machine-readable so the source repository can watch
this copy for drift from the side that knows when the source changed.
