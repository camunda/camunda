# Coverage plans

One YAML file per epic, listing the automated cases that epic needs and where
each one lives. A plan is **reviewed by a human before any test is generated** —
the duplication and routing judgements (what is already covered a layer down,
what belongs at which level, what is out of scope) are the point of it, and they
are not judgements a generator should make.

See camunda/team-test-automation#83 for the programme, and
[`product-hub-3526.yml`](product-hub-3526.yml) for a worked example spanning
service unit tests, acceptance ITs, Operate component tests, and the REST and UI
coverage still to be written.

## Who reads these

- **The coverage gate** in
  [`camunda/c8-cross-component-e2e-tests`](https://github.com/camunda/c8-cross-component-e2e-tests)
  reports a plan's status onto product PRs for its epic. It reports *from* the
  plan, so a wrong status is read as fact.
- **CI here** validates every plan against this repository's suite on each
  relevant PR — see
  [`.github/scripts/coverage-plan-check/`](../../.github/scripts/coverage-plan-check/)
  for the schema, the drift rules, and how to run it locally.

## If CI says a plan no longer matches the suite

The usual causes, in order of likelihood:

1. **A test moved or was renamed.** Update the spec `path` in the plan.
2. **A test was deleted.** The case is no longer covered — set it back to
   `planned`, or `excluded` with `already_covered_by` naming what covers it now.
   Do not leave it `automated`.
3. **A new test exists for a `planned` case.** Flip it to `automated`. This one
   is a warning, not an error: the suite is ahead of its paperwork.
4. **The case id is missing from a generated spec.** Only applies where the
   check runs with case ids on, which this repository's job does not — see
   *Why the id check is off here* in the check's README.

## Known gap: a plan can under-report silently

The checks catch a plan claiming coverage that is gone. They do **not** catch a
plan that forgot to record coverage that landed, unless the suite carries case
ids — and this repository's does not.

`product-hub-3526.yml` is in that state today: every `SR-API-*` case is marked
`planned`, but the four spec files they name exist on `main` with 40 tests
between them, and `processInstanceSuspendResume.spec.ts` has 10 more. The
coverage gate reports from the plan, so it has been under-reporting this epic.
Confirming which cases those tests actually satisfy is a review judgement, not
a mechanical one, so it is not done here.
