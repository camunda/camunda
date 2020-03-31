#!/bin/bash

set -euo pipefail

usage() {
  echo "Usage: ${0} --action=[create|delete] --namespace=<namespace>"
}

fail() {
  usage
  echo "[${0}] Error: ${1}"
  exit 1
}

namespace_create() {
  set +e
  kubectl get namespace "${1}" > /dev/null 2>&1
  if [[ $? -eq 0 ]]; then
    fail "Namespace ${1} already exists. Is the previous test still running?"
  fi
  set -e

  kubectl create namespace "${1}"
}

namespace_delete() {
  set +e
  kubectl delete namespace "${1}"
  set -e
}

ACTION=""
NAMESPACE=""

for i in "$@"
do
case $i in
  -a=*|--action=*)
    ACTION="${i#*=}"
    shift
    ;;
  -ns=*|--namespace=*)
    NAMESPACE="${i#*=}"
    shift
    ;;
  *)
    fail "Unknown option provided"
    ;;
esac
done

if ! [[ "${ACTION}" =~ ^(create|delete)$ ]]; then
  fail "Action must be either 'create' or 'delete', got '${ACTION}'"
fi

if ! [[ "${NAMESPACE}" =~ ^zeebe-chaos.* ]]; then
  fail "Namespace must start with 'zeebe-chaos', got '${NAMESPACE}'"
fi

namespace_${ACTION} "${NAMESPACE}"
