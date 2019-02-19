#!/bin/sh -ex

REPO="${REPO:?Nexus repository required, eg. \'camunda-optimize\'}"
VERSION="${VERSION:?Optimize version required, eg. \'2.0.0\'}"
SNAPSHOT="${SNAPSHOT:?Snapshot version: \'true\' or \'false\'}"
DISTRO="${DISTRO:?Download \'production\' or \'demo\' distribution}"

ARTIFACT_GROUP="org.camunda.optimize"
ARTIFACT="camunda-optimize"
ARTIFACT_VERSION="${VERSION}"

# Determine if SNAPSHOT repo and version should be used
if [ "${SNAPSHOT}" = "true" ]; then
    REPO="${REPO}-snapshots"
    ARTIFACT_VERSION="${VERSION}-SNAPSHOT"
fi

function useNexusDownload {
  # Configure username and password for download
  echo "machine app.camunda.com login ${USERNAME} password ${PASSWORD}" >> ~/.netrc

  # Determine nexus URL parameters
  echo "Downloading Camunda Optimize ${ARTIFACT_VERSION}"

  # Download distro from nexus
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
