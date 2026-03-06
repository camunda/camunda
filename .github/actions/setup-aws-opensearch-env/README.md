# Setup AWS OpenSearch env

Imports AWS OpenSearch endpoint secrets from Vault, resolves the endpoint URL for a selected
OpenSearch version, exports required environment variables, and configures AWS credentials
for subsequent steps.

## Inputs

|        Input         |                    Description                    | Required |
|----------------------|---------------------------------------------------|----------|
| `vault-address`      | Vault URL to retrieve secrets from                | Yes      |
| `vault-role-id`      | Vault Role ID to use                              | Yes      |
| `vault-secret-id`    | Vault Secret ID to use                            | Yes      |
| `opensearch-version` | OpenSearch version to configure (`2.19` or `3.3`) | Yes      |

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
      vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
      vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
      opensearch-version: "2.19"

  - name: Use OpenSearch URL
    run: echo "${{ env.OPENSEARCH_URL }}"
```

