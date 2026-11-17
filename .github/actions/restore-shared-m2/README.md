# Restore Shared m2 SNAPSHOTs Action

## Intro

Restores the `io.camunda` `-SNAPSHOT` artifacts that the `build-distball` job installed
locally and archived to the GCS build-cache as `m2-installed.tar`, so downstream
`mvn verify` runs see them without re-running the reactor (saves ~2–15 min per job; see
issue camunda/camunda#52693).

It downloads the run-scoped `m2-installed.tar` and extracts it into
`~/.m2/repository/installed`. The distball tarball itself (`camunda-zeebe-*.tar.gz`) is
**not** restored here — callers that need it download it separately, since not every
consumer does.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this action
  is referenced by local path.
- gcloud must already be authenticated against the build-cache bucket via a prior
  [`gcs-build-cache-auth`](../gcs-build-cache-auth) step.
- `GCS_BUILD_CACHE_BUCKET` must be set at the workflow level, and the job must run in the
  same `github.run_id` as the `build-distball` job that produced the tarball.

## Usage

### Inputs

None.

### Outputs

None. Extracts `-SNAPSHOT`s into `~/.m2/repository/installed` on the runner.

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/setup-build
    with:
      vault-address: ${{ secrets.VAULT_ADDR }}
      vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
      vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
  - name: Authenticate to GCS build-cache  # authenticates gcloud for the restore below
    timeout-minutes: 3
    uses: ./.github/actions/gcs-build-cache-auth
    with:
      vault-addr: ${{ secrets.VAULT_ADDR }}
      vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
      vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
  - uses: ./.github/actions/restore-shared-m2
```

