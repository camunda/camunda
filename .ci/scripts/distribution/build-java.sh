#!/bin/sh -eux
# Specifically for building, use as many CPUs as are available
mvn -B -T1C -s "${MAVEN_SETTINGS_XML}" \
  -DskipTests \
  -Pspotbugs,prepare-offline -PcheckFormat,-autoFormat \
  clean install
