# check-codeowners

Checks that all files in the repository have an owner defined in `.codeowners`
using the [`codeowners-cli`](https://github.com/multimediallc/codeowners-plus) tool.

Internally sets up the CLI via the
[`codeowners-setup-cli`](../codeowners-setup-cli/README.md) action and runs
`codeowners-cli unowned`. Fails if any file has no owner.

## Inputs

|   Name    | Required | Default  |              Description               |
|-----------|----------|----------|----------------------------------------|
| `version` | No       | `v1.9.0` | Version of `codeowners-cli` to install |

## Outputs

_None._

## Example Usage

```yaml
steps:
  - uses: actions/checkout@v6
  - uses: ./.github/actions/codeowners-check
```

