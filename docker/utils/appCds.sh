#!/bin/bash -xeu
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Zeebe Community License 1.1. You may not use this file
# except in compliance with the Zeebe Community License 1.1.
#
set -oxe pipefail

APP="${1}"
TARGET="${2}"
CLASSLIST="${TARGET}.classlist"
ARCHIVE="${TARGET}.jsa"

if [[ ! -f "${APP}" ]]; then
  echo "Expected to find an application at ${APP}, but there is no such file"
  exit 1
fi

# generate the class list first
JAVA_OPTS="-Xshare:off -XX:DumpLoadedClassList=${CLASSLIST}" "${APP}" &

# wait until application is ready and then stop it
curl --connect-timeout 30 -f -s -o /dev/null \
  --retry-connrefused --retry-max-time 20 --retry 10 --retry-delay 1 \
  "http://localhost:9600/ready"
echo "Application is ready, shutting it down..."
kill "$(jobs -p)"
wait

# generate the archive from it
JAVA_OPTS="-Xshare:dump -XX:SharedArchiveFile=${ARCHIVE} -XX:SharedClassListFile=${CLASSLIST}" "${APP}"
echo "Generated CDS for application ${APP} at ${ARCHIVE}"
