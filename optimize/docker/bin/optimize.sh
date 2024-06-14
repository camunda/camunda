#!/bin/bash -e

CMD="./optimize-startup.sh"

if [ -n "${WAIT_FOR}" ]; then
    CMD="wait-for-it.sh ${WAIT_FOR} -s -t ${WAIT_FOR_TIMEOUT} -- ${CMD}"
fi

bash ${CMD} "$@"
