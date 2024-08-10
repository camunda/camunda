#!/bin/bash
# Isolates a single node from the rest of the cluster
set -euf -o pipefail

function printUsageAndExit() {
  echo "./partition.sh [--heal] <targetPodName>"
  exit 1
}

# FIXME: use getopt or getopts or whatever you want
function parseArgs() {
  if [ $# -eq 0 ]; then
    echo "No arguments given"
    printUsageAndExit
  fi

  if [ "$1" = "--heal" ]; then
    if [ $# -eq 2 ]; then
      targetPod="$2"
      verb="del"
    else
      echo "No target pod name given"
      printUsageAndExit
    fi
  elif [ -z "$1" ]; then
    echo "No target pod name given"
    printUsageAndExit
  else
    targetPod="$1"
  fi
}

# $1 => target pod name
function getPodIp() {
  local pod="$1"
  kubectl get pod "$pod" --template={{.status.podIP}}
}

# $1 => pod name on which to execute the command
# $2 => pod name of the target route
function updateRoute() {
  local pod="$1"
  local target="$2"

  echo "On [$pod] - $verb unreachable route $target.zeebe.default.svc.cluster.local"
  kubectl exec "$pod" -- ip route "$verb" unreachable "$(getPodIp $target)"
}

# Verb determines if the route is added or deleted
# Values are either `add` or `delete`
verb="add"

# targetPod is the pod we want to isolate
targetPod="zeebe-0"
targetPodIp=$(kubectl get pod "$targetPod" --template={{.status.podIP}})

parseArgs "$@"

# TODO: how safe is it to ignore failures? ¯\_(ツ)_/¯
kubectl get pods -l app=zeebe | grep '^zeebe' | cut -d ' ' -f 1 | while read pod; do
  podIp=$(kubectl get pod "$pod" --template={{.status.podIP}})
  if [ "$pod" != "$targetPod" ]; then
    updateRoute "$pod" "$targetPod" || true
    updateRoute "$targetPod" "$pod" || true
  fi
done
