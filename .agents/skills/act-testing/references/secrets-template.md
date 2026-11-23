# Local `.secrets` Template for Act

Create a local `.secrets` file (never commit it):

```text
SLACK_TOPMONOREPORELEASE_WEBHOOK_URL=https://hooks.slack.com/services/test/mock/url
VAULT_ADDR=http://localhost:8200
VAULT_JWT_ROLE=mock-jwt-role
VAULT_JWT_PATH=mock-jwt-path
VAULT_JWT_AUDIENCE=mock-jwt-audience
```

Use with:

```bash
act pull_request -e .agents/skills/act-testing/references/event-payloads/pr-opened-ready.json -W .github/workflows/test-example.yml --secret-file .secrets --reuse
```

