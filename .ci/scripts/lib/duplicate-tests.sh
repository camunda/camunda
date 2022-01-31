#!/bin/bash
#######################################
# Parses Maven output to find if any tests was run more than once.
# Globals:
#   None
# Arguments:
#   1 - file containing the test outputs
#   2 - writable file which where the duplicate tests will be written
# Outputs:
#   Error message on STDERR if duplicate tests were found.
#   Error message on STDERR if no tests were found, indicating the function is broken
# Returns:
#   0 if no tests were run multiple times
#   1 if at least one test was run multiple times or of no tests were found
#######################################
function findDuplicateTestRuns() {
  local -r testInputFile="$1"
  local -r outputFile="$2"
  local -r tmpFile=$(mktemp)
  grep -oP "\[INFO\] Running \K(io\.camunda\..*)$" "$testInputFile" > "$tmpFile"
  if [ -s "$tmpFile" ]; then  # found tests
      sort "$tmpFile" | uniq -d | tee "$outputFile"
      if [ -s "$outputFile" ]; then
          echo "Found duplicate test runs!" >&2
          return 1
      fi
      return 0
  fi
  echo "Could not find any tests, duplicate detection is broken." >&2
  return 1
}
