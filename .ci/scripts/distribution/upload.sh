#!/bin/sh -eux

mvn -B -T1C -s .ci/settings.xml generate-sources source:jar javadoc:jar deploy -DskipTests
