# GCS Build-Cache Auth Action

## Intro

Authenticates `gcloud` against the run-scoped GCS build-cache bucket
(`camunda-monorepo-ci-artifacts`) used to share the Zeebe distball and the
locally-installed `m2` SNAPSHOT tarball across jobs (see #52693).

It bundles the four steps every build-cache consumer repeated verbatim:

1. Detect fork PRs (via [`is-fork`](../is-fork)) and skip Vault/WIF auth entirely
   when running on one, since secrets aren't available there.
2. Fetch the `monorepo-build-cache-sa` service account from Vault (WIF provider +
   service account, no long-lived key).
3. Authenticate via `google-github-actions/auth` using Workload Identity Federation.
4. Install the gcloud CLI via `google-github-actions/setup-gcloud`.

After this action runs, the caller issues its own `gcloud storage` command
(`cp` upload/download, or `rm` cleanup) — the storage operation stays at the call
site so upload/download/delete intent is visible where it happens.

Call this action directly as its own job step, right alongside (not nested inside)
`setup-build` when the build stack is also needed. It is **not** wired into
`setup-build` itself: composite actions can't set `timeout-minutes` on their own
steps (only a workflow step calling a `uses:` action can), so this action must stay
a direct, top-level step in the workflow for its caller to bound a Vault/WIF hang
with `timeout-minutes` — see INC-6820, where a silent WIF hang inside a nested
call consumed an entire job's timeout budget before GitHub killed the job.
Always set `timeout-minutes` (e.g. `3`) on the call site.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this
  action is referenced by local path.
- The calling job needs `permissions: id-token: write` so `google-github-actions/auth`
  can mint the OIDC token for WIF.
- The calling *step* should set `timeout-minutes` (e.g. `3`) so a Vault/WIF hang
  fails fast instead of silently consuming the job's whole timeout budget.

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
      - name: Authenticate to GCS build-cache
        timeout-minutes: 3  # fail fast on a Vault/WIF hang -- see INC-6820
        uses: ./.github/actions/gcs-build-cache-auth
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

