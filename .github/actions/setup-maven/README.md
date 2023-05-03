# Setup Maven Action

## Intro

A Maven setup wrapper to setup a specific Maven version + Java version + GitHub Cache + Camunda-Nexus cache

See [setup-java](https://github.com/actions/setup-java) for possible distribution keywords or how to define java versions.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| java-version | Allows setting a version version to overwrite the default | false | 11 |
| distribution | Allows changing the java distribution | false | adopt |
| maven-version | Allows overwriting the maven version installed by default | false | 3.8.6 |
| secrets | JSON wrapped secrets for easier secret passing | true | |

## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Setup Maven
    uses: ./.github/actions/setup-maven
    with:
        secrets: ${{ toJSON(secrets) }}
        java-version: 17
        distribution: zulu
```
