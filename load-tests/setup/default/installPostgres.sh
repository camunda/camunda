#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

set -exo pipefail

if [ -z "$1" ]
then
  echo "Please provide a namespace name!"
  exit 1
fi

### Load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created
namespace=$1

helm upgrade --install postgres oci://registry-1.docker.io/bitnamicharts/postgresql \
  --namespace $namespace \
  --wait --timeout 5m0s \
  --set global.postgresql.auth.database=camunda \
  --set global.postgresql.auth.username=camunda \
  --set global.postgresql.auth.password=camunda \
  --set primary.resources.requests.memory=4Gi \
  --set primary.resources.requests.cpu=3000m \
  --set primary.resources.limits.memory=6Gi \
  --set primary.resources.limits.cpu=6000m \
  --set primary.persistence.size=10Gi
