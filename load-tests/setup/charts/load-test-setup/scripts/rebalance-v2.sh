set -eu

echo "Requesting token for rebalancing"
if auth_response=$(curl -sS -f --connect-timeout 10 --max-time 60 -X POST "$AUTH_SERVER" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET" \
  --data-urlencode "audience=$AUTHORIZATION_AUDIENCE"); then
  :
else
  echo "Token request to $AUTH_SERVER failed" >&2
  exit 1
fi

token=$(printf '%s' "$auth_response" | jq -r '.access_token // empty')
if [ -z "$token" ]; then
  echo "Failed to obtain an access token for the cluster-admin API" >&2
  exit 1
fi

endpoint="$ZEEBE_REST_ADDRESS/cluster/v2/rebalance"
if status=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 10 --max-time 60 -X POST "$endpoint" \
  -H "Authorization: Bearer $token"); then
  :
else
  echo "Rebalance request to $endpoint failed: curl transport error" >&2
  exit 1
fi

# 409 (already in progress) is expected given the 10-minute schedule, so treat it as a no-op success
case "$status" in
  202)
    echo "Rebalance request to $endpoint succeeded (status $status)"
    ;;
  409)
    echo "Rebalance request to $endpoint: rebalance already in progress (status $status), treating as a no-op"
    ;;
  *)
    echo "Rebalance request to $endpoint failed with unexpected status $status" >&2
    exit 1
    ;;
esac
