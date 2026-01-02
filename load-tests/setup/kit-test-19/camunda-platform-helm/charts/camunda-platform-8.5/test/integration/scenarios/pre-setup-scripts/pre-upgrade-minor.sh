#!/bin/bash
#
# This script will run before the Camunda Helm chart upgrade step in the "upgrade-minor" flow.
# Any necessary tasks should be performed here and removed after the release.
#

set -x

# Delete the Identity deployment as we moved Identity as part of the main chart instead of a subchart.
kubectl delete deployment -n "${TEST_NAMESPACE}" -l app.kubernetes.io/component=identity --ignore-not-found
