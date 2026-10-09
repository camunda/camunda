# Composite Action Template

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

Also add `README.md` with purpose, inputs, outputs, and usage examples.
