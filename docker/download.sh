#!/bin/sh -ex
NEXUS="https://app.camunda.com/nexus/service/local/artifact/maven/redirect"
REPO="${REPO:?Nexus repository required, eg. \'camunda-optimize\'}"
VERSION="${VERSION:?Optimize version required, eg. \'2.0.0\'}"
SNAPSHOT="${SNAPSHOT:?Snapshot version: \'true\' or \'false\'}"
DISTRO="${DISTRO:?Download \'production\' or \'demo\' distribution}"

ARTIFACT_GROUP="org.camunda.optimize"
ARTIFACT="camunda-optimize"
ARTIFACT_VERSION="${VERSION}"

# Determine if SNAPSHOT repo and version should be used
if [ ${SNAPSHOT} = "true" ]; then
    REPO="${REPO}-snapshots"
    ARTIFACT_VERSION="${VERSION}-SNAPSHOT"
fi

function useNexusDownload {
  # Configure username and password for download
  echo "machine app.camunda.com login ${USERNAME} password ${PASSWORD}" >> ~/.netrc

  # Determine nexus URL parameters
  echo "Downloading Camunda Optimize ${VERSION}"

  # Download distro from nexus
  wget --progress=bar:force:noscroll -O /tmp/optimize.tar.gz "${NEXUS}?r=${REPO}&g=${ARTIFACT_GROUP}&a=${ARTIFACT}&v=${ARTIFACT_VERSION}&c=${DISTRO}&p=tar.gz"

  # Unpack distro to /optimize directory
  tar xzf /tmp/optimize.tar.gz
}

function useLocalArtifact {
  echo "Using local artifacts of Camunda Optimize ${ARTIFACT_VERSION}"
  tar xzf /tmp/${ARTIFACT}-${ARTIFACT_VERSION}-${DISTRO}.tar.gz
}


if [ "${SKIP_DOWNLOAD}" = false ]; then
  useNexusDownload
else
  useLocalArtifact
fi