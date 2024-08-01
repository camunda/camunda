#!/bin/bash

url=$1
max_wait=600 # 10 minutes in seconds
sleep_interval=5 # check every 5 seconds
loop_counter=0

until curl --output /dev/null --silent --head --fail --max-time 30 "$url"; do
  echo "Waiting to start..."
  loop_counter=$((loop_counter + sleep_interval))
  if [[ loop_counter -ge max_wait ]]; then
    echo "Timeout reached"
    exit 1
  fi
  sleep $sleep_interval
done
