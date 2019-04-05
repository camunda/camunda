#!/bin/sh -eux

mvn -B -T1C -s settings.xml generate-sources source:jar javadoc:jar deploy -DskipTests
