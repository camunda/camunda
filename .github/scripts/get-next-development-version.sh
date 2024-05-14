#!/bin/bash

# fail fast and print each statement
set -ex

if [[ -z "$RELEASE_VERSION" ]]; then
  echo "Release version is required"
  exit 1
fi

if [[ -z "$RELEASE_TYPE" ]]; then
  echo "Release type is required"
  exit 1
fi

# Set the Internal Field Separator to '.'
IFS='.' read -r -a numbers <<<"$RELEASE_VERSION"

if [[ "$RELEASE_TYPE" == "major" ]]; then
  numbers[0]=$((numbers[0] + 1))
elif [[ "$RELEASE_TYPE" == "minor" ]]; then
  numbers[1]=$((numbers[1] + 1))
elif [[ "$RELEASE_TYPE" == "alpha" ]]; then
  # here we don't do anything
  continue
else
  echo "Invalid release type"
  exit 1
fi

echo "next_development_version=${numbers[0]}.${numbers[1]}.${numbers[2]}" >>$GITHUB_OUTPUT
