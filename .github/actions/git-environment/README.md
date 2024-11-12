# Git Environment Action

## Intro

The action shall provide some common info about the environment to make it easier to rely on those
values in the if statements of jobs to not falsely trigger those.

Ifs don't allow complex statements or bash scripts, due to which it's easier to provide the values
from outside.

## Disclaimer

This action is heavily WIP. Use with care as every execution costs at least 1 billed minute. The
action itself takes 2 seconds but GitHub only bills full minutes.

## Usage

### Inputs

|        Input        |                     Description                      | Required |           Default           |
|---------------------|------------------------------------------------------|----------|-----------------------------|
| stable_branch_regex | A bash regex provided to determine the stable branch | false    | ^stable\/([0-9]+\.[0-9]+)$  |
| main_branch_regex   | A bash regex provided to determine the main branch   | false    | ^main$                      |
| branch              | A provided branch name                               | false    | defaults to github.ref_name |

### Outputs

|          Output          |                                       Description                                       |
|--------------------------|-----------------------------------------------------------------------------------------|
| stable_version           | If it's a stable branch, which stable version it is                                     |
| is_stable_branch         | Whether the provided branch is a stable branch                                          |
| is_main_branch           | Whether the provided branch is a main branch                                            |
| is_main_or_stable_branch | Whether the provided branch is a main or stable branch                                  |
| branch_slug              | The sanitized branch - everything lowercase and anything not a-z0-9- is replaced with - |
| git_commit_hash          | The git commit hash                                                                     |
| image_tag                | Depending on main/stable returns hash or branch-slug                                    |
| latest_tag               | If stable returns stable-version-latest else latest                                     |

## Example of using the action

```yaml
jobs:
  environment:
    name: Define global values
    runs-on: ubuntu-latest
    outputs:
      stable_version: ${{ steps.define-values.outputs.stable_version }}
      is_stable_branch: ${{ steps.define-values.outputs.is_stable_branch }}
      is_main_branch: ${{ steps.define-values.outputs.is_main_branch }}
      is_main_or_stable_branch: ${{ steps.define-values.outputs.is_main_or_stable_branch }}
      branch_slug: ${{ steps.define-values.outputs.branch_slug }}
      git_commit_hash: ${{ steps.define-values.outputs.git_commit_hash }}
      image_tag: ${{ steps.define-values.outputs.image_tag }}
      latest_tag: ${{ steps.define-values.outputs.latest_tag }}
    steps:
      - uses: actions/checkout@v3
      - id: define-values
        uses: ./.github/actions/git-environment
  ...
  release:
    name: Perform the release
    runs-on: ubuntu-latest
    needs: [ 'environment' ]
    steps:
      - uses: actions/checkout@v3
      - name: Expose common variables as Env
        run: |
          {
          echo "VERSION=$RELEASE_VERSION"
          echo "REVISION=${{ needs.environment.define-values.outputs.git_commit_hash }}"
          } >> "$GITHUB_ENV"
  ...
```

