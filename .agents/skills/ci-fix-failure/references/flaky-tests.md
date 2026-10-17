# Category B — Flaky Tests

A flake is a test that fails non-deterministically. Don't reach for this label without evidence —
"unrelated to my diff" is necessary but not sufficient. Repo policy is to **keep the test enabled**
and file a tracking issue.

## Confirming flake (do this before claiming it)

1. The failing assertion is unrelated to the diff (no file overlap, no transitive call path).
2. At least one of:
   - The same test has a recent passing run on the same `headSha` (`gh run list --workflow <name> --commit <sha>`).
   - The test or a sibling test has prior failures with a different cause (`gh issue list --label kind/flake --search "<test name>"`).
   - The stack trace shows known flake shapes: timeout, awaitility wait expired, "expected port to be free", container/network race, eventual-consistency assertion without retry.
3. A rerun of just the failed jobs passes:
   ```bash
   gh run rerun --failed <run-id> --repo camunda/camunda
   ```
   Recommend this to the user — don't trigger it yourself.

## Repo policy

From `AGENTS.md`:

> If it is flaky (non-deterministic):
> 1. Search for an existing open issue in camunda/camunda. If none exists, raise one using
>    the `create-issue` skill (use the bug template; also add the `kind/flake` label).
> 2. Assign the issue to the engineer.
> 3. Treat the baseline as passed and proceed — do not disable the test.

There is also an automated **flaky-test gate** documented at
[docs/monorepo-docs/flaky-test-gate.md](../../../../docs/monorepo-docs/flaky-test-gate.md). Read
it before proposing manual mitigation — the gate may already be doing the work.

The broader repo policy on flakes is in the "flaky tests" section of
[docs/monorepo-docs/ci.md](../../../../docs/monorepo-docs/ci.md): tests should retry 3–5 times
within the configured timeout and report via the detailed test statistics API into CI health
metrics. Any in-test retry change must follow this pattern, not invent its own.

## What to propose to the user

```
Likely flake: <Test FQN> — <one-line reason>.
Existing issue: <link or "none found">.
Recommended next steps:
  1. Rerun the failed jobs: gh run rerun --failed <run-id> --repo camunda/camunda
  2. If no tracking issue exists, file one with the `create-issue` skill (label kind/flake).
  3. Do NOT disable, @Disabled, or skip the test.
```

If the test is genuinely racy and there's a clean fix (e.g. replace `Thread.sleep` with
Awaitility, or add a retry on an eventually-consistent assertion), offer that as follow-up — but separate from the rerun.
