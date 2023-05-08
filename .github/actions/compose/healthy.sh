#!/bin/bash

# docker compose --> v2 (GA)
# docker-compose --> v1 (missing some newer flags)
# Edge case; Self-hosted runners don't support "docker compose" yet even though on v2
VERSION=$(docker-compose version --short)

if [[ "$VERSION" =~ ^1\.[0-9]+\.[0-9]+ ]]; then
    # if docker-compose is v1, we're setting it to docker compose, which should be v2
    echo "Deteceted v1, setting to v2"
    DOCKER_COMMAND="docker compose -f ${FILE} ${COMPOSE_FLAGS}"
else
    # e.g. locally or on self-hosted runners docker-compose can be v2
    echo "Detected v2"
    DOCKER_COMMAND="docker-compose -f ${FILE} ${COMPOSE_FLAGS}"
fi

eval $DOCKER_COMMAND ps
eval $DOCKER_COMMAND logs

regx='\(healthy\)'

# Set interval (duration) in seconds.
secs=${TIMEOUT}
endTime=$(( $(date +%s) + secs ))

# Loop until interval has elapsed.
while [ $(date +%s) -lt $endTime ]; do
    # initialise counter with 0 since we're checking the status of each service
    cnt=0
    while IFS= read -r line; do
        if [[ $line =~ $regx ]]; then
            cnt=$((cnt+1))
        fi
    done <<< $(eval $DOCKER_COMMAND ps --format json | jq '.[].Status')
    echo -en "\rWaiting for services... $cnt/$(eval $DOCKER_COMMAND ps --format json | jq '.[].Status' | wc -l)"
    if [[ $cnt -eq $(eval $DOCKER_COMMAND ps --format json | jq '.[].Status' | wc -l) ]]; then
        echo ""
        exit 0
    fi
    sleep 1
done

eval $DOCKER_COMMAND ps
eval $DOCKER_COMMAND logs

exit 1
