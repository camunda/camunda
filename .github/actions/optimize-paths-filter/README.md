# Optimize Paths Filter

## Intro

Action that returns flags based on Optimize files changes, to indicate what type of changes were made.

### Outputs

| Output           | Description                                                                  |
| ---------------- | ---------------------------------------------------------------------------- |
| backend-changes  | Returns `true` if there were changes done to backend files                   |
| frontend-changes | Returns `true` if there were changes done to frontend files                  |

## Example of using the action

```yaml
jobs:
  detect-changes:
    name: Get changed directories
    runs-on: ubuntu-latest
    outputs:
      backend-changes: ${{ steps.filter.outputs.backend-changes }}
      frontend-changes: ${{ steps.filter.outputs.frontend-changes }}
    steps:
      - name: Checkout repository
        uses: actions/checkout@a5ac7e51b41094c92402da3b24376905380afc29 # v4

      - name: Get list of changed directories
        id: filter
        uses: ./.github/actions/optimize-paths-filter
  frontend-job:
    name: This job should only run when there are frontend changes
    needs: detect-changes
    if: needs.detect-changes.outputs.frontend-changes == 'true'
    steps: ...
```
