# Minimal Workflow Template

```yaml
# description: <Description of what the GHA runs and what is tested>
# test location: <Path to tests>
# type: ci
# owner: <github-team-handle>
---
name: <workflow-name>

on:
  pull_request:

defaults:
  run:
    shell: bash

# Routes CI-health failures to the owning team's medic. observe-build-status
# reads this as the default user_description; without it failures land with no
# owner. Must match .codeowners. Override per job (or use ${{ matrix.owner }})
# for outliers. See the stable/product-branch caveat in SKILL.md.
env:
  TEST_OWNER: '<github-team-handle>'

jobs:
  ci:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions: {}

    steps:
      - name: Checkout
        uses: actions/checkout@<PINNED_SHA>

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

