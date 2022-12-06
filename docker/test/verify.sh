#!/bin/bash -eu
# This script can be used to verify certain properties of a docker image.
# Returns:
#   0 on success
#   1 if one of the properties is invalid

set -o pipefail

echo "The docker image properties only need to verified on versions > 8.0"
echo "Skipping docker image verification"
