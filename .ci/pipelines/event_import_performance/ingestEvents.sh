#!/bin/bash
export LC_ALL=C

die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 3 ]] || die "3 arguments required [NAMESPACE] [EXTERNAL_EVENT_COUNT] [SECRET], $# provided"

NAMESPACE=$1
EXTERNAL_EVENT_COUNT=$2
SECRET=$3

BATCH_FILE="/tmp/currentBatch.json"
BATCH_SIZE=10000

EVENT_NAMES=( "InvoiceReceived" "InvoiceApprovalStarted" "InvoiceApprovalFinished" "InvoiceProcessed" )
EVENT_TEMPLATE="{\"specversion\":\"1.0\",\"id\":\"%s\",\"group\":\"invoice\",\"source\":\"system\",\"type\":\"%s\",\"traceid\":\"%s\",\"time\":\"%(%Y-%m-%dT%H:%M:%S.000Z)T\"}"
# default to UTC timestamps when formatting
export TZ=UTC

printf -v CURRENT_TIMESTAMP_SECONDS "%(%s)T"
# subtract the number of events in seconds from current timestamp, each event will be 1 seconds apart from the previous
START_TIMESTAMP_SECONDS=$(($CURRENT_TIMESTAMP_SECONDS-$EXTERNAL_EVENT_COUNT))
CURRENT_BATCH_SIZE=0

CURRENT_INSTANCE_ID=1
echo "[" > $BATCH_FILE
for (( i=1; i<=$EXTERNAL_EVENT_COUNT; )); do
  # one iteration through the events in the folder is one process instance
  # create a traceId for this instance
  CURRENT_TRACE_ID="$CURRENT_INSTANCE_ID"
  CURRENT_INSTANCE_ID=$((CURRENT_INSTANCE_ID+1))
  for CURRENT_EVENT_NAME in "${EVENT_NAMES[@]}"; do
    CURRENT_BATCH_SIZE=$((CURRENT_BATCH_SIZE+1))
    # create event uuid
    CURRENT_EVENT_ID=$i
    # modify the event template with the current values & append the event to the batch file
    printf "$EVENT_TEMPLATE" "$CURRENT_EVENT_ID" "$CURRENT_EVENT_NAME" "$CURRENT_TRACE_ID" "$((START_TIMESTAMP_SECONDS+i))" >> $BATCH_FILE
    # if batch size is reached or end of generation, ingest the current batch
    if [[ $CURRENT_BATCH_SIZE -ge $BATCH_SIZE || $i -ge $EXTERNAL_EVENT_COUNT ]]
    then
      echo "]" >> $BATCH_FILE
      # ingest completed batch
      printf "%(%Y-%m-%dT%H:%M:%S.000Z)T - Ingesting Batch of size $CURRENT_BATCH_SIZE...\n"
      # curl with generous retry, for cases when pod is unreachable which can happen due restart
      # using -H 'Expect:' to disable 100-continue behavior of curl, see https://gms.tf/when-curl-sends-100-continue.html
      curl -s\
       -H 'Expect:' -H 'Content-Type: application/json' -H "Authorization: Bearer $SECRET"\
       -f --connect-timeout 5 --max-time 10 --retry 60 --retry-delay 0 --retry-max-time 600 --retry-all-errors\
       --compressed -s -o /dev/null -w  "Status: %{http_code}, ResponseTime: %{time_total}s\n"\
       -XPOST optimize.${NAMESPACE}:8090/api/ingestion/event/batch --data-binary "@$BATCH_FILE"
      printf "%(%Y-%m-%dT%H:%M:%S.000Z)T - done\n"
      # reset batch
      CURRENT_BATCH_SIZE=0
      echo "[" > $BATCH_FILE
    else
      echo "," >> $BATCH_FILE
    fi
    i=$((i+1))
    # if count of events reached abort
    if [[ $i -gt $EXTERNAL_EVENT_COUNT ]]
    then
      break
    fi
  done
done