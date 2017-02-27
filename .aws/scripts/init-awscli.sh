#!/usr/bin/env bash
set -o errexit
set -o errtrace

DATE="date +%Y/%m/%d-%H:%M:%S"
REGION=eu-west-1

# params
PROFILE=
if [ "$1" != "" ]; then
  PROFILE="--profile $1"
fi

function awscli {
  aws --region=${REGION} ${PROFILE} "$@"
}

function generatePassword {
  date +%s | sha256sum | base64 | head -c 32; echo
}

function get_latest_db_snapshot {
  local db_instance_id=$1
  local latest_snapshot
  latest_snapshot=$(awscli rds describe-db-snapshots --db-instance-identifier=${db_instance_id} --query "DBSnapshots[?SnapshotType=='automated']" | jq -r 'max_by(.SnapshotCreateTime).DBSnapshotIdentifier')
  echo "${latest_snapshot}"
}

function awscli_modify_db {
  # usage: modify_db ${database_instance_identifier} "modifications"
  local db_instance_id=$1
  local modifications=$2
  awscli rds \
      modify-db-instance \
      --db-instance-identifier=${db_instance_id} \
      ${modifications} \
      --apply-immediately && \
    echo "`${DATE}` SUCCESS:modifying  ${db_instance_id}" || echo "`${DATE}` FAILED:modifying   ${db_instance_id}"

    # modifying -> available
    awscli_wait_available ${db_instance_id} &&\
    echo "`${DATE}` SUCCESS:modified   ${db_instance_id}" || echo "`${DATE}` FAILED:modified    ${db_instance_id}"
}

function awscli_wait_available {
  # usage : wait_available ${DB_INSTANCE_ID}
  local db_instance_id=$1
  local sleep_time=10
  local num_of_sleeps=30
  echo "`${DATE}` START:available    ${db_instance_id}"

  for (( i = 0; i < ${num_of_sleeps}; i++ )); do
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
        incompatible-parameters)
          is_break=false
          is_exit=true
          ;;
        modifying)
          ;;
        rebooting)
          ;;
        resetting-master-credentials)
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

      echo -e "\033[0;32m`${DATE}` sleep ${sleep_time} (${status})\033[0;39m"
    done
  done
}
