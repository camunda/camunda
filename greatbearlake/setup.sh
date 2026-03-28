#!/usr/bin/env bash
# setup.sh – Build the Iceberg ingestion JAR before running docker compose.
#
# JARs for Flink (Iceberg runtime, Hadoop common) are downloaded automatically
# by the jar-downloader container — no manual download step required.
#
# Run this once before `docker compose up -d` if you want to include the
# Flink ingestion job. Skip it if you only want the HDFS + Hive + Trino stack.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> Building greatbearlake-iceberg-ingestion fat-JAR"
mvn -f "${REPO_ROOT}/pom.xml" \
    -pl greatbearlake/iceberg-ingestion \
    -am \
    package -DskipTests -q

BUILT_JAR="${SCRIPT_DIR}/iceberg-ingestion/target/greatbearlake-iceberg-ingestion-"*"-SNAPSHOT.jar"
JAR="${SCRIPT_DIR}/iceberg-ingestion/target/greatbearlake-iceberg-ingestion.jar"
# shellcheck disable=SC2086
cp $BUILT_JAR "${JAR}"
echo "==> JAR ready: ${JAR}"

echo ""
echo "You can now start the stack:"
echo "  docker compose -f ${SCRIPT_DIR}/docker-compose.yml up -d"
