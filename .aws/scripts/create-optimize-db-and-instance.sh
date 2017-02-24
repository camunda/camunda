#!/usr/bin/env bash
set -o errexit
set -o errtrace

__db_instance_id=${1:-processenginedemo}
__db_new_password=${2:-camunda123}
__tf_plan=setup_db_and_instance.plan

cd .aws/terraform/optimize

source ../scripts/init-terraform.sh
source ../../scripts/init-awscli.sh

if [ "${DESTROY}" = "true" ]; then
  terraform destroy -force
fi

latest_snapshot="$(get_latest_db_snapshot ${__db_instance_id})"
terraform plan -var db_latest_snapshot=${latest_snapshot} -out ${__tf_plan}

if [ "${APPLY}" = "true" ]; then
  terraform apply ${__tf_plan}
  __new_db_instance_id="$(terraform output db-instance-identifier)"
  # change master password and disable backup
  awscli_modify_db ${__new_db_instance_id} "--master-user-password ${__db_new_password} --backup-retention-period 0"
fi
