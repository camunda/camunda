#!/bin/bash -xue

if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Skipping updating the compat version as ${RELEASE_VERSION} is not a stable version"
  exit 0
fi

mvn -B -s "${MAVEN_SETTINGS_XML}" versions:set-property -DgenerateBackupPoms=false -Dproperty=backwards.compat.version -DnewVersion="${RELEASE_VERSION}"
FILE=$(mvn -B -s "${MAVEN_SETTINGS_XML}" help:evaluate -Dexpression=ignored.changes.file -q -DforceStdout)
rm -f "clients/java/${FILE}" "test/${FILE}" "exporter-api/${FILE}" "protocol/${FILE}" "bpmn-model/${FILE}"
git commit -am "build(project): update java compat versions"
git push origin "${RELEASE_BRANCH}"
