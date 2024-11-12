# Execute healthcheck and push images

## Intro

This action does a healthcheck against an Optimize image during the smoketest and pushes the image to a registry if the healthcheck is successful.
A requirement for the usage for this action is that the Optimize container runs on port 8080.

## Usage

### Inputs

|  Input   |                       Description                        | Required |
|----------|----------------------------------------------------------|----------|
| version  | The version of the to be created optimize docker image.  | true     |
| date     | The date of the docker image creation.                   | true     |
| revision | The revision of the to be created optimize docker image. | true     |

## Example of using the action

```yaml
steps:
  - uses: actions/checkout@v3
  - name: Execute health check and push docker image
    uses: ./.github/actions/execute-healthcheck-and-push-image
    with:
      version: ${{ env.VERSION }}
      date: ${{ env.DATE }}
      revision:  ${{ env.REVISION }}
```

