#!/usr/bin/env bash
set -ex

DATE="date +%Y/%m/%d-%H:%M:%S"
REGION=eu-west-1
DB_INSTANCE_ID=processenginedemo
DB_NEW_PASSWORD=camunda

# params
PROFILE=
if [ "$1" != "" ]; then
  PROFILE="--profile $1"
fi

function awscli {
  aws --region=${REGION} ${PROFILE} "$@"
}

function get_latest_db_snapshot {
  local db_instance_id=$1
  local latest_snapshot
  latest_snapshot=$(awscli rds describe-db-snapshots --db-instance-identifier=${db_instance_id} --query "DBSnapshots[?SnapshotType=='automated']" | jq -r 'max_by(.SnapshotCreateTime).DBSnapshotIdentifier')
  echo "${latest_snapshot}"
}

function generatePassword {
  date +%s | sha256sum | base64 | head -c 32; echo
}

function modify_db {
  # usage: modify_db ${database_instance_identifier}
  local db_instance_id=$1
  awscli rds \
      modify-db-instance \
      --db-instance-identifier ${db_instance_id} \
      --master-user-password ${DB_NEW_PASSWORD} \
      --backup-retention-period 0 \
      --apply-immediately && \
    echo "`${DATE}` SUCCESS:modifying  ${db_instance_id}" || echo "`${DATE}` FAILED:modifying   ${db_instance_id}"
#       --master-user-password ${DB_NEW_PASSWORD} \
#       --vpc-security-group-ids ${_DB_SEC_GROUP} \


    # modifying -> available
    wait_available ${db_instance_id} &&\
    echo "`${DATE}` SUCCESS:modified   ${db_instance_id}" || echo "`${DATE}` FAILED:modified    ${db_instance_id}"
}

function wait_available {
  # usage : wait_available ${DB_INSTANCE_ID}
  local db_instance_id=$1
  local sleep_time=10
  echo "`${DATE}` START:available    ${db_instance_id}"

  for (( i = 0; i < 3; i++ )); do
    while :
    do
      sleep ${sleep_time}
      status=`awscli rds describe-db-instances --query 'DBInstances[?DBInstanceIdentifier==\`'${db_instance_id}'\`]'.DBInstanceStatus --output text`
      is_break=false
      is_exit=false
      case ${status} in
        available)
          is_break=true;;
        backing-up)
          ;;
        creating)
          ;;
        deleted)
          is_break=false
          is_exit=true
          ;;
        deleting)
          is_break=false
          is_exit=true
          ;;
        failed)
          is_break=false
          is_exit=true
          ;;
        incompatible-restore)
          is_break=false
          is_exit=true
          ;;
        incompatible-paraameters)
          is_break=false
          is_exit=true
          ;;
        modifying)
          ;;
        rebooting)
          ;;
        resetting-master-credentials)
          is_break=false
          is_exit=true
          ;;
        storage-full)
          is_break=false
          is_exit=true
          ;;
        *)
          is_break=false
          is_exit=true
          ;;
      esac

      if ${is_break}; then
        echo -e "\033[0;35m`${DATE}` break (${status})\033[0;39m"
        break
      fi

      if ${is_exit}; then
        echo -e "\033[0;31m`${DATE}` exit 1 (${status})\033[0;39m"
        exit 1
      fi

      echo -e "\033[0;32m`${DATE}` sleep ${_SLEEP_TIME} (${status})\033[0;39m"
    done
  done
}



###  Main script ###

cd .aws/terraform/db

source ../scripts/init-terraform.sh

if [ "${DESTROY}" = "true" ]; then
	terraform destroy -force
fi

latest_snapshot="$(get_latest_db_snapshot ${DB_INSTANCE_ID})"
terraform plan -var db_latest_snapshot=${latest_snapshot}

if [ "${APPLY}" = "true" ]; then
	terraform apply -var db_latest_snapshot=${latest_snapshot}
  new_db_instance_id="$(terraform output db-instance-identifier)"
  modify_db ${new_db_instance_id}
fi
