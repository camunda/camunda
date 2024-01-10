#!/bin/bash

ARTIFACTORY_METADATA_XML_URL='https://artifacts.camunda.com/artifactory/camunda-bpm-snapshots/org/camunda/bpm/camunda-engine/maven-metadata.xml'

XML_CONTENT=$(curl -s "$ARTIFACTORY_METADATA_XML_URL")
ENGINE_SNAPSHOT_VERSION=$(echo "$XML_CONTENT" | xmllint --xpath "string(/metadata/versioning/latest)" -)

echo "$ENGINE_SNAPSHOT_VERSION"
