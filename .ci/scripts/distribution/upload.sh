#!/bin/sh -eux

mvn -B -T1C -s ${MAVEN_SETTINGS_XML} generate-sources source:jar javadoc:jar deploy -DskipTests -DskipChecks
