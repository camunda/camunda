# CI Workflow Authoring Guide

This prompt provides patterns and templates for creating GitHub Actions workflows in this monorepo.

## Documentation First

**Before implementing or advising**, consult: `docs/monorepo-docs/ci.md`

```bash
# Search for specific CI guidance
search "allowed actions" path:docs/monorepo-docs/ci.md
search "Unified CI" path:docs/monorepo-docs/ci.md
search "workflow inclusion criteria" path:docs/monorepo-docs/ci.md
```

## Mandatory Conventions

### Workflow Requirements

1. **Top-level metadata:**

   ```yaml
   ```

# description: <Description of what the GHA is running and what is being tested>

# test location: <The filepath of the tests being run>

# type: <ci, release, scheduled, etc.>

# owner: <github-team-handle>

---

name: <workflow-name>

```
2. **Default shell:**

```yaml
defaults:
  run:
    shell: bash
```

3. **Permissions:** Start with `permissions: {}`, add only what's needed
4. **Timeout:** Always specify `timeout-minutes` on jobs (max 10 for Unified CI)
5. **Pinned SHAs:** No floating tags like `@v4` - use exact commit SHAs
6. **Last step of each job:**

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

## Minimal Workflow Template

```yaml
# description: <Description of what the GHA is running and what is being tested>
# test location: <The filepath of the tests being run>
# type: ci
# owner: <github-team-handle>
---
name: <workflow-name>

on:
  pull_request:

defaults:
  run:
    shell: bash

jobs:
  ci:
    name: CI
    runs-on: ubuntu-latest
    timeout-minutes: 10
    permissions: {}

    steps:
      - name: Checkout
        uses: actions/checkout@<PINNED_SHA>

      # ... minimal required steps only ...

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

## Composite Action Template

Path: `.github/actions/<kebab-case-name>/action.yml`

```yaml
name: "<Action Name>"
description: "<What it does>"
inputs:
  example_input:
    description: "Example input"
    required: false
runs:
  using: "composite"
  steps:
    - shell: bash
      run: |
        set -euo pipefail
        echo "Hello from composite"
```

**Required:** Include `README.md` with purpose, inputs/outputs, example usage.

## Unified CI Inclusion Criteria

From `docs/monorepo-docs/ci.md`:
- Runtime: max 10 minutes
- Instrumented for CI health metrics
- High stability (10 consecutive green builds)
- Uses Vault for secrets
- Follows caching strategy
- Follows security best practices

## Caching Strategy

- **Maven:** Use `.github/actions/setup-maven-cache`
- **Yarn:** Use `camunda/infra-global-github-actions/setup-yarn-cache`
- **Docker layers:** Do NOT write to GHA cache
- **Cache writes:** Only from `main` and `stable*` branches

