#!/usr/bin/env bash
# Recovery tool for orphaned Aurora async-replication IT infrastructure.
#
# When the nightly "Aurora Async Replication IT" job fails to tear itself down,
# it leaves an RDS global cluster, subnet groups, security groups, an IAM role
# and a bastion EC2 instance behind — all tagged `Name=<prefix>` and
# `Purpose=aurora-async-replication-it`, where <prefix> is
#   aurora-it-<run_id>-<db_engine>.
#
# Two teardown paths:
#   1. Terraform (preferred). If the run's remote state still exists, we re-init
#      against it and `terraform destroy` — terraform already encodes the correct
#      dependency order.
#   2. Raw `aws` fallback. If the state is gone (or destroy left resources
#      behind), we delete by discovered identifier in the required order. This is
#      only safe because <prefix> contains the run id, so the filters cannot match
#      another run's resources.
#
# See README.md for the full runbook.
#
# Usage:
#   ./cleanup.sh <run_id> <db_engine>          # tear down the run's resources
#   ./cleanup.sh --list <run_id> <db_engine>   # only list what is still tagged
#   ./cleanup.sh --prefix <prefix> [db_engine] # target an explicit prefix
#
#   db_engine is `mysql` or `postgresql`. With --prefix it is optional and only
#   affects the terraform path's `engine` var (defaults to postgresql); use it
#   to clean up resources whose name does not follow the aurora-it-<run>-<engine>
#   convention (e.g. old-scheme `aurora-it-<run_id>`). --list works with --prefix
#   too: `./cleanup.sh --list --prefix <prefix>`.
#   Set AUTO_APPROVE=true to skip the confirmation prompt (the CI wrapper does).
#
# Requires: AWS credentials in the environment, and terraform + aws + jq on PATH.
# Run from this directory.
set -euo pipefail

# AWS CLI v2 pipes output through a pager (less) by default, which hangs waiting
# for 'q' — both interactively and in CI. Disable it for every aws call here.
export AWS_PAGER=""

PRIMARY_REGION="${TF_VAR_primary_region:-eu-west-1}"
SECONDARY_REGION="${TF_VAR_secondary_region:-eu-west-2}"
STATE_BUCKET_REGION="${TF_STATE_BUCKET_REGION:-eu-west-1}"

LIST_ONLY=false
USE_PREFIX=false
if [[ "${1:-}" == "--list" ]]; then
  LIST_ONLY=true
  shift
fi
if [[ "${1:-}" == "--prefix" ]]; then
  USE_PREFIX=true
  shift
fi

if [[ "${USE_PREFIX}" == true ]]; then
  PREFIX="${1:?prefix required after --prefix}"
  DB_ENGINE="${2:-postgresql}"
else
  RUN_ID="${1:?run_id required (the failing GitHub Actions run id)}"
  DB_ENGINE="${2:?db_engine required (mysql|postgresql)}"
  PREFIX="aurora-it-${RUN_ID}-${DB_ENGINE}"
fi

case "${DB_ENGINE}" in
  mysql)      TF_ENGINE="aurora-mysql" ;;
  postgresql) TF_ENGINE="aurora-postgresql" ;;
  *) echo "db_engine must be 'mysql' or 'postgresql', got '${DB_ENGINE}'" >&2; exit 1 ;;
esac

echo "Target resource prefix: ${PREFIX}"

# --- discovery ---------------------------------------------------------------
# The Resource Groups Tagging API does NOT support wildcards in tag-filter
# values, so we cannot match `${PREFIX}-*`. Instead we filter server-side on the
# shared `Purpose` tag and narrow to this run client-side with a JMESPath
# `starts_with` on the `Name` tag (which is `${PREFIX}` or `${PREFIX}-<suffix>`).
# $1 region, $2 optional resource-type filter (e.g. `kms`).
tagged_arns() {
  local region="$1" rtf="${2:-}" args
  args=(--region "${region}" --tag-filters "Key=Purpose,Values=aurora-async-replication-it")
  [[ -n "${rtf}" ]] && args+=(--resource-type-filters "${rtf}")
  aws resourcegroupstaggingapi get-resources "${args[@]}" \
    --query "ResourceTagMappingList[?Tags[?Key=='Name' && starts_with(Value, '${PREFIX}')]].ResourceARN" \
    --output text 2>/dev/null || true
}

list_orphans() {
  for region in "${PRIMARY_REGION}" "${SECONDARY_REGION}"; do
    echo "== tagged resources in ${region} =="
    tagged_arns "${region}" | tr '\t' '\n'
  done
}

# Count tagged resources across both regions (used to decide whether the aws
# fallback still has work to do).
count_orphans() {
  local total=0 region arns
  for region in "${PRIMARY_REGION}" "${SECONDARY_REGION}"; do
    arns="$(tagged_arns "${region}")"
    total=$((total + $(printf '%s' "${arns}" | wc -w)))
  done
  echo "${total}"
}

