# Run Maven Action

## Intro

A Maven wrapper to provide defaults when running maven. E.g. defaults to Europe/Berlin timezone for the maven process.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| parameters | Maven paremeters to supply to specify the action | true | |
| threads | Allows overwriting the amount of threads used by passing the value to -T | false | 1C |


## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Verify upgrade
    uses: ./.github/actions/run-maven
    with:
        parameters: verify -Dskip.docker -pl upgrade
```
