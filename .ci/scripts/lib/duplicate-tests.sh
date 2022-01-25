#!/bin/bash

# Script that parses maven output to find any tests that are run more than once.
# Returns 1 when no tests are found (likely an error, for example if the maven output changes) or
# if any test is run more than once. Otherwise return 0.

# We are using files instead of variables so that we don't spam stdout too much when tracing is enabled.
function findDuplicateTestRuns() {
  local -r testInputFile="$1"
  local -r outputFile="$2"
  local -r tmpFile=$(mktemp)
  grep -oP "\[INFO\] Running \K(io\.camunda\..*)$" "$testInputFile" > "$tmpFile"
  if [ -s "$tmpFile" ]; then  # found tests
      sort "$tmpFile" | uniq -d | tee "$outputFile"
      if [ -s "$outputFile" ]; then
          echo "Found duplicate test runs!"
          return 1
      fi
      return 0
  fi
  echo "Could not find any tests, duplicate detection is broken."
  return 1
}
