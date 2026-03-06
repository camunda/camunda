---
applyTo: ".github/workflows/**,.github/actions/**"
---

# GitHub Actions CI Conventions

When creating or modifying GitHub Actions workflows and composite actions, follow these rules.

## Workflow Requirements

- Include metadata comments at the top: `# description:`, `# type:`, `# owner:`
- Set default shell to `bash` to ensure proper pipefail behavior
- Start every job with `permissions: {}` and add only required scopes
- Add `timeout-minutes` to every job
- Do not hard code secrets; do not use GitHub Secrets directly
- Use the Hashicorp Vault action to retrieve credentials and secrets (GitHub Secrets are only
  allowed for Vault bootstrap: `VAULT_ADDR`, `VAULT_ROLE_ID`, `VAULT_SECRET_ID`)
- The last step of each job must submit CI health metrics:

  ```yaml
  - name: Observe build status
    if: always()
    continue-on-error: true
    uses: ./.github/actions/observe-build-status
    with:
      build_status: ${{ job.status }}
      secret_vault_address: ${{ secrets.VAULT_ADDR }}
      secret_vault_roleId: ${{ secrets.VAULT_ROLE_ID }}
      secret_vault_secretId: ${{ secrets.VAULT_SECRET_ID }}
  ```

## Actions and Caching

- Avoid introducing new 3rd-party GitHub Actions; prefer composite actions
- Pin all external actions to commit SHAs
- Create composite actions in `.github/actions/` subdirectories using kebab-case naming
- Every composite action must include a `README.md` describing purpose, inputs, outputs, and usage
- For Maven jobs, use the `setup-maven-cache` composite action
- For Node.js jobs using Yarn, use `camunda/infra-global-github-actions/setup-yarn-cache`
- Do not cache Docker layers via GHA cache

## Validation

- Run `actionlint` on workflow modifications
- Run `conftest test --rego-version v0 -o github --policy .github` on workflow modifications
- Reference: `docs/monorepo-docs/ci.md`
