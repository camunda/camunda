# Temporary Act Test Workflow Template

Create temporary harnesses as `.github/workflows/test-<workflow>.yml`.

```yaml
name: Test - <Original Workflow Name>
on:
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions: {}
    defaults:
      run:
        shell: bash
    steps:
      - name: Prepare mock inputs
        run: |
          # MOCK-START
          echo "mock input" > mock.txt
          # MOCK-END

      - name: Execute production logic (verbatim)
        run: |
          # Paste production bash logic here

      - name: Mock external calls
        run: |
          # MOCK-START
          echo "MOCK: external integrations skipped"
          # MOCK-END
```

Keep non-mock logic equivalent to production.

For better local visibility, make harness steps print business-relevant values (for example, validation results, computed action types, branch names, or generated notification payload excerpts), so users can verify behavior quickly from `act` output without scanning unrelated logs.
