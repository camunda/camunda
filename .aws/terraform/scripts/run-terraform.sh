#!/usr/bin/env bash
set -eux

###  Main script ###

cd .aws/terraform/${COMPONENT}

source ../scripts/init-terraform.sh

if [ "${DESTROY}" = "true" ]; then
	terraform destroy
fi

terraform plan

if [ "${APPLY}" = "true" ]; then
	terraform apply
fi
