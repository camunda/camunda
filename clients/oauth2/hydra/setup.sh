#!/bin/sh
# Wait until the hydra server is up and running
nc -z -w30 hydra 4445

# Create our Zeebe client
hydra clients create \
  --endpoint http://hydra:4445 \
  --callbacks http://hydra:5555/callback \
  --grant-types client_credentials \
  --response-types code \
  --id zeebe \
  --secret secret \
  --audience "127.0.0.1" \
  --token-endpoint-auth-method client_secret_post
