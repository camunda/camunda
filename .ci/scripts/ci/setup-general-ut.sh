#!/bin/bash
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

set -euxo pipefail

#### Outputs a list of optimize, operate, tasklist, and zeebe modules that should be skipped in the general unit tests
#### The skipped modules are run elsewhere. This script ensures that any new modules that are added will be run by general unit test

### Get list of all modules in monorepo
# shellcheck disable=SC2005,SC2046
rawModuleList=$(echo $(python3 .ci/scripts/ci/find-pom-artifactids.py))
echo "Raw module list: $rawModuleList"

# Convert the module list string into an array
IFS=' ' read -ra items <<< "$rawModuleList"

# Initialize an empty array for filtered items
filtered_items=()

# Loop through each item and construct list of Operate, Optimize, Tasklist, and Zeebe modules. These modules will be removed
for item in "${items[@]}"; do
  if [[ $item == *optimize* || $item == tasklist* || $item == operate* || $item == zeebe* ]]; then
    filtered_items+=("$item")
  fi
done

# these modules shouldn't be removed and need to be included for a successful run
doNotSkip="$1"

for i in "${!filtered_items[@]}"; do
  word="${filtered_items[$i]}"
  # shellcheck disable=SC1087
  if [[ " $doNotSkip " =~ [[:space:]]$word[[:space:]] ]]; then
    unset 'filtered_items[i]'  # Remove the module from the array
  fi
done

# Join the filtered items back into a string
IFS=' '; modules="${filtered_items[*]}"

### Add Extra modules to skip, these are not Zeebe/Operate/Tasklist/Optimize modules
modules+=" $2"

### Format modules for Maven ('-:<moduleName>') and Gradle ('-x :<moduleName>:test')
gradle_formatted_modules=()
maven_formatted_modules=()

for module in $modules; do
  # Skip aggregator parent modules — they have no source or test task
  if [[ $module != *-parent ]]; then
    gradle_formatted_modules+=("-x :$module:test")
  fi
  maven_formatted_modules+=("'-:$module'")
done

gradle_ut_modules=$(IFS=' '; echo "${gradle_formatted_modules[*]}")
maven_ut_modules=$(IFS=','; echo "${maven_formatted_modules[*]}")

echo "Gradle test exclusions: $gradle_ut_modules"
echo "Maven module exclusions: $maven_ut_modules"

# shellcheck disable=SC2086
echo "GENERAL_UT_GRADLE_MODULES=$gradle_ut_modules" >> "$GITHUB_ENV"
# shellcheck disable=SC2086
echo "GENERAL_UT_MAVEN_MODULES=$maven_ut_modules" >> "$GITHUB_ENV"
