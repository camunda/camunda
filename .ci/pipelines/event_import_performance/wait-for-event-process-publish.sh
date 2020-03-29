#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 3 ]] || die "3 arguments required [NAMESPACE] [PROCESS_ID] [AUTH_COOKIE_FILE], $# provided"

NAMESPACE=$1
PROCESS_ID=$2
AUTH_COOKIE_FILE=$3

#Monitoring Publish state, should be in state "published" to be finished
STATE="none"
until [[ ("$STATE" == "published") ]]; do
    PROGRESS=$(curl -b $AUTH_COOKIE_FILE -c authCookie -f -s  "http://optimize.${NAMESPACE}:8090/api/eventBasedProcess/$PROCESS_ID" | jq -r ".publishingProgress") || true
    STATE=$(curl -b $AUTH_COOKIE_FILE -c authCookie -f -s "http://optimize.${NAMESPACE}:8090/api/eventBasedProcess/$PROCESS_ID" | jq -r ".state") || true
    echo "STATE: $STATE, PROGRESS: $PROGRESS"
    sleep 60
done