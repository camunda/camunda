#!/bin/bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

SCRIPT_FOLDER="$(dirname "$0")"
cd "$SCRIPT_FOLDER" || (echo "Missing folder $SCRIPT_FOLDER"  && exit 1)

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

JAR="target/benchmarks.jar"

if [ ! -f "$JAR" ]; then
    echo -e "${BLUE}Building benchmark JAR...${NC}"
    cd ..
    ./mvnw clean package -pl microbenchmarks -am -DskipTests
    cd "$SCRIPT_FOLDER" || (echo "Missing folder $SCRIPT_FOLDER"  && exit 1)
fi

if [ ! -f "$JAR" ]; then
    echo "Error: Failed to build $JAR"
    exit 1
fi

BENCHMARKS="$@"

echo -e "${GREEN}Running Benchmarks: ${BENCHMARKS}...${NC}"
if [ $# -eq 0 ]; then
  echo "No benchmark specified, running all of them."
fi
echo ""

java -jar "$JAR" "$BENCHMARKS"
