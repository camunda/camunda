# Login Registry Action

## Intro

This action abstracts the Docker login to the [Harbor registry](registry.camunda.cloud) provided by the Infrastructure team.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| secrets | JSON wrapped secrets for easier secret passing | true | |
| env | By providing "dev." or "stage." one can switch the registry | false | |


## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Login to Harbor registry
    uses: ./.github/actions/login-registry
    with:
        secrets: ${{ toJSON(secrets) }}
```
