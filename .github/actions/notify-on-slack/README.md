# Notify on Slack Action

## Intro

Action that allows to post a status message of the workflow results to slack channel.

### Inputs

| Input             | Description                      | Required | Default                            |
| ----------------- | -------------------------------- | -------- | ---------------------------------- |
| slack_webhook_url | Slack Webhook URL                | true     |                                    |
| text              | Message to be sent               | false    |                                    |
| status            | Status of the message            | false    | failure                            |
| fields            | Fields to be used in the message | false    | workflow,eventName,repo,action,ref |
| channel           | Slack channel name               | false    | "#optimize-daily-medic-update"     |

## Example of using the action

```yaml
steps:
  - name: Import secrets
    id: secrets
    uses: hashicorp/vault-action@v2.5.0
    with:
      url: ${{ secrets.VAULT_ADDR }}
      method: approle
      roleId: ${{ secrets.VAULT_ROLE_ID }}
      secretId: ${{ secrets.VAULT_SECRET_ID }}
      secrets: |
        secret/data/products/optimize/ci/camunda-optimize SLACK_BOT_URL;
  - name: Post results on slack
    if: always()
    uses: ./.github/actions/notify-on-slack
    with:
      slack_webhook_url: ${{ steps.secrets.outputs.SLACK_BOT_URL}}
      status: ${{ job.status }}
```
