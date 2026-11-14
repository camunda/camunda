#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Makes the brokers of one namespace one half of a zone-aware cluster spanning two namespaces,
# "region-a" and "region-b". Invoked by common.mk at `make install`/`make template` time when
# dual_region=true, so the rest of the namespace folder stays the same as a single-namespace load
# test.
#
# It follows the documented Helm setup (one release per region, the same zone list everywhere,
# only the local zone differing) and writes two files:
#
#   camunda-platform-dual-region-values.yaml (Helm values, passed with -f)
#     What the platform chart supports natively: orchestration.partitioning with the zone-aware
#     scheme, from which the chart derives the cluster size, the replication factor, the per-zone
#     StatefulSet (camunda-<zone>) and the namespace-qualified advertised host; and the cluster
#     name, which the chart otherwise derives from the release (namespace) name and brokers only
#     join a cluster of their own name.
#
#   camunda-platform-dual-region-override.yaml (application config)
#     What the chart leaves to the user once a cluster spans more than one zone. It fills
#     orchestration.extraConfiguration[1] (application-override.yml) through `--set-file`, the
#     same slot scenario=max uses, so the scenario=max overrides are folded in here.
#       - the initial contact points: every broker of both zones, addressed through its zone's
#         headless service, which publishes not-ready addresses so they resolve during a cold start
#       - with elasticsearch, one Camunda exporter per region instead of the autoconfigured one.
#         Exporter positions are replicated per exporter id, so each id must write to the same
#         Elasticsearch on every broker: camundaregiona always writes to region A,
#         camundaregionb always to region B, whichever region leads the partition.
#
# Usage: generate-dual-region-values.sh <zone> <own-namespace> <peer-namespace> <cluster-name> \
#          <secondary-storage> <scenario>
#   zone            region-a or region-b: the zone of the brokers in <own-namespace>
#   own-namespace   namespace these files are generated for
#   peer-namespace  namespace of the other region
#   cluster-name    cluster name shared by both namespaces
#   secondary-storage  elasticsearch or none; with none there is nothing to export to
#   scenario        load test scenario; "max" adds its consistency check overrides
#
# Environment overrides: BROKERS_PER_REGION (3), PARTITION_COUNT (3), REPLICATION_FACTOR (4),
# REGION_A_PRIORITY (1000), REGION_B_PRIORITY (500), ORCHESTRATION_FULLNAME (camunda),
# INTERNAL_API_PORT (26502), ELASTICSEARCH_SERVICE (elasticsearch-es-http), ELASTICSEARCH_PORT (9200).
#
# Writes both files relative to CWD (the namespace folder).

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
# orchestration.fullnameOverride; the chart names the zone's StatefulSet and headless service
# <fullname>-<zone>
ORCHESTRATION_FULLNAME="${ORCHESTRATION_FULLNAME:-camunda}"
INTERNAL_API_PORT="${INTERNAL_API_PORT:-26502}"
# the Elasticsearch service created by ECK for the load-test-setup chart's `elasticsearch` resource
ELASTICSEARCH_SERVICE="${ELASTICSEARCH_SERVICE:-elasticsearch-es-http}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-9200}"

values_file="camunda-platform-dual-region-values.yaml"
override_file="camunda-platform-dual-region-override.yaml"

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

replicas_per_region=$((REPLICATION_FACTOR / 2))

cat > "$values_file" <<EOF
# dual region (generate-dual-region-values.sh): zone ${zone} of ${namespace_a} + ${namespace_b}.
global:
  # identical in both namespaces: brokers only join a cluster of their own name
  zeebeClusterName: ${cluster_name}
orchestration:
  partitionCount: "${PARTITION_COUNT}"
  # The chart derives both from the zone list and rejects values that contradict it, so they
  # restate the zone list totals over the single-region ones of camunda-platform-values-defaults.yaml.
  clusterSize: "$((2 * BROKERS_PER_REGION))"
  replicationFactor: "${REPLICATION_FACTOR}"
  partitioning:
    scheme: zone-aware
    zone: ${zone}
    zones:
      - name: region-a
        numberOfBrokers: ${BROKERS_PER_REGION}
        numberOfReplicas: ${replicas_per_region}
        priority: ${REGION_A_PRIORITY}
      - name: region-b
        numberOfBrokers: ${BROKERS_PER_REGION}
        numberOfReplicas: ${replicas_per_region}
        priority: ${REGION_B_PRIORITY}
EOF

region_exporter() {
  local name="$1" namespace="$2"
  cat <<EOF
      ${name}:
        class-name: io.camunda.exporter.CamundaExporter
        args:
          connect:
            type: elasticsearch
            url: http://${ELASTICSEARCH_SERVICE}.${namespace}.svc.cluster.local:${ELASTICSEARCH_PORT}
          createSchema: true
          # what the autoconfigured exporter would take from camunda-platform-values-defaults.yaml
          history:
            waitPeriodBeforeArchiving: 1m
            rolloverBatchSize: 500
            retention:
              enabled: true
              minimumAge: 1d
              policyName: camunda-retention-policy
EOF
}

{
  echo "# dual region (generate-dual-region-values.sh): zone ${zone} of ${namespace_a} + ${namespace_b}."
  if [[ "$scenario" == "max" ]]; then
    echo "# scenario=max: its consistency check overrides, which this file replaces"
    cat camunda-platform-override-values.yaml
  fi
  cat <<EOF
camunda:
  cluster:
    # every broker, as the chart lists them for a single zone
    initial-contact-points:
EOF
  for region in "region-a:${namespace_a}" "region-b:${namespace_b}"; do
    service="${ORCHESTRATION_FULLNAME}-${region%%:*}"
    for ((i = 0; i < BROKERS_PER_REGION; i++)); do
      echo "      - ${service}-${i}.${service}.${region#*:}.svc.cluster.local:${INTERNAL_API_PORT}"
    done
  done
  if [[ "$secondary_storage" == "elasticsearch" ]]; then
    cat <<EOF
  # every exporter id writes to the same Elasticsearch on every broker, in both regions, so a
  # leadership change between regions never resumes an exporter against another Elasticsearch
  data:
    secondary-storage:
      autoconfigure-camunda-exporter: false
    exporters:
EOF
    region_exporter camundaregiona "$namespace_a"
    region_exporter camundaregionb "$namespace_b"
  fi
} > "$override_file"
