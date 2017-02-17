#!/usr/bin/env bash
set -eux

cd .aws/terraform/s3

source ../scripts/init-terraform.sh

terraform plan

if [ "${APPLY}" = "true" ]; then
	terraform apply
fi
