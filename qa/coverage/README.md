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
4. **The test does not carry its case id.** Add the marker — see *Carrying a
   case id in a test* in the check's README.

