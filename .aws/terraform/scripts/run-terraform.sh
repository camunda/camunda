#!/usr/bin/env bash
set -eux

component=${1:-COMPONENT}
destroy=${DESTROY:-false}
apply=${APPLY:-false}
###  Main script ###

cd .aws/terraform/${component}

source ../scripts/init-terraform.sh

if [ "${destroy}" = "true" ]; then
	terraform destroy
fi

terraform plan

if [ "${apply}" = "true" ]; then
	terraform apply
fi
