#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Generates camunda-platform-dual-region-override.yaml: the application-override.yml content that
# makes the brokers of one namespace one half of a zone-aware cluster spanning two namespaces,
# "region-a" and "region-b". Invoked by common.mk at `make install`/`make template` time when
# dual_region=true, so the rest of the namespace folder stays the same as a single-namespace load
# test.
#
# The file replaces orchestration.extraConfiguration[1] (application-override.yml, loaded after
# application.yml) through `--set-file`, the same way scenario=max replaces it. Because only one
# file can fill that slot, the consistency check overrides of scenario=max are folded in here.
#
# What it configures, and why:
#   - zone-aware partitioning with one zone per namespace, so every partition has replicas in both
#   - the cluster size, replication factor and initial contact points spanning both namespaces,
#     addressed by pod FQDN since the chart only knows about the pods of its own namespace
#   - the cluster name, which the chart derives from the release (namespace) name; brokers only
#     join a cluster of their own name
#   - the advertised host as a FQDN, because a short pod name does not resolve from the other
#     namespace
#   - with a secondary storage, a second Camunda exporter writing to the Elasticsearch of the other
#     namespace, so each region's records end up in both Elasticsearch clusters
#
# Usage: generate-dual-region-values.sh <zone> <own-namespace> <peer-namespace> <cluster-name> \
#          <secondary-storage> <scenario>
#   zone            region-a or region-b: the zone of the brokers in <own-namespace>
#   own-namespace   namespace this file is generated for
#   peer-namespace  namespace of the other region
#   cluster-name    cluster name shared by both namespaces
#   secondary-storage  elasticsearch or none; with none there is nothing to export to
#   scenario        load test scenario; "max" adds its consistency check overrides
#
# Environment overrides: BROKERS_PER_REGION (3), PARTITION_COUNT (3), REPLICATION_FACTOR (4),
# REGION_A_PRIORITY (1000), REGION_B_PRIORITY (500), STATEFULSET (camunda), SERVICE (camunda),
# INTERNAL_API_PORT (26502), ELASTICSEARCH_SERVICE (elasticsearch-es-http), ELASTICSEARCH_PORT (9200).
#
# Writes ./camunda-platform-dual-region-override.yaml, relative to CWD (the namespace folder).

set -euo pipefail

zone="$1"
own_namespace="$2"
peer_namespace="$3"
cluster_name="$4"
secondary_storage="$5"
scenario="$6"

BROKERS_PER_REGION="${BROKERS_PER_REGION:-3}"
PARTITION_COUNT="${PARTITION_COUNT:-3}"
# replicas of each partition, spread evenly over the two regions
REPLICATION_FACTOR="${REPLICATION_FACTOR:-4}"
REGION_A_PRIORITY="${REGION_A_PRIORITY:-1000}"
REGION_B_PRIORITY="${REGION_B_PRIORITY:-500}"
# the StatefulSet and the headless service its pods hang off, both named by the chart's fullnameOverride
STATEFULSET="${STATEFULSET:-camunda}"
SERVICE="${SERVICE:-camunda}"
INTERNAL_API_PORT="${INTERNAL_API_PORT:-26502}"
# the Elasticsearch service created by ECK for the load-test-setup chart's `elasticsearch` resource
ELASTICSEARCH_SERVICE="${ELASTICSEARCH_SERVICE:-elasticsearch-es-http}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-9200}"

output_file="camunda-platform-dual-region-override.yaml"

case "$zone" in
  region-a) namespace_a="$own_namespace"; namespace_b="$peer_namespace" ;;
  region-b) namespace_a="$peer_namespace"; namespace_b="$own_namespace" ;;
  *)
    echo "Error: zone must be region-a or region-b, got '$zone'" >&2
    exit 1
    ;;
esac

if [[ "$secondary_storage" != "elasticsearch" && "$secondary_storage" != "none" ]]; then
  echo "Error: secondary-storage must be elasticsearch or none, got '$secondary_storage'" >&2
  exit 1
fi

if [[ "$own_namespace" == "$peer_namespace" ]]; then
  echo "Error: the two regions must be in different namespaces, got '$own_namespace' twice" >&2
  exit 1
fi

for number in "$BROKERS_PER_REGION" "$PARTITION_COUNT" "$REPLICATION_FACTOR"; do
  if [[ ! "$number" =~ ^[1-9][0-9]*$ ]]; then
    echo "Error: expected a positive integer, got '$number'" >&2
    exit 1
  fi
done

if (( REPLICATION_FACTOR % 2 != 0 )); then
  echo "Error: REPLICATION_FACTOR must be even to spread the replicas evenly over the two regions, got $REPLICATION_FACTOR" >&2
  exit 1
fi

if (( REPLICATION_FACTOR > 2 * BROKERS_PER_REGION )); then
  echo "Error: REPLICATION_FACTOR $REPLICATION_FACTOR exceeds the ${BROKERS_PER_REGION}x2 brokers" >&2
  exit 1
fi

contact_points() {
  local namespace ordinal
  for namespace in "$namespace_a" "$namespace_b"; do
    for ((ordinal = 0; ordinal < BROKERS_PER_REGION; ordinal++)); do
      printf '    - %s-%d.%s.%s.svc.cluster.local:%s\n' \
        "$STATEFULSET" "$ordinal" "$SERVICE" "$namespace" "$INTERNAL_API_PORT"
    done
  done
}

{
  echo "# dual region (generate-dual-region-values.sh): zone ${zone} of ${namespace_a} + ${namespace_b}."
  if [[ "$scenario" == "max" ]]; then
    echo "# scenario=max: its consistency check overrides, which this file replaces"
    cat camunda-platform-override-values.yaml
  fi
  cat <<EOF
camunda.cluster:
  # the zone of the brokers in this namespace; their node ids are the pod ordinals
  zone: ${zone}
  # all brokers of both regions
  size: $((2 * BROKERS_PER_REGION))
  partition-count: ${PARTITION_COUNT}
  replication-factor: ${REPLICATION_FACTOR}
  partitioning:
    scheme: ZONE_AWARE
    zone-aware:
      zones:
        - {name: region-a, number-of-brokers: ${BROKERS_PER_REGION}, number-of-replicas: $((REPLICATION_FACTOR / 2)), priority: ${REGION_A_PRIORITY}}
        - {name: region-b, number-of-brokers: ${BROKERS_PER_REGION}, number-of-replicas: $((REPLICATION_FACTOR / 2)), priority: ${REGION_B_PRIORITY}}
  initial-contact-points:
$(contact_points)
zeebe.broker.cluster.clusterName: ${cluster_name}
zeebe.broker.network.advertisedHost: "\${K8S_NAME}.\${K8S_SERVICE_NAME}.\${K8S_NAMESPACE}.svc.cluster.local"
EOF
  if [[ "$secondary_storage" == "elasticsearch" ]]; then
    cat <<EOF
# the exporter autoconfigured from camunda.data.secondary-storage writes to the Elasticsearch of
# this namespace; this one writes the same records to the one of ${peer_namespace}
camunda.data.exporters:
  camundaExporterPeer:
    className: io.camunda.exporter.CamundaExporter
    args:
      connect:
        type: elasticsearch
        url: http://${ELASTICSEARCH_SERVICE}.${peer_namespace}.svc.cluster.local:${ELASTICSEARCH_PORT}
      createSchema: true
EOF
  fi
} > "$output_file"
