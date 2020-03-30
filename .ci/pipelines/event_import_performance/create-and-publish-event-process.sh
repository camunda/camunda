#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 2 ]] || die "2 arguments required [NAMESPACE] [EVENT_PROCESS_FILE], $# provided"

NAMESPACE=$1
EVENT_PROCESS_FILE=$2

# Authenticate as demo user
curl -f -s -w "%{http_code}" -H 'Content-Type: application/json' -XPOST "optimize.$NAMESPACE:8090/api/authentication" --data-binary '{"username":"demo", "password":"demo"}' -c authCookie
# Create event based invoice process
PROCESS_ID=$(curl -b authCookie -f -s -H 'Content-Type: application/json' -XPOST "optimize.$NAMESPACE:8090/api/eventBasedProcess" -d "@$EVENT_PROCESS_FILE" | jq -r '.id')
# Publish the event based process
curl -b authCookie -f -s -w "%{http_code}" -XPOST "optimize.$NAMESPACE:8090/api/eventBasedProcess/$PROCESS_ID/_publish"
# wait for publish to finish

#Monitoring Publish state, should be in state "published" to be finished
STATE="none"
until [[ ("$STATE" == "published") ]]; do
    sleep 5
    PROGRESS=$(curl -b authCookie -c authCookie -f -s  "optimize.$NAMESPACE:8090/api/eventBasedProcess/$PROCESS_ID" | jq -r ".publishingProgress") || true
    STATE=$(curl -b authCookie -c authCookie -f -s "optimize.$NAMESPACE:8090/api/eventBasedProcess/$PROCESS_ID" | jq -r ".state") || true
    printf "%(%Y-%m-%dT%H:%M:%S.000Z)T - STATE: $STATE, PROGRESS: $PROGRESS\n"
done