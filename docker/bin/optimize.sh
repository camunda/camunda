#!/bin/bash -e

CMD="java ${JAVA_OPTS} -cp "${OPTIMIZE_CLASSPATH}" -Dfile.encoding=UTF-8 org.camunda.optimize.Main"

if [ -n "${WAIT_FOR}" ]; then
    CMD="wait-for-it.sh ${WAIT_FOR} -s -t ${WAIT_FOR_TIMEOUT} -- ${CMD}"
fi

exec ${CMD}
