# Category A — Code / Test Failures

The change under test is genuinely broken. Goal: name the smallest edit that turns the job green and is in line with best practices from [docs/monorepo-docs/ci.md](../../../../docs/monorepo-docs/ci.md).

## Sub-types and how to dig in

### Java compile error

The log names the file and line. Read that file at `headSha` (`gh api repos/camunda/camunda/contents/<path>?ref=<sha>` or just `git show <sha>:<path>` if checked out). Common shapes:

- Missing import after a refactor in a dependency module — check sibling modules for the new symbol location.
- Method signature changed in an upstream module — find callers with `grep -r <method>` and update them.
- Generic/nullness issue — check whether the file uses `@NullMarked` (the repo is mid-migration to jspecify).

### Spotless / license / format failure

Log will include "spotless" or "license-maven-plugin". The fix is always:

```bash
./mvnw license:format spotless:apply -T1C
```

Recommend exactly this — do not propose hand-edits.

### JUnit / AssertJ test failure

From the log, get the test class FQN, method, and the assertion message. Then:

1. Read the failing test file at `headSha`.
2. Read the production class under test.
3. Determine whether the test is asserting the right thing for the new behavior, or whether the production change broke an invariant.

If the diff changed production code: the test is usually correct and the production code needs fixing. If the diff changed only the test: the test is wrong. If the diff is unrelated, treat as a candidate flake (Category B) — but first verify it really is unrelated, not a hidden coupling.

To re-run that single test locally:

```bash
./mvnw verify -pl <module> -Dtest=<TestClassName>#<methodName> -DskipTests=false -DskipITs -Dquickly
```

For integration tests:

```bash
./mvnw verify -pl <module> -Dit.test=<ITClassName> -DskipTests=false -DskipUTs -Dquickly
```

Use the module from the test's path (e.g. `zeebe/engine`, not `zeebe`).

### Frontend test failure (Vitest / Playwright)

Tests live under `webapp/client/`, `operate/client/`, `tasklist/client/`. The log will reference a `.test.ts(x)` or `.spec.ts` file. Read the test and the source it imports.

- Vitest unit test: invoke the `frontend-unit-test` skill for conventions.
- Playwright: invoke the `frontend-integration-test` skill.
- Migrated to OC webapp: see `frontend-migrator`.

### GHA per-job timeout (cancelled job)

Job conclusion is `cancelled`, annotations contain `The job has exceeded the maximum execution
time of <N>m<S>s` plus a `<TestClass.method> -- Time elapsed: <N> s <<< ERROR!` entry naming the
offending test. The test hung or ran too slowly to complete within the workflow's
`timeout-minutes`.

Treat this as a code/test failure. The fix is one of:

- A genuine bug in the test or the production code under test (deadlock, infinite loop,
  missing termination condition) → diagnose like any other test failure.
- Test is doing too much work (oversized fixture, exhaustive sweep where a property test would
  do) → reshape the test.
- The whole test job is now over budget — split it.

Re-run that test locally first to confirm it reproduces:

```bash
./mvnw verify -pl <module> -Dtest=<TestClassName>#<methodName> -DskipTests=false -DskipITs -Dquickly
```

If it does not reproduce locally and the test has prior timeout history, it may be flaky —
consult Category B before proposing a fix.

### Maven build / dependency error

"Could not resolve dependencies", "module not found", or a snapshot version mismatch. Often a sibling module wasn't installed locally. Recommend:

```bash
./mvnw install -pl <module> -am -Dquickly -T1C
```

…to rebuild dependencies, then re-run the failing step.

## Propose the fix

Be specific: name the file, the line range, and the exact change. Show a short snippet of the
edit if helpful. Then offer the verification command from the section above. Wait for the user's
go-ahead before editing.
