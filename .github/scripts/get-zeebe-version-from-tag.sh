#!/bin/bash

ARTIFACTORY_METADATA_XML_URL='https://artifacts.camunda.com/artifactory/zeebe-io/io/camunda/zeebe-protocol/maven-metadata.xml'

getVersion() {
  local releaseTag=$1

  if [[ $releaseTag == "snapshot" ]]; then
    echo "SNAPSHOT"
    return
  elif [[ $releaseTag != "latest" && $releaseTag != "snapshot" ]]; then
    echo "$releaseTag"
    return
  fi

  local xmlContent=$(curl -s "$ARTIFACTORY_METADATA_XML_URL")
  local version=$(echo "$xmlContent" | xmllint --xpath "string(/metadata/versioning/latest)" -)

  echo "$version"
}

getVersion "$1"
