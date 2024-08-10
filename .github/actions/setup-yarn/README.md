# Setup Yarn

## Intro

This action allows to install yarn package manager in environments where NPM is not available

## Example of using the action

```yaml
steps:
  - uses: actions/checkout@v3
  - name: Install yarn
    uses: ./.github/actions/setup-yarn
```

