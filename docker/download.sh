#!/bin/sh -ex

VERSION="${VERSION:?Optimize version required, eg. \'2.0.0\'}"
SNAPSHOT="${SNAPSHOT:?Snapshot version: \'true\' or \'false\'}"
DISTRO="${DISTRO:?Download \'production\' or \'demo\' distribution}"

ARTIFACT_GROUP="org.camunda.optimize"
ARTIFACT="camunda-optimize"
ARTIFACT_VERSION="${VERSION}"

# Determine if SNAPSHOT repo and version should be used
if [ "${SNAPSHOT}" = "true" ]; then
    ARTIFACT_VERSION="${VERSION}-SNAPSHOT"
fi

function useNexusDownload {
  echo "Downloading Camunda Optimize ${ARTIFACT_VERSION}"

  # Download distro from nexus
  mvn dependency:get -B --global-settings /tmp/settings.xml \
    -DremoteRepositories="camunda-nexus::::https://artifacts.camunda.com/artifactory/internal/" \
    -DgroupId="${ARTIFACT_GROUP}" -DartifactId="${ARTIFACT}" \
    -Dversion="${ARTIFACT_VERSION}" -Dpackaging="tar.gz" -Dclassifier="${DISTRO}" -Dtransitive=false
  mvn dependency:copy -B --global-settings /tmp/settings.xml \
    -Dartifact="${ARTIFACT_GROUP}:${ARTIFACT}:${ARTIFACT_VERSION}:tar.gz:${DISTRO}" \
    -DoutputDirectory=/tmp/

  # Unpack distro to /build directory
  tar xzf /tmp/${ARTIFACT}-${ARTIFACT_VERSION}-${DISTRO}.tar.gz
}

function useLocalArtifact {
  echo "Using local artifacts of Camunda Optimize ${ARTIFACT_VERSION}"

  # Unpack distro to /build directory
  tar xzf /tmp/${ARTIFACT}-${ARTIFACT_VERSION}-${DISTRO}.tar.gz
}

if [ "${SKIP_DOWNLOAD}" = "false" ]; then
  useNexusDownload
else
  useLocalArtifact
fi

# Prevent environment-config.yaml from overriding service-config.yaml since the
# service-config.yaml allows usage of OPTIMIZE_ environment variables: SRE-523
rm config/environment-config.yaml
