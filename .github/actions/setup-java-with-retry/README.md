# Setup Java With Retry Action

## Intro

Installs the JDK via [`actions/setup-java`](https://github.com/actions/setup-java), retrying up to
three times. Resolving the release metadata and downloading the JDK both go over the network, and a
transient failure there used to fail the whole job (INC-7417).

Retries in a workflow are normally shell-command-only
([`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry)), and GitHub Actions has no retry primitive for a `uses:`
step. So each attempt here is a literal copy of the step, gated on the previous attempt's `outcome`.
See [Notes](#notes) for what that means when you edit this action.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is referenced
  by local path.

## Usage

### Inputs

|    Input     |         Description          | Required | Default |
|--------------|------------------------------|----------|---------|
| distribution | Java distribution to install | true     |         |
| java-version | Java version to install      | true     |         |

### Outputs

None. `setup-java`'s own outputs are not forwarded — no caller needs them. `JAVA_HOME` and `PATH` are
exported to the job as usual, since the runner applies `GITHUB_ENV`/`GITHUB_PATH` writes from a
nested composite action exactly as it does for a top-level step.

## Notes

- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **The whole retry window is ~39 seconds.** Measured in
  [run 34333710709](https://github.com/camunda/camunda/actions/runs/34333710709): a failing
  `setup-java` takes ~6.2s per attempt, so three attempts plus two 10s gaps give up 39s after the
  step started. This protects against a blip, **not an outage.** The throttling window behind
  INC-7723 lasted 30 minutes; neither this configuration nor the `Wandalen/wretry.action` one it
  replaced (identical `attempt_limit: 3` / `attempt_delay: 10000`) would have saved a single one of
  those jobs. What fixes that failure mode is that nothing is fetched at runtime any more — see
  below. Do not reach for these actions expecting them to absorb a sustained outage.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `actions/setup-java` SHA. A mismatch means an attempt silently installs something
  different from its predecessor.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*, but
  the annotation is already published to the check run and annotations have no notion of being
  absorbed — so a green job can carry red annotations from this action.
  [`post-ci-failure-reasons`](../post-ci-failure-reasons) filters on job conclusion before it reads
  annotations, so a job that recovered produces no PR comment. The residual effect: if the job later
  fails for an unrelated reason, that comment will list these already-retried errors alongside the
  real one.
- No failure classification: a genuine error (a bad `java-version`, say) costs all three attempts
  before the job reports red.

This replaced `Wandalen/wretry.action`, which fetched the action it wrapped with an anonymous runtime
`git clone` — outside its own retry loop, and the single point of failure behind INC-7723. See
[#62493](https://github.com/camunda/camunda/pull/62493).

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/setup-java-with-retry
    with:
      distribution: temurin
      java-version: "21"
```

Most jobs should not call this directly — [`setup-build`](../setup-build) already does, along with
the rest of the build bootstrap.
