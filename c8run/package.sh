#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Check for required commands
for cmd in wget tar uname; do
  if ! command -v "$cmd" &> /dev/null; then
    echo "Error: Required command '$cmd' is not installed." >&2
    exit 1
  fi
done

# Set versions
CAMUNDA_VERSION='8.6.2'
CAMUNDA_CONNECTORS_VERSION='8.6.2'
ELASTICSEARCH_VERSION='8.13.4'

# Determine architecture
architectureRaw="$(uname -m)"
case "${architectureRaw}" in
  arm64*)
    architecture=aarch64
    ;;
  x86_64*)
    architecture=x86_64
    ;;
  *)
    echo "Error: Architecture '${architectureRaw}' is not supported." >&2
    exit 1
    ;;
esac
echo "Architecture: $architecture"

# Determine platform
kernelName="$(uname -s)"
case "${kernelName}" in
  Linux)
    platform=linux
    ;;
  Darwin)
    platform=darwin
    ;;
  *)
    echo "Error: Kernel '${kernelName}' is not supported." >&2
    exit 1
    ;;
esac
echo "Platform: $platform"

# Create a temporary directory for downloads
tempDir=$(mktemp -d)
trap 'rm -rf "$tempDir"' EXIT

# Download and extract Elasticsearch if not already present
if [ ! -d "elasticsearch-$ELASTICSEARCH_VERSION" ]; then
  echo "Downloading Elasticsearch $ELASTICSEARCH_VERSION"
  wget -q -c -P "$tempDir" "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${ELASTICSEARCH_VERSION}-${platform}-${architecture}.tar.gz"

  echo "Extracting Elasticsearch $ELASTICSEARCH_VERSION"
  tar -xzf "$tempDir/elasticsearch-${ELASTICSEARCH_VERSION}-${platform}-${architecture}.tar.gz"
else
  echo "Elasticsearch $ELASTICSEARCH_VERSION already exists, skipping download."
fi

# Download and extract Camunda Zeebe if not already present
if [ ! -d "camunda-zeebe-$CAMUNDA_VERSION" ]; then
  echo "Downloading Camunda $CAMUNDA_VERSION"
  wget -q -c -P "$tempDir" "https://github.com/camunda/camunda/releases/download/$CAMUNDA_VERSION/camunda-zeebe-$CAMUNDA_VERSION.tar.gz"

  echo "Extracting Camunda $CAMUNDA_VERSION"
  tar -xzf "$tempDir/camunda-zeebe-$CAMUNDA_VERSION.tar.gz"
else
  echo "Camunda Zeebe $CAMUNDA_VERSION already exists, skipping download."
fi

# Download Camunda Connectors if not already present
connectorsFileName="connector-runtime-bundle-$CAMUNDA_CONNECTORS_VERSION-with-dependencies.jar"
if [ ! -f "$connectorsFileName" ]; then
  echo "Downloading Camunda Connectors $CAMUNDA_CONNECTORS_VERSION"
  wget -q -c "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/$CAMUNDA_CONNECTORS_VERSION/$connectorsFileName"
else
  echo "Camunda Connectors $CAMUNDA_CONNECTORS_VERSION already exists, skipping download."
fi

# Create a tarball of the required files
echo "Creating tarball camunda8-run-$CAMUNDA_VERSION-$architecture.tar.gz"
tar -czf "camunda8-run-$CAMUNDA_VERSION-$architecture.tar.gz" \
  -C ../ \
  c8run/start.sh \
  c8run/shutdown.sh \
  c8run/endpoints.txt \
  c8run/README.md \
  c8run/connectors-application.properties \
  c8run/"$connectorsFileName" \
  c8run/internal/run.sh \
  c8run/"elasticsearch-$ELASTICSEARCH_VERSION" \
  c8run/custom_connectors \
  c8run/configuration \
  c8run/"camunda-zeebe-$CAMUNDA_VERSION"

echo "Script completed successfully."
