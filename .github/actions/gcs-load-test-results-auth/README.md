# GCS Load-Test-Results Auth Action

## Intro

Authenticates `gcloud` against the `camunda-benchmark-load-test-results-prod`
bucket used to persist automated load test results (metrics and flamegraphs)
beyond the 90-day retention of GitHub Actions artifacts.

It bundles the three steps every load-test-results uploader needs:

1. Fetch the `load-test-results-sa` service account from Vault (WIF provider +
   service account, no long-lived key).
2. Authenticate via `google-github-actions/auth` using Workload Identity Federation.
3. Install the gcloud CLI via `google-github-actions/setup-gcloud`.

After this action runs, the caller issues its own `gcloud storage cp` command —
the upload intent stays at the call site.

Always set `timeout-minutes` (e.g. `3`) on the call site step, since composite
actions can't set `timeout-minutes` on their own steps.

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
  upload-results:
    permissions:
      contents: read
      id-token: write  # required for WIF auth
    steps:
      - uses: actions/checkout@v6
      - name: Authenticate to GCS load-test-results
        timeout-minutes: 3  # fail fast on a Vault/WIF hang
        uses: ./.github/actions/gcs-load-test-results-auth
        with:
          vault-addr: ${{ secrets.VAULT_ADDR }}
          vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
          vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
      - name: Upload results to GCS
        shell: bash
        run: |
          set -euo pipefail
          gcloud storage cp -r results/ \
            "gs://${GCS_LOAD_TEST_RESULTS_BUCKET}/automated/daily/stress/${BENCHMARK}/"
```

