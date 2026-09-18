# Upload Artifact With Retry Action

## Intro

Uploads a build artifact via
[`actions/upload-artifact`](https://github.com/actions/upload-artifact), retrying up to three
times. A transient GitHub artifact-storage error used to fail an otherwise-green job: every test
step passed and only the upload was refused (INC-7913).

`upload-artifact`'s own client retries on 429, 500, 502, 503 and 504, but a `403 Forbidden` on
`FinalizeArtifact` is non-retryable, so the step fails immediately even though the blob was
already written. No input widens that list
([upload-artifact#530](https://github.com/actions/upload-artifact/issues/530)), so the retry has
to live in the caller.

GitHub Actions has no retry primitive for a `uses:` step, so each attempt here is a literal copy
gated on the previous attempt's `outcome`. See [Notes](#notes) before editing this action.

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
| retry-delay-seconds  | Base seconds to wait before each retry; `0` retries at once  | false    | `10`    |
| retry-jitter-seconds | Inclusive upper bound on a random wait added to the delay    | false    | `10`    |

### Outputs

|   Output   |                           Description                            |
|------------|------------------------------------------------------------------|
| `attempts` | Number of upload attempts made: `1` when the first one succeeded |

More than `1` means a retry was needed, and `0` that invalid backoff inputs stopped the action
before it tried. `attempts` exists because a successful retry leaves the job green, hiding the
absorbed failure. The action also emits a warning annotation and a run-summary line in that case.

## Notes

- **Three attempts, hard-coded.** A composite action has no loop, so change the attempt count by
  adding or removing an attempt block.
- **The backoff is configurable, and jittered by default.** Each wait is
  `retry-delay-seconds + RANDOM % (retry-jitter-seconds + 1)`, so the defaults give 10-20s
  inclusive. The jitter keeps matrix shards off a lockstep retry, so only drop it
  (`retry-jitter-seconds: "0"`) for a single unsharded upload.
- **`overwrite: true` is required.** A failed attempt reserves the artifact name, so a retry
  without it hits a non-retryable 409
  ([dotnet/orleans#10961](https://github.com/dotnet/orleans/issues/10961)).
- **`overwrite: true` is only safe with unique names.** Two parallel jobs uploading the same name
  race and still fail ([upload-artifact#506](https://github.com/actions/upload-artifact/issues/506)).
  Every current caller uses a name unique within the run. Also avoid combining `overwrite` with
  `archive: false` ([upload-artifact#769](https://github.com/actions/upload-artifact/issues/769)).
- **This protects against a blip, not an outage.** INC-7913's failures cleared within a ~57s
  window. A sustained outage like INC-7723 (30 minutes) would exhaust all three attempts.
- **Editing one attempt means editing all three.** Keep the `with:` blocks identical.
- **The last attempt omits `continue-on-error`**, so an exhausted retry fails the job.
- **A failed attempt still emits its error annotations**, since `continue-on-error` absorbs the
  step but not the annotation already published to the check run.
- **Retrying the upload does not make a lost artifact visible.** Assert at the consuming end by
  name, not by file count. The report-merge jobs in
  [`ci-operate.yml`](../../workflows/ci-operate.yml) do this.

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
