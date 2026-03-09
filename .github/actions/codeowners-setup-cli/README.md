# codeowners-setup-cli

Downloads and caches the [`codeowners-cli`](https://github.com/multimediallc/codeowners-plus)
binary for use in GitHub Actions workflows.

## Purpose

Provides a reusable setup step that downloads the `codeowners-cli` binary from
the codeowners-plus GitHub releases, caches it across workflow runs, and adds it
to `PATH` so subsequent steps can call it directly.

## Inputs

|   Name    | Required | Default  |              Description               |
|-----------|----------|----------|----------------------------------------|
| `version` | No       | `v1.9.0` | Version of `codeowners-cli` to install |

## Outputs

_None._ The binary is added to `PATH` for subsequent steps.

## Example Usage

```yaml
steps:
  - uses: actions/checkout@v6

  - name: Setup codeowners-cli
    uses: ./.github/actions/codeowners-setup-cli
    with:
      version: v1.9.0

  - name: Check unowned files
    run: codeowners-cli unowned --root ./

  - name: Check file owner
    run: codeowners-cli owner --root ./ --format json path/to/file.java
```

