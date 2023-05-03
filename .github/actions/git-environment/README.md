# Git Environment Action

## Intro

The action shall provide some common info about the environment to make it easier to rely on those values in the if statements of jobs to not falsely trigger those.

Ifs don't allow complex statements or bash scripts, due to which it's easier to provide the values from outside.

## Disclaimer

This action is heavily WIP and maybe the old Jenkins functionality can be realised better. Use with care as every execution costs at least 1 billed minute. The action itself takes 2 seconds but GitHub only bills full minutes.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| maintenance_branch_regex | A bash regex provided to determine the maintenance branch | false | ^maintenance\/([0-9]+\.[0-9]+)$ | 
| main_branch_regex | A bash regex provided to determine the main branch | false | ^master$ |
| branch | A provided branch name | false | defaults to github.ref_name |

### Outputs
| Output | Description |
|--------|-------------|
| maintenance_version | If it's a maintenance branch, which maintenance version it is |
| is_maintenance_branch | Whether the provided branch is a maintenance branch  |
| is_main_branch | Whether the provided branch is a main branch |
| is_main_or_maintenance_branch | Whether the provided branch is a main or maintenance branch |
| branch_slug | The sanitized branch - everything lowercase and anything not a-z0-9- is replaced with - |
| git_commit_hash | The git commit hash |
| image_tag | Depending on main/maintenance returns hash or branch-slug |
| latest_tag | If maintenance returns maintenance-version-latest else latest |

## Example of using the action

```yaml
jobs:
    environment:
        name: Define global values
        runs-on: ubuntu-latest
        outputs:
            maintenance_version: ${{ steps.define-values.outputs.maintenance_version }}
            is_maintenance_branch: ${{ steps.define-values.outputs.is_maintenance_branch }}
            is_main_branch: ${{ steps.define-values.outputs.is_main_branch }}
            is_main_or_maintenance_branch: ${{ steps.define-values.outputs.is_main_or_maintenance_branch }}
            branch_slug: ${{ steps.define-values.outputs.branch_slug }}
            git_commit_hash: ${{ steps.define-values.outputs.git_commit_hash }}
            image_tag: ${{ steps.define-values.outputs.image_tag }}
            latest_tag: ${{ steps.define-values.outputs.latest_tag }}
        steps:
        - uses: actions/checkout@v3
        - id: define-values
          uses: ./.github/actions/git-environment

  sonarqube:
    name: SonarQube - Java
    runs-on: ubuntu-latest
    needs: ['environment']
    if: ${{ needs.environment.outputs.is_main_or_maintenance_branch }}
    steps:
        ...
```
