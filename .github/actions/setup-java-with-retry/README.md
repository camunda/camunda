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
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical, including
  the pinned `actions/setup-java` SHA. A mismatch means an attempt silently installs something
  different from its predecessor.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the job.
  Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`, never
  `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required attempt 1
  to fail. No cumulative `&&` chain is needed.
- **A failed attempt still emits its error annotations.** They stay in the job log even when a later
  attempt succeeds, so a green job can contain red annotations from this action.
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
