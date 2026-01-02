#!/bin/bash
#
# This script will run before the Camunda Helm chart upgrade step in the "upgrade" flow.
# Any necessary tasks should be performed here and removed after the release.
#

## TODO: Remove after the 8.8 release.

# Adding "orchestration" also, as this will change again before the final release, to avoid the need for deleting
# the StatefulSet during the upgrade. It will be again "zeebe-broker" for backward compatibility between 8.7 and 8.8.
kubectl delete sts -n "${TEST_NAMESPACE}" -l app.kubernetes.io/component=orchestration --ignore-not-found

# Remove the PostgreSQL StatefulSet and PVC as we rollback to PSQL 14 instead of PSQL 15.
kubectl delete sts -n "${TEST_NAMESPACE}" -l app.kubernetes.io/name=postgresql-web-modeler
kubectl delete pvc -n "${TEST_NAMESPACE}" -l app.kubernetes.io/name=postgresql-web-modeler