list_orphans
if [[ "${LIST_ONLY}" == true ]]; then
  exit 0
fi

if [[ "${AUTO_APPROVE:-false}" != "true" && -t 0 ]]; then
  read -r -p "Delete everything tagged '${PREFIX}' above? [y/N] " reply
  [[ "${reply}" == "y" || "${reply}" == "Y" ]] || { echo "Aborted."; exit 1; }
fi

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
STATE_BUCKET="aurora-it-tf-state-${ACCOUNT_ID}"
STATE_KEY="aurora-replication-it/tfstate-${PREFIX}/${PREFIX}.tfstate"

# --- path 1: teardown via terraform (correct dependency order) --------------
if aws s3api head-object --bucket "${STATE_BUCKET}" --key "${STATE_KEY}" --region "${STATE_BUCKET_REGION}" >/dev/null 2>&1; then
  echo "Terraform state found — destroying via terraform."
  terraform init -reconfigure \
    -backend-config="bucket=${STATE_BUCKET}" \
    -backend-config="key=${STATE_KEY}" \
    -backend-config="region=${STATE_BUCKET_REGION}" \
    -backend-config="encrypt=true"
  # master_password has no default and is irrelevant to destroy; pass a placeholder.
  terraform destroy -auto-approve \
    -var "name_prefix=${PREFIX}" \
    -var "engine=${TF_ENGINE}" \
    -var "primary_region=${PRIMARY_REGION}" \
    -var "secondary_region=${SECONDARY_REGION}" \
    -var "master_password=unused-for-destroy" || echo "terraform destroy reported errors — will sweep leftovers with aws."
else
  echo "No Terraform state at s3://${STATE_BUCKET}/${STATE_KEY} — using aws fallback."
fi

# --- path 2: raw aws fallback for anything terraform could not remove -------
# Deletes in the order the two-region Aurora global cluster requires:
#   instances -> clusters (detached from global) -> global cluster
#   -> subnet groups -> security groups -> bastion EC2 -> IAM.

wait_quiet() { aws "$@" 2>/dev/null || true; }

delete_db_cluster() {
  # $1 region, $2 cluster identifier
  local region="$1" cluster="$2" members arn
  aws rds describe-db-clusters --region "${region}" --db-cluster-identifier "${cluster}" \
    >/dev/null 2>&1 || return 0

  members="$(aws rds describe-db-clusters --region "${region}" --db-cluster-identifier "${cluster}" \
    --query 'DBClusters[0].DBClusterMembers[].DBInstanceIdentifier' --output text 2>/dev/null || true)"
  for inst in ${members}; do
    echo "  deleting instance ${inst} (${region})"
    wait_quiet rds delete-db-instance --region "${region}" --db-instance-identifier "${inst}" --skip-final-snapshot
  done
  for inst in ${members}; do
    wait_quiet rds wait db-instance-deleted --region "${region}" --db-instance-identifier "${inst}"
  done

  # Detach from the global cluster before the cluster itself can be deleted.
  arn="$(aws rds describe-db-clusters --region "${region}" --db-cluster-identifier "${cluster}" \
    --query 'DBClusters[0].DBClusterArn' --output text 2>/dev/null || true)"
  if [[ -n "${arn}" && "${arn}" != "None" ]]; then
    wait_quiet rds remove-from-global-cluster --global-cluster-identifier "${PREFIX}-global" --db-cluster-identifier "${arn}"
  fi

  echo "  deleting cluster ${cluster} (${region})"
  wait_quiet rds delete-db-cluster --region "${region}" --db-cluster-identifier "${cluster}" --skip-final-snapshot
  wait_quiet rds wait db-cluster-deleted --region "${region}" --db-cluster-identifier "${cluster}"
}

delete_subnet_group() {
  # $1 region, $2 name
  aws rds describe-db-subnet-groups --region "$1" --db-subnet-group-name "$2" >/dev/null 2>&1 || return 0
  echo "  deleting subnet group $2 ($1)"
  wait_quiet rds delete-db-subnet-group --region "$1" --db-subnet-group-name "$2"
}

delete_kms_keys() {
  # $1 region — schedule deletion of every customer KMS key tagged with the prefix.
  # KMS keys cannot be deleted immediately (7-day minimum window), so we schedule
  # deletion and then strip the tags so the key drops out of the orphan tag filter
  # (it lingers in PendingDeletion until the window elapses, which is expected).
  local region="$1" arns keyid state tagkeys
  arns="$(tagged_arns "${region}" kms)"
  for arn in ${arns}; do
    keyid="${arn##*/}"
    state="$(aws kms describe-key --region "${region}" --key-id "${keyid}" \
      --query 'KeyMetadata.KeyState' --output text 2>/dev/null || echo Unknown)"
    if [[ "${state}" != "PendingDeletion" ]]; then
      echo "  scheduling KMS key ${keyid} for deletion (${region})"
      wait_quiet kms schedule-key-deletion --region "${region}" --key-id "${keyid}" --pending-window-in-days 7
    fi
    tagkeys="$(aws kms list-resource-tags --region "${region}" --key-id "${keyid}" \
      --query 'Tags[].TagKey' --output text 2>/dev/null || true)"
    [[ -n "${tagkeys}" ]] && wait_quiet kms untag-resource --region "${region}" --key-id "${keyid}" --tag-keys ${tagkeys}
  done
}

