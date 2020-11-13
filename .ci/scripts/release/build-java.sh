#!/bin/bash -xeu

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

if [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  mvn versions:set-property -DgenerateBackupPoms=false -Dproperty=backwards.compat.version -DnewVersion=${RELEASE_VERSION}
  FILE=$(mvn help:evaluate -Dexpression=ignored.changes.file -q -DforceStdout)
  rm -f clients/java/$FILE test/$FILE exporter-api/$FILE protocol/$FILE bpmn-model/$FILE

  git commit -am "chore(project): update version in pom.xml"
else
  echo "Version $RELEASE_VERSION"
fi

mvn -B -s ${MAVEN_SETTINGS_XML} -DskipTests clean com.mycila:license-maven-plugin:check com.coveo:fmt-maven-plugin:check install -Pprepare-offline
