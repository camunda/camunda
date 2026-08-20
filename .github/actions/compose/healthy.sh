#!/bin/bash

# docker compose --> v2 (GA)
# docker-compose --> v1 (missing some newer flags)
# Edge case; Self-hosted runners don't support "docker compose" yet even though on v2

DOCKER_COMMAND="docker compose -f ${FILE} ${COMPOSE_FLAGS}"
eval $DOCKER_COMMAND ps
eval $DOCKER_COMMAND logs

regx='\(healthy\)'

# Set interval (duration) in seconds.
secs=${TIMEOUT}
endTime=$(($(date +%s) + secs))

# Loop until interval has elapsed.
# Version 2.21.0 of Docker Compose has introduced a change in its output format. This script must support both the old and new formats.
while [ $(date +%s) -lt $endTime ]; do
    # initialise counter with 0 since we're checking the status of each service
    cnt=0
    while IFS= read -r line; do
        if [[ $line =~ $regx ]]; then
            cnt=$((cnt + 1))
        fi
    done <<<$(eval $DOCKER_COMMAND ps --format json | jq -n '[inputs] | flatten | .[].Status')
    echo -en "\rWaiting for services... $cnt/$(eval $DOCKER_COMMAND ps --format json | jq -n '[inputs] | flatten | .[].Status' | wc -l)"
    if [[ $cnt -eq $(eval $DOCKER_COMMAND ps --format json | jq -n '[inputs] | flatten | .[].Status' | wc -l) ]]; then
        echo ""
        eval $DOCKER_COMMAND ps
        exit 0
    fi
    sleep 1
done

echo "\rServices are not healthy after $TIMEOUT seconds"

eval $DOCKER_COMMAND ps
eval $DOCKER_COMMAND logs

exit 1