delete_security_groups() {
  # $1 region — delete every SG whose Name tag matches the prefix.
  local region="$1" ids
  ids="$(aws ec2 describe-security-groups --region "${region}" \
    --filters "Name=tag:Name,Values=${PREFIX},${PREFIX}-*" \
    --query 'SecurityGroups[].GroupId' --output text 2>/dev/null || true)"
  for id in ${ids}; do
    echo "  deleting security group ${id} (${region})"
    wait_quiet ec2 delete-security-group --region "${region}" --group-id "${id}"
  done
}

if [[ "$(count_orphans)" -gt 0 ]]; then
  echo "Sweeping remaining resources with aws..."

  # Terminate the bastion first: it holds the security group and instance profile.
  BASTION_ID="$(aws ec2 describe-instances --region "${PRIMARY_REGION}" \
    --filters "Name=tag:Name,Values=${PREFIX}-bastion" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null || true)"
  for id in ${BASTION_ID}; do
    echo "  terminating bastion ${id}"
    wait_quiet ec2 terminate-instances --region "${PRIMARY_REGION}" --instance-ids "${id}"
    wait_quiet ec2 wait instance-terminated --region "${PRIMARY_REGION}" --instance-ids "${id}"
  done

  # Secondary (read replica) before primary, then the global cluster shell.
  delete_db_cluster "${SECONDARY_REGION}" "${PREFIX}-secondary"
  delete_db_cluster "${PRIMARY_REGION}" "${PREFIX}-primary"
  if aws rds describe-global-clusters --region "${PRIMARY_REGION}" --global-cluster-identifier "${PREFIX}-global" >/dev/null 2>&1; then
    echo "  deleting global cluster ${PREFIX}-global"
    wait_quiet rds delete-global-cluster --region "${PRIMARY_REGION}" --global-cluster-identifier "${PREFIX}-global"
  fi

  delete_subnet_group "${PRIMARY_REGION}" "${PREFIX}-primary"
  delete_subnet_group "${SECONDARY_REGION}" "${PREFIX}-secondary"

  # Security groups only detach once the instances/clusters using them are gone.
  delete_security_groups "${PRIMARY_REGION}"
  delete_security_groups "${SECONDARY_REGION}"

  # KMS keys used for cluster encryption (scheduled for deletion, not immediate).
  delete_kms_keys "${PRIMARY_REGION}"
  delete_kms_keys "${SECONDARY_REGION}"

  # IAM (global): instance profile must release the role before either is deleted.
  if aws iam get-instance-profile --instance-profile-name "${PREFIX}-bastion" >/dev/null 2>&1; then
    echo "  deleting IAM instance profile ${PREFIX}-bastion"
    wait_quiet iam remove-role-from-instance-profile --instance-profile-name "${PREFIX}-bastion" --role-name "${PREFIX}-bastion"
    wait_quiet iam delete-instance-profile --instance-profile-name "${PREFIX}-bastion"
  fi
  if aws iam get-role --role-name "${PREFIX}-bastion" >/dev/null 2>&1; then
    echo "  deleting IAM role ${PREFIX}-bastion"
    wait_quiet iam detach-role-policy --role-name "${PREFIX}-bastion" --policy-arn "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
    wait_quiet iam delete-role --role-name "${PREFIX}-bastion"
  fi
fi

# --- purge the state object (versioned bucket) ------------------------------
echo "Deleting Terraform state object versions for ${STATE_KEY}"
RAW="$(aws s3api list-object-versions --region "${STATE_BUCKET_REGION}" --bucket "${STATE_BUCKET}" --prefix "${STATE_KEY}" --output json 2>/dev/null || echo '{}')"
OBJECTS="$(echo "${RAW}" | jq '{Objects: ((.Versions // []) + (.DeleteMarkers // [])) | map({Key: .Key, VersionId: .VersionId})}')"
COUNT="$(echo "${OBJECTS}" | jq '.Objects | length')"
if [[ "${COUNT}" -gt 0 ]]; then
  aws s3api delete-objects --region "${STATE_BUCKET_REGION}" --bucket "${STATE_BUCKET}" --delete "${OBJECTS}" >/dev/null
  echo "Deleted ${COUNT} state object version(s)."
else
  echo "No state objects found for ${STATE_KEY}."
fi

echo "Verifying nothing remains tagged..."
list_orphans
if [[ "$(count_orphans)" -gt 0 ]]; then
  echo "WARNING: resources are still tagged above — inspect them manually in the AWS console." >&2
  exit 1
fi
echo "Done — no resources remain for ${PREFIX}."
