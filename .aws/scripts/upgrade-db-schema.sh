#!/usr/bin/env bash
set -ex

source .aws/scripts/init-awscli.sh

DB_INSTANCE_ID="camunda-optimize-db-stage"
PGPASSWORD=${1:-camunda123}
DB_NAME=$(awscli rds describe-db-instances --query "DBInstances[?DBInstanceIdentifier=='${DB_INSTANCE_ID}'].DBName" --output text)
DB_HOST=$(awscli rds describe-db-instances --query "DBInstances[?DBInstanceIdentifier=='${DB_INSTANCE_ID}'].Endpoint.Address" --output text)
REMOTE_HOST_IP=$(awscli ec2 describe-instances --filters 'Name=tag:Name,Values=Camunda Optimize*' --query 'Reservations[*].Instances[*].PublicIpAddress' --output text)

cd .aws/ansible || exit 1

cat << EOF > hosts
[optimize]
${REMOTE_HOST_IP}
EOF

ansible-playbook bootstrap-python.yml -i hosts

ansible-playbook upgrade-db.yml \
  -i hosts \
  -e db_name=${DB_NAME} \
  -e db_host=${DB_HOST} \
  -e db_password=${PGPASSWORD}
