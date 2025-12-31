#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
for i in 7; do
  ns="kit-test-${i}"

  # Create namespace only if it doesn't exist
  if ! kubectl get namespace "$ns" >/dev/null 2>&1; then
    kubectl create namespace "$ns"
  else
    echo "Namespace $ns already exists, skipping creation"
  fi

  cd "$ns" || exit 1
  make install
  cd ..
done
