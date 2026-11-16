#!/usr/bin/env bash
# Builds the Docker images used by the tests in this directory. Run from anywhere.
#   lazyfs-zeebe:0.3.1-statfs   LazyFS 0.3.1 (Jepsen's fault-injection FUSE filesystem) + JDK 21
#   lazyfs-camunda:8.8.41       the above + the Camunda 8.8.41 distribution (journal jars, broker)
#   lazyfs-camunda:8.8.41-quota the above with the LazyFS "quota" patch (quota/quota_patch.py)
#   juicefs-camunda:8.8.41      the above + JuiceFS (optional, for juicefs/)
#   nfs-camunda:8.8.41          the above + NFS server/client (optional, for nfs/)
set -euo pipefail
cd "$(dirname "$0")"
docker build -t lazyfs-zeebe:0.3.1-statfs .
docker build -t lazyfs-camunda:8.8.41 broker
docker build -t lazyfs-camunda:8.8.41-quota quota
if [ "${1:-}" = all ]; then
  docker build -t juicefs-camunda:8.8.41 juicefs
  docker build -t nfs-camunda:8.8.41 nfs
fi
