# GCS Build-Cache Auth Action

## Intro

Authenticates `gcloud` against the run-scoped GCS build-cache bucket
(`camunda-monorepo-ci-artifacts`) used to share the Zeebe distball and the
locally-installed `m2` SNAPSHOT tarball across jobs (see #52693).

It bundles the three steps every build-cache consumer repeated verbatim:

1. Fetch the `monorepo-build-cache-sa` service account from Vault (WIF provider +
   service account, no long-lived key).
2. Authenticate via `google-github-actions/auth` using Workload Identity Federation.
3. Install the gcloud CLI via `google-github-actions/setup-gcloud`.

After this action runs, the caller issues its own `gcloud storage` command
(`cp` upload/download, or `rm` cleanup) — the storage operation stays at the call
site so upload/download/delete intent is visible where it happens.

Most build jobs get this indirectly: `setup-build` nests this action behind its
opt-in `gcs-build-cache-auth: true` input (auto-disabled for fork PRs). Call this
action directly when either:

- the job has no build stack (no `setup-build`), e.g. the lightweight
  `check-results` cleanup; or
- a long step runs between `setup-build` and the `gcloud storage` command (e.g.
  `build-distball`'s ~15-min build), so auth must happen late to keep the WIF/OIDC
  token fresh.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this
  action is referenced by local path.
- The calling job needs `permissions: id-token: write` so `google-github-actions/auth`
  can mint the OIDC token for WIF.

## Usage

### Inputs

|      Input      |                     Description                     | Required | Default |
|-----------------|-----------------------------------------------------|----------|---------|
| vault-addr      | Vault address (`secrets.VAULT_ADDR`)                | true     |         |
| vault-role-id   | Vault AppRole role id (`secrets.VAULT_ROLE_ID`)     | true     |         |
| vault-secret-id | Vault AppRole secret id (`secrets.VAULT_SECRET_ID`) | true     |         |

### Outputs

None. Authentication state is applied to the runner environment; downstream
`gcloud` / `gcloud storage` steps in the same job pick it up automatically.

## Example

```yaml
jobs:
  consume-distball:
    permissions:
      contents: read
      id-token: write  # required for WIF auth
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/gcs-build-cache-auth
        with:
          vault-addr: ${{ secrets.VAULT_ADDR }}
          vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
          vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
      - name: Download distball from GCS
        shell: bash
        run: |
          set -euo pipefail
          gcloud storage cp \
            "gs://${GCS_BUILD_CACHE_BUCKET}/${GITHUB_RUN_ID}/m2-installed.tar" \
            "${RUNNER_TEMP}/m2-installed.tar"
```

