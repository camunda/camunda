# Generate changelog action

## Intro

This actions gets a branch for which we want to generate changelog, finds last tag on it and generates changelog from this tag to branch HEAD. Changelog is generated using [`dlavrenuek/conventional-changelog-action`](https://github.com/dlavrenuek/conventional-changelog-action).

## Usage

### Inputs

| Input  |                   Description                    | Required | Default |
|--------|--------------------------------------------------|----------|---------|
| branch | A branch for which we want to generate changelog | true     | main    |

### Outputs

|  Output   |                   Descritpion                    |
|-----------|--------------------------------------------------|
| changelog | Generated changelog as a multiline string output |

## Example of using this action

```yaml
jobs:
  generate-changelog:
    name: Generate changelog
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11 # v4
        with:
          ref: ${{ github.event.inputs.branch || 'main' }}
      - name: Calculate changelog
        uses: ./.github/actions/generate-changelog
        id: changelog
        with:
          branch: ${{ github.ref_name }}
      - name: Print changelog
        run: |
          echo "${{ steps.changelog.outputs.changelog }}"
```

