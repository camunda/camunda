# List Maintained Branches Action

Discovers the maintained branches (`main` plus every `stable/X.Y` at or above a
`min-version`) from the git remote and exposes them as JSON arrays ready for
`strategy.matrix` via `fromJSON()`.

Because it reads refs with `git ls-remote`, the list stays correct as new
`stable/*` branches are cut and older ones reach EOL — there is no hardcoded
version list to maintain.

## Prerequisites

- The repository must be checked out (e.g. `actions/checkout`) **before** this
  action runs, so the `origin` remote is configured.
- `jq` must be available on the runner (present on GitHub-hosted runners).

## Inputs

|     Input     |                            Description                            | Required |
|---------------|-------------------------------------------------------------------|----------|
| `min-version` | Minimum stable version to include, as `MAJOR.MINOR` (e.g. `8.8`). | yes      |

## Outputs

|      Output       |                                    Description                                    |
|-------------------|-----------------------------------------------------------------------------------|
| `branches`        | JSON array including `main`, e.g. `["main","stable/8.8","stable/8.9"]`.           |
| `stable-branches` | JSON array of only the `stable/X.Y` branches, e.g. `["stable/8.8","stable/8.9"]`. |

> **Ordering is not guaranteed.** The action does not sort its output. Callers
> that need a specific order (e.g. ascending by version) must sort it themselves.

## Example

```yaml
jobs:
  prepare-matrix:
    runs-on: ubuntu-latest
    outputs:
      branches: ${{ steps.branches.outputs.branches }}
    steps:
      - uses: actions/checkout@v4
      - id: branches
        uses: ./.github/actions/list-maintained-branches
        with:
          min-version: '8.8'

  scan:
    needs: prepare-matrix
    strategy:
      matrix:
        branch: ${{ fromJSON(needs.prepare-matrix.outputs.branches) }}
    # ...
```

