#!/bin/bash

mvn $PARAMETERS -T $THREADS -B --fail-at-end -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
