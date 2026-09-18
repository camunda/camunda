# Upload Artifact With Retry Action

## Intro

Uploads a build artifact via
[`actions/upload-artifact`](https://github.com/actions/upload-artifact), retrying up to three
times. A transient GitHub artifact-storage error used to fail an otherwise-green job: every test
step passed and only the upload was refused, which paged a medic for a GitHub-side blip
(INC-7913).

The action's own retry does not cover this. `upload-artifact` delegates to `@actions/artifact`,
whose client retries five times with backoff — but only on 429, 500, 502, 503 and 504. The `403
Forbidden` seen on `FinalizeArtifact` is classified **non-retryable** and fails the step
immediately, even though the blob content has already been written. No input or environment
variable widens that list ([upload-artifact#530](https://github.com/actions/upload-artifact/issues/530)
is the open request), so the retry has to live in the caller.

Retries in a workflow are normally shell-command-only
([`nick-fields/retry`](https://github.com/nick-fields/retry), as used by
[`npm-ci-with-retry`](../npm-ci-with-retry)), and GitHub Actions has no retry primitive for a
`uses:` step. So each attempt here is a literal copy of the step, gated on the previous attempt's
`outcome`. See [Notes](#notes) for what that means when you edit this action.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is
  referenced by local path.
- **The artifact name must be unique within the workflow run.** See the `overwrite` note below.

## Usage

### Inputs

|        Input         |                         Description                          | Required | Default |
|----------------------|--------------------------------------------------------------|----------|---------|
| name                 | Artifact name                                                | true     |         |
| path                 | File, directory or wildcard pattern to upload                | true     |         |
| retention-days       | Days to keep the artifact; empty uses the repository default | false    | `""`    |
| include-hidden-files | Whether to include hidden files under `path`                 | false    | `false` |
| if-no-files-found    | `warn`, `error` or `ignore`                                  | false    | `warn`  |

### Outputs

None. `upload-artifact`'s own outputs (`artifact-id`, `artifact-url`, `artifact-digest`) are not
forwarded — no caller needs them. Forwarding them would also be misleading here, since which
attempt produced the value is not visible to the caller.

## Notes

- **Three attempts, 10s apart, hard-coded.** Retry-by-repetition cannot be parameterised: a
  composite action has no loop, so `max_attempts` would have to change the number of steps in the
  file. Change the count by adding or removing an attempt block.
- **`overwrite: true` is load-bearing, not cosmetic.** A failed attempt leaves the artifact name
  reserved, so the next attempt would fail with `Failed to CreateArtifact: (409) Conflict` — also
  non-retryable, which makes the 403-then-409 sequence terminal. This is the failure mode
  described in [dotnet/orleans#10961](https://github.com/dotnet/orleans/issues/10961). Without
  `overwrite`, the retry would convert one error into a different one instead of recovering.
- **`overwrite: true` is only safe because callers use unique names.** It is not atomic: when two
  parallel jobs upload the *same* artifact name, they race between the delete and the create and
  still fail ([upload-artifact#506](https://github.com/actions/upload-artifact/issues/506)).
  Every current caller uploads a name unique within the run (the sharded ones are suffixed with
  `matrix.shardIndex`). Check that before adding a caller. Also avoid combining `overwrite` with
  `archive: false`, which is broken
  ([upload-artifact#769](https://github.com/actions/upload-artifact/issues/769)).
- **This protects against a blip, not an outage.** The four INC-7913 failures fell inside a ~57
  second window, each refused within 1–20s, while sibling shards of the same matrix uploaded
  successfully — three attempts 10s apart absorb that. They do not absorb a sustained outage: the
  throttling window behind INC-7723 lasted 30 minutes, and no attempt count would have saved
  those jobs. Do not reach for these actions expecting otherwise.
- **Editing one attempt means editing all three.** The `with:` blocks must stay identical,
  including the pinned `actions/upload-artifact` SHA. A mismatch means an attempt silently
  uploads something different from its predecessor.
- **The last attempt deliberately omits `continue-on-error`**, so an exhausted retry fails the
  job. Attempts 1 and 2 carry it, which is what lets the next attempt run.
- **Gating on the immediate predecessor is enough.** A skipped step reports `outcome: skipped`,
  never `failure`, so attempt 3 can only run if attempt 2 ran and failed — which in turn required
  attempt 1 to fail. No cumulative `&&` chain is needed. The gates deliberately omit `always()`:
  a cancelled run must not keep sleeping and retrying.
- **A failed attempt still emits its error annotations.** `continue-on-error` absorbs the *step*,
  but the annotation is already published to the check run and annotations have no notion of being
  absorbed — so a green job can carry red annotations from this action.
  [`post-ci-failure-reasons`](../post-ci-failure-reasons) filters on job conclusion before it
  reads annotations, so a job that recovered produces no PR comment. The residual effect: if the
  job later fails for an unrelated reason, that comment will list these already-retried errors
  alongside the real one.
- **A retry is cheap on a green run but not free on a red one.** Attempts 2 and 3 report `skipped`
  when the upload succeeds, so a passing job pays nothing. A genuinely broken upload (a bad
  `path`, say) costs all three attempts plus 20s of sleep before the job reports red.
- **Retrying the upload does not make a lost artifact visible.** If an artifact goes missing
  anyway, a consumer that globs for it can silently proceed with fewer files — assert the expected
  count at the consuming end rather than trusting the upload.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/upload-artifact-with-retry
    with:
      name: operate-a11y-report
      path: operate/client/playwright-report/
      retention-days: "30"
```

For Maven test reports, prefer [`collect-test-artifacts`](../collect-test-artifacts), which
already wraps the upload with its own defaults.
