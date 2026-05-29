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

items=()
declare -A seen_items=()
declare -A module_paths=()
project_dir_pattern='project\(":([^"]+)"\)\.projectDir[[:space:]]*=[[:space:]]*file\("([^"]+)"\)'

while IFS= read -r line; do
  if [[ $line =~ $project_dir_pattern ]]; then
    module="${BASH_REMATCH[1]}"
    path="${BASH_REMATCH[2]}"

    if [[ $path == optimize/* || $path == tasklist/* || $path == operate/* || $path == zeebe/* ]]; then
      if [[ -z "${seen_items[$module]:-}" ]]; then
        items+=("$module")
        seen_items["$module"]=1
        module_paths["$module"]="$path"
      fi
    fi
  fi
done < settings.gradle.kts

echo "Raw module list: ${items[*]}"

filtered_items=()
for item in "${items[@]}"; do
  project_path="${module_paths[$item]:-}"

  if [[ -n "$project_path" && -d "$project_path/src/test" ]]; then
    filtered_items+=("$item")
  fi
done

doNotSkip="$1"
for i in "${!filtered_items[@]}"; do
  word="${filtered_items[$i]}"
  # shellcheck disable=SC1087
  if [[ " $doNotSkip " =~ [[:space:]]$word[[:space:]] ]]; then
    unset 'filtered_items[i]'
  fi
done

IFS=' '
modules="${filtered_items[*]}"
modules+=" $2"

formatted_modules=()
declare -A seen_formatted_modules=()

for module in $modules; do
  formatted_module="-x :$module:test"
  if [[ -z "${seen_formatted_modules[$formatted_module]:-}" ]]; then
    formatted_modules+=("$formatted_module")
    seen_formatted_modules["$formatted_module"]=1
  fi
done

ut_modules=$(IFS=' '; echo "${formatted_modules[*]}")

echo "Gradle test exclusions: $ut_modules"

# shellcheck disable=SC2086
echo GENERAL_UT_MODULES=$ut_modules >> "$GITHUB_ENV"
