#!/bin/sh -eux

# compile and generate-sources to ensure that the Javadoc can be properly generated; compile is
# necessary when using annotation preprocessors for code generation, as otherwise the symbols are
# not resolve-able by the Javadoc generator
mvn -B -T1C -s "${MAVEN_SETTINGS_XML}" -DskipTests -DskipChecks \
  compile generate-sources source:jar javadoc:jar deploy
