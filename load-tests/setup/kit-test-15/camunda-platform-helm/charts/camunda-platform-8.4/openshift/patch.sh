#!/bin/bash -eu
# Expected usage is as an Helm post renderer.
# Example usage:
#   $ helm install my-release camunda/camunda-platform --post-renderer ./patch.sh
#
# This script is a Helm chart post-renderer for users on Helm 3.2.0 and greater. It allows removing default
# values set in sub-charts/dependencies, something which should be possible but is currently not working.
# See this issue for more: https://github.com/helm/helm/issues/9136
#
# The result of patching the rendered Helm templates is printed out to STDOUT. Any other logging from the
# script is thus sent to STDERR.
#
# Note to contributors: this post-renderer is used in the integration tests, so make sure that it can be used
# from any working directory.

set -o pipefail

if [[ "$(uname)" == "Darwin" ]]; then
    sed_command="gsed"
else
    sed_command="sed"
fi

"$sed_command" -e '/\srunAsUser:\s/d' -e '/\sfsGroup:\s/d'