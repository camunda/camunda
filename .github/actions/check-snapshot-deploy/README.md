# Check Snapshot Deploy Action

## Intro

Decides whether a snapshot deploy job may publish its snapshot when it runs as a re-run (attempt 2
or later), and tells the user what to do instead when it may not.

Re-running an older `CI` run re-executes its snapshot deploy jobs. Two things can go wrong:

- **The snapshot is rolled back** ([#67319](https://github.com/camunda/camunda/issues/67319)). If
  newer commits landed on the branch since, the re-run
  publishes `X.Y-SNAPSHOT` from its older commit. Every consumer (nightly E2E/API suites, preview
  environments, Helm integration, developers) then runs outdated code until the next push
  re-deploys, and nothing shows it.
- **The job fails on expired artifacts.** The deploy jobs download frontend bundles that
  [`ci.yml`](../../workflows/ci.yml) keeps for only 2 days (`retention-days: 2`). Each failed job
  raised its own L1 `base-branch-unsuccessful-job` incident (INC-8805, INC-8806, INC-8807).

On a re-run, this action therefore:

1. **Skips the deploy when the run's commit is no longer the branch head.** It sets `deploy=false`
   and the job ends green: the run for the newer commit owns the snapshot, so nothing failed.
2. **Fails when the run is a re-run of the branch head older than `max-age-hours`**, because its
   artifacts have expired. It sets `stale=true`, so the caller can report the failure to CI
   analytics with `user_reason: stale-rerun`.

## Scope

Only for the Unified CI snapshot deploy jobs in [`ci.yml`](../../workflows/ci.yml):
`deploy-snapshots`, `deploy-camunda-docker-snapshot` and `deploy-optimize-docker-snapshot`.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action is
  referenced by local path.
- The job's `GITHUB_TOKEN` needs `contents: read` to read the branch head and `actions: read` to
  read when the run was created.
- `curl`, `jq` and GNU `date` on the runner. The action doesn't use the `gh` CLI, because it isn't
  installed on the self-hosted runners (INC-8786).

## Usage

### Inputs

| Input | Description | Required | Default |
|---|---|---|---|
| `github-token` | Token with `contents: read` and `actions: read` | true | |
| `max-age-hours` | Maximum run age for a re-run of the branch head; keep in sync with the `retention-days` of downloaded artifacts | false | `48` |

### Outputs

| Output | Description |
|---|---|
| `deploy` | `'false'` when the caller must skip the deploy steps, otherwise `'true'` |
| `stale` | `'true'` when this is a re-run of the branch head older than `max-age-hours`, otherwise `'false'` |

### Behavior

| Situation | Result |
|---|---|
| First attempt (`run_attempt == 1`) | Passes, `deploy=true`, no API call |
| Re-run, commit is no longer the branch head | Passes with a notice and a step-summary note, `deploy=false` |
| Re-run of the branch head within `max-age-hours` | Passes, `deploy=true` |
| Re-run of the branch head older than `max-age-hours` | Fails with an `::error::` and a step-summary note, `deploy=false`, `stale=true` |
| GitHub API unreachable | Passes with a warning, `deploy=true` (fail open) |
| `github-token` empty | Fails |

## Notes

- **Gate every later step with `if: steps.<id>.outputs.deploy == 'true'`**, except
  `observe-build-status`. GitHub can't end a job early with success, so a skipped deploy has to
  skip each step.
- **Add it as a step in each job that needs it, not as a separate guard job.** With "Re-run failed
  jobs", GitHub doesn't re-run jobs that already passed and reuses their old outputs, so a guard job
  would keep the answer from the first attempt.
- **Keep reporting to `observe-build-status`, with `user_reason: stale-rerun` when `stale` is
  `'true'`.** CI analytics should still see the failure. The reason lets the
  `base-branch-unsuccessful-job` alert exclude it by cause, as it does for `agent-disconnected`.
- **First attempts aren't checked.** They deploy in arrival order through the jobs' concurrency
  queue, which almost always matches commit order. On busy branches the head moves before about
  half of all first-attempt deploys start, so a head check there would hold the snapshot back for
  long stretches.
- **It fails open.** An API error must never block a legitimate deploy, so the worst case is the
  previous behavior.
- **Age is measured from the run's `created_at`**, not from the commit time or `run_started_at`
  (which a re-run resets). The artifacts are uploaded early in the first attempt, so `created_at`
  is a close, slightly conservative stand-in for when their retention started.
- **A small race remains.** A commit can land between the head check and the push. The run for
  that commit then deploys after this one, through the same concurrency queue.

## Example

```yaml
jobs:
  deploy-snapshot:
    permissions:
      actions: read  # check-snapshot-deploy reads the run's creation time
      contents: read  # check-snapshot-deploy reads the branch head
    steps:
      - uses: actions/checkout@v6
      - name: Check whether to deploy the snapshot
        id: deploy-check
        uses: ./.github/actions/check-snapshot-deploy
        with:
          github-token: ${{ github.token }}
      - uses: actions/download-artifact@v8
        if: steps.deploy-check.outputs.deploy == 'true'
        with:
          name: fe-operate-${{ github.run_id }}
      # ... every further deploy step gated the same way ...
      - name: Observe build status
        if: always()
        continue-on-error: true
        uses: ./.github/actions/observe-build-status
        with:
          build_status: ${{ job.status }}
          user_reason: ${{ steps.deploy-check.outputs.stale == 'true' && 'stale-rerun' || '' }}
```

See also the runbook entry
[Re-run of an old workflow run in the deploy jobs](../../../docs/monorepo-docs/ci-runbooks.md#re-run-of-an-old-workflow-run-in-the-deploy-jobs).
