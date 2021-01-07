#!/bin/bash

#######################################
# Analyses flaky tests produced by flaky-test-extractor-maven-plugin
# Globals:
#   None
# Arguments:
#   1 - file containing the test outputs
#   2 - file to which the flaky tests should be appended
# Outputs:
#   Error message on STDERR if flaky tests are detected
# Returns:
#   0 if no flaky tests detected
#   1 if flaky tests were detected
#######################################
function analyseFlakyTests() {
  local -r testInputFile="$1"
  local -r testOutputFile="$2"
  local -r irOutputFile=$(mktemp)

  if grep -q "\[WARNING\] Flakes:" ${testInputFile}; then
    tmpfile2=$(mktemp)
    awk '/^\[WARNING\] Flakes:.*$/{flag=1}/^\[ERROR\] Tests run:.*Flakes: [0-9]*$/{print;flag=0}flag' ${testInputFile} > ${irOutputFile}
    grep "\[ERROR\]   Run 1: " ${irOutputFile} | awk '{print $4}' >> ${testOutputFile}
    echo 'ERROR: Flaky Tests detected'>&2
    return 1
  fi

  return 0
}
