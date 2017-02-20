#!/usr/bin/env bash
set -ex

DB_INSTANCE_ID=${1:-processenginedemo}
DB_NEW_PASSWORD=${2:-camunda123}
TF_PLAN=setup_db_and_instance.plan

cd .aws/terraform/optimize

source ../scripts/init-terraform.sh
source ../../scripts/init-awscli.sh

if [ "${DESTROY}" = "true" ]; then
  terraform destroy -force
fi

latest_snapshot="$(get_latest_db_snapshot ${DB_INSTANCE_ID})"
terraform plan -var db_latest_snapshot=${latest_snapshot} -out ${TF_PLAN}

if [ "${APPLY}" = "true" ]; then
  terraform apply ${TF_PLAN}
  new_db_instance_id="$(terraform output db-instance-identifier)"
  # change master password and disable backup
  awscli_modify_db ${new_db_instance_id} "--master-user-password ${DB_NEW_PASSWORD} --backup-retention-period 0"
fi
