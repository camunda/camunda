# Setup AWS OpenSearch env

Imports AWS OpenSearch endpoint secrets from Vault, resolves the endpoint URL for a selected
OpenSearch version, exports required environment variables, and configures AWS credentials
for subsequent steps.

## Inputs

|        Input         |                    Description                    | Required |
|----------------------|---------------------------------------------------|----------|
| `vault-address`      | Vault URL to retrieve secrets from                | Yes      |
| `vault-role-id`      | Deprecated Vault AppRole role ID                  | No       |
| `vault-secret-id`    | Deprecated Vault AppRole secret ID                | No       |
| `vault-jwt-role` | Vault JWT role (`secrets.VAULT_JWT_ROLE`); when set, the action logs in via JWT/OIDC and the job needs `id-token: write` | No |
| `vault-jwt-path` | Vault JWT auth backend path (`secrets.VAULT_JWT_PATH`) | No |
| `vault-jwt-audience` | GitHub OIDC audience for the Vault JWT login (`secrets.VAULT_JWT_AUDIENCE`) | No |
| `opensearch-version` | OpenSearch version to configure (`2.19` or `3.7`) | Yes      |

## Outputs

|      Output      |                        Description                        |
|------------------|-----------------------------------------------------------|
| `opensearch-url` | Resolved OpenSearch endpoint URL for the selected version |

## Example

```yaml
steps:
  - uses: actions/checkout@v6
  - name: Setup AWS OpenSearch
    uses: ./.github/actions/setup-aws-opensearch-env
    with:
      vault-address: ${{ secrets.VAULT_ADDR }}
      vault-jwt-role: ${{ secrets.VAULT_JWT_ROLE }}
      vault-jwt-path: ${{ secrets.VAULT_JWT_PATH }}
      vault-jwt-audience: ${{ secrets.VAULT_JWT_AUDIENCE }}
      opensearch-version: "2.19"

  - name: Use OpenSearch URL
    run: echo "${{ env.OPENSEARCH_URL }}"
```

