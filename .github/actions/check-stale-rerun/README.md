# Check Stale Re-run Action

## Intro

Fails a job early when it runs as a re-run (attempt 2 or later) of a workflow run older than
`max-age-hours`, and tells the user to start a new run instead.

The snapshot deploy jobs in [`ci.yml`](../../workflows/ci.yml) download frontend bundles that are
kept for only 2 days (`retention-days: 2`). If someone re-runs an older run, those downloads fail.
Even if they didn't, the jobs would publish an outdated snapshot. Each failed job used to raise its
own L1 `base-branch-unsuccessful-job` incident (INC-8805, INC-8806, INC-8807). This action makes the
failure explicit, and gives callers a `stale` output they use to skip `observe-build-status`, so no
alert fires.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is
  referenced by local path.
- The job's `GITHUB_TOKEN` needs `actions: read` to look up when the run was created.
- `curl`, `jq` and GNU `date` on the runner. The action doesn't use the `gh` CLI, because it isn't
  installed on the self-hosted runners (INC-8786).

## Usage

### Inputs

|      Input      |                                         Description                                          | Required | Default |
|-----------------|----------------------------------------------------------------------------------------------|----------|---------|
| `github-token`  | Token with `actions: read`, used to read the run's `created_at`                              | true     |         |
| `max-age-hours` | Maximum run age for a re-run; keep in sync with the `retention-days` of downloaded artifacts | false    | `48`    |

### Outputs

| Output  |                                       Description                                       |
|---------|-----------------------------------------------------------------------------------------|
| `stale` | `'true'` when this is a re-run of a run older than `max-age-hours`, otherwise `'false'` |

### Behavior

|                 Situation                 |                             Result                              |
|-------------------------------------------|-----------------------------------------------------------------|
| First attempt (`run_attempt == 1`)        | Passes, `stale=false`, no API call                              |
| Re-run within `max-age-hours`             | Passes, `stale=false`                                           |
| Re-run older than `max-age-hours`         | Fails with an `::error::` and a step-summary note, `stale=true` |
| GitHub API unreachable or no `created_at` | Passes with a warning, `stale=false` (fail open)                |
| `github-token` empty                      | Fails, `stale=false`                                            |

## Notes

- **Add it as a step in each job that needs it, not as a separate guard job.** With "Re-run failed
  jobs", GitHub doesn't re-run jobs that already passed and reuses their old outputs, so a guard job
  would keep reporting "not stale".
- **Skip `observe-build-status` when `stale` is `'true'`.** Reporting `aborted` or `cancelled`
  instead isn't enough: the base-branch alert counts any unsuccessful job.
- **It fails open.** An API error must never block a legitimate deploy, so the worst case is the
  previous behavior.
- **Age is measured from the run's `created_at`**, not from the commit time or `run_started_at`
  (which a re-run resets). The artifacts are uploaded early in the first attempt, so `created_at`
  is a close, slightly conservative stand-in for when their retention started.
- **A re-run within the window can still deploy an older commit** if newer commits landed since.
  This action doesn't cover that case.

## Example

```yaml
jobs:
  deploy-snapshot:
    permissions:
      actions: read  # check-stale-rerun reads the run's creation time
    steps:
      - uses: actions/checkout@v6
      - name: Check for stale re-run
        id: stale-rerun
        uses: ./.github/actions/check-stale-rerun
        with:
          github-token: ${{ github.token }}
      # ... download artifacts and deploy ...
      # A stale re-run fails by design; don't page the medic for it.
      - name: Observe build status
        if: always() && steps.stale-rerun.outputs.stale != 'true'
        continue-on-error: true
        uses: ./.github/actions/observe-build-status
        with:
          build_status: ${{ job.status }}
```

See also the runbook entry
[Re-run of an old workflow run fails in the deploy jobs](../../../docs/monorepo-docs/ci-runbooks.md#re-run-of-an-old-workflow-run-fails-in-the-deploy-jobs).
