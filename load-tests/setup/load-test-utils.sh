#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

# create test profiles in bulk
for i in 3 5 7 9 11 13 15; do
  ./newLoadTest.sh "kit-optimize-2-${i}"
done


# start test profiles in bulk
for i in 3 5 7 9 11 13 15; do
  cd "kit-optimize-2-${i}" || exit 1
  make install
  cd ..
done

for i in 1 2 3 4; do
  ns="kit-optimize-2-${i}"

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

kubectl delete namespace kit-optimize-2-1
