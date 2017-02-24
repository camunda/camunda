#!/usr/bin/env bash
set -o errexit
set -o errtrace

source .aws/scripts/init-awscli.sh
source .aws/scripts/init-ansible-vault.sh

DB_INSTANCE_ID="camunda-optimize-db-stage"
DB_NAME=$(awscli rds describe-db-instances --query "DBInstances[?DBInstanceIdentifier=='${DB_INSTANCE_ID}'].DBName" --output text)
DB_HOST=$(awscli rds describe-db-instances --query "DBInstances[?DBInstanceIdentifier=='${DB_INSTANCE_ID}'].Endpoint.Address" --output text)
REMOTE_HOST_IP=$(awscli ec2 describe-instances --filters 'Name=tag:Name,Values=Camunda Optimize*' --query 'Reservations[*].Instances[*].PublicIpAddress' --output text)

cd .aws/ansible || exit 1

cat << EOF > hosts
[optimize]
${REMOTE_HOST_IP}
EOF

ansible-playbook bootstrap-python.yml -i hosts

ansible-playbook camunda-bpm.yml \
  -i hosts \
  -e db_name=${DB_NAME} \
  -e db_host=${DB_HOST}
