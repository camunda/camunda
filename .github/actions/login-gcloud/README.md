# Login Google Cloud Action

## Intro

This action abstracts login to the [Google cloud](https://console.cloud.google.com/gcr/images/ci-30-162810) project.

## Usage

### Inputs

|   Input    |                  Description                   | Required |   Default    |
|------------|------------------------------------------------|----------|--------------|
| secrets    | JSON wrapped secrets for easier secret passing | true     |              |
| project_id | Google cloud project ID                        | false    | ci-30-162810 |

## Example of using the action

```yaml
steps:
  - uses: actions/checkout@v3
  - name: Login to Google Cloud
    uses: ./.github/actions/login-gcloud
    with:
      secrets: ${{ toJSON(secrets) }}
```

