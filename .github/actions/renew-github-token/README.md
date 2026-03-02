# Renew GitHub Token

Generates a fresh GitHub App token via HashiCorp Vault and safely replaces the
git credential set by `actions/checkout@v6`, avoiding duplicate `Authorization`
headers that cause HTTP 400 errors.

## Why this is needed

`actions/checkout@v6` stores the `http.*.extraheader` credential in an external
temp config file (`$RUNNER_TEMP/git-credentials-<uuid>.config`) and references it
from `.git/config` via `includeIf.gitdir` directives. The GitHub App token
expires after 1 hour. In long-running jobs (e.g. Maven release), the token may
expire before `git push`.

A naïve `git config --add` to inject a renewed token creates a **second**
extraheader entry — one in `.git/config` and the original in the external file —
causing git to send two `Authorization` headers, which GitHub rejects with
HTTP 400 `"Duplicate header: Authorization"`.

This action properly clears both locations and writes the renewed token to the
original credential file used by checkout@v6.

**References:**
- [checkout@v6 persist-creds change (PR #2286)](https://github.com/actions/checkout/pull/2286)
- [Token expiry issue (camunda/camunda#28522)](https://github.com/camunda/camunda/issues/28522)

## Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `vault-url` | Yes | — | Vault server URL |
| `vault-role-id` | Yes | — | Vault AppRole role ID |
| `vault-secret-id` | Yes | — | Vault AppRole secret ID |
| `github-app-id-vault-key` | No | `MONOREPO_RELEASE_APP_ID` | Vault key for the GitHub App ID |
| `github-app-id-vault-path` | No | `secret/data/products/camunda/ci/camunda` | Vault path for the GitHub App ID |
| `github-app-private-key-vault-key` | No | `MONOREPO_RELEASE_APP_PRIVATE_KEY` | Vault key for the GitHub App private key |
| `github-app-private-key-vault-path` | No | `secret/data/products/camunda/ci/camunda` | Vault path for the GitHub App private key |

## Outputs

| Name | Description |
|------|-------------|
| `token` | The renewed GitHub App token |

## Example Usage

```yaml
# In a long-running job, before git push:
- name: Renew GitHub token for push
  if: ${{ !inputs.dryRun }}
  uses: ./.github/actions/renew-github-token
  with:
    vault-url: ${{ secrets.VAULT_ADDR }}
    vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
    vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}

- name: Push changes
  if: ${{ !inputs.dryRun }}
  run: git push origin "${RELEASE_BRANCH}"
```

### With custom GitHub App credentials

```yaml
- name: Renew GitHub token
  uses: ./.github/actions/renew-github-token
  with:
    vault-url: ${{ secrets.VAULT_ADDR }}
    vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
    vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
    github-app-id-vault-key: MY_CUSTOM_APP_ID
    github-app-id-vault-path: secret/data/my/custom/path
    github-app-private-key-vault-key: MY_CUSTOM_APP_PRIVATE_KEY
    github-app-private-key-vault-path: secret/data/my/custom/path
```

# Renew GitHub Token

Generates a fresh GitHub App token via HashiCorp Vault and safely replaces the
git credential set by `actions/checkout@v6`, avoiding duplicate `Authorization`
headers that cause HTTP 400 errors.

## Why this is needed

`actions/checkout@v6` stores the `http.*.extraheader` credential in an external
temp config file (`$RUNNER_TEMP/git-credentials-<uuid>.config`) and references it
from `.git/config` via `includeIf.gitdir` directives. The GitHub App token
expires after 1 hour. In long-running jobs (e.g. Maven release), the token may
expire before `git push`.

A naive `git config --add` to inject a renewed token creates a **second**
extraheader entry — one in `.git/config` and the original in the external file —
causing git to send two `Authorization` headers, which GitHub rejects with
HTTP 400 `"Duplicate header: Authorization"`.

This action safely updates the original credential file used by
`actions/checkout@v6` so that git continues to use a single `Authorization`
header with the renewed token.

**References:**
- [checkoutv6 issue about the token expiration](https://github.com/actions/create-github-app-token/issues/121#issuecomment-2027574184)
- [checkout@v6 persist-creds change (PR #2286)](https://github.com/actions/checkout/pull/2286)
- [Token expiry issue (camunda/camunda#47369)](https://github.com/camunda/camunda/issues/47369)

## Inputs

|                Name                 | Required |                Description                |
|-------------------------------------|----------|-------------------------------------------|
| `vault-url`                         | Yes      | Vault server URL                          |
| `vault-role-id`                     | Yes      | Vault AppRole role ID                     |
| `vault-secret-id`                   | Yes      | Vault AppRole secret ID                   |
| `github-app-id-vault-key`           | Yes      | Vault key for the GitHub App ID           |
| `github-app-id-vault-path`          | Yes      | Vault path for the GitHub App ID          |
| `github-app-private-key-vault-key`  | Yes      | Vault key for the GitHub App private key  |
| `github-app-private-key-vault-path` | Yes      | Vault path for the GitHub App private key |

## Outputs

|  Name   |         Description          |
|---------|------------------------------|
| `token` | The renewed GitHub App token |

## Example Usage

```yaml
# In a long-running job, before git push:
- name: Renew GitHub token for push
  if: ${{ !inputs.dryRun }}
  uses: ./.github/actions/renew-github-token
  with:
    vault-url: ${{ secrets.VAULT_ADDR }}
    vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
    vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
    github-app-id-vault-key: MONOREPO_RELEASE_APP_ID
    github-app-id-vault-path: secret/data/products/camunda/ci/camunda
    github-app-private-key-vault-key: MONOREPO_RELEASE_APP_PRIVATE_KEY
    github-app-private-key-vault-path: secret/data/products/camunda/ci/camunda

- name: Push changes
  if: ${{ !inputs.dryRun }}
  run: git push origin "${RELEASE_BRANCH}"
```

