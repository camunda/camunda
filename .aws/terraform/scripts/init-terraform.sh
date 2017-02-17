#!/bin/sh -eux

bucket_id="camunda-optimize-terraform"
region="eu-west-1"

folder=$(basename "${PWD}")

rm -rf .terraform

terraform remote config \
    -backend=s3 \
    -backend-config="bucket=${bucket_id}" \
    -backend-config="region=${region}" \
    -backend-config="encrypt=true" \
    -backend-config="key=${folder}/terraform.tfstate"

terraform get
