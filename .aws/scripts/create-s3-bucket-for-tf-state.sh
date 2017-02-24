#!/usr/bin/env bash
#
# This script is ONLY for documentation.
# Use it has to be executed only ONE time except:
# - the remote terraform state is gone from S3 due to DRAGON ATTACK.
#
set -o errexit
set -o nounset
set -o errtrace

cd .aws/terraform/s3

rm -rf .terraform

terraform plan

if [ "${APPLY}" = "true" ]; then
  terraform apply
fi
