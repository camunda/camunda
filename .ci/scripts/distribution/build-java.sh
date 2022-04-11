#!/bin/sh -eux
# Specifically for building, use as many CPUs as are available; add the extract-flaky-test plugin
# as well to ensure we'll be downloading the plugin for later use
mvn -B -T2C -s "${MAVEN_SETTINGS_XML}" \
  -DskipTests \
  -Pspotbugs,prepare-offline,extract-flaky-tests -PcheckFormat,-autoFormat \
  clean install
