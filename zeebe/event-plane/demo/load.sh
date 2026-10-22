#!/usr/bin/env bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
# Publishes messages through broker-0's REST API at about a rate: ./load.sh [per second] [seconds]
set -euo pipefail

rate=${1:-20}
seconds=${2:-600}
pause=$(awk "BEGIN { print 1 / ${rate} }")
end=$((SECONDS + seconds))
i=0
while [ "${SECONDS}" -lt "${end}" ]; do
  curl -s -o /dev/null -X POST http://localhost:8080/v2/messages/publication \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"demo\",\"correlationKey\":\"key-${i}\"}" || echo "publish ${i} failed"
  i=$((i + 1))
  sleep "${pause}"
done
echo "published ${i} messages"
