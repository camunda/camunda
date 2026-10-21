#!/usr/bin/env bash
# Shared settings for the log archive manual test. Source this file.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
export DIST="${DIST:-$REPO_ROOT/dist/target/camunda-zeebe}"
export WORK="${WORK:-/tmp/log-archive-manual}"
export STORE="$WORK/store"   # filesystem backup store, one folder per scenario
export BROKERS=3
export PARTITIONS=${PARTITIONS:-2}
export REST_BASE=8080   # broker i serves REST on 8080+i
export MGMT_BASE=9600   # broker i serves the management API on 9600+i
export EP_PORT=${EP_PORT:-8090}   # the event plane server (scenario i-eventplane), 8080 is broker 0
export EP_DIR=${EP_DIR:-$HOME/Development/projects/event-plane}

# The store folder of a scenario. STORE_OVERRIDE=<dir> points a run at another folder, e.g. a copy
# of a store that a restore may write into.
store_of() { echo "${STORE_OVERRIDE:-$STORE/$1}"; }
# The H2 database of an h-* scenario (RDBMS secondary storage). H2_OVERRIDE=<path> points a run at a
# copy, so a restored cluster's exporter does not change the database the next restore reads.
h2_of() { echo "${H2_OVERRIDE:-$WORK/h2/$1}"; }
h2_url() { echo "jdbc:h2:file:$(h2_of "$1");AUTO_SERVER=TRUE;MODE=PostgreSQL"; }

# Scenario switches, set by cluster.sh from the scenario name
broker_env() {
  local i=$1 scenario=$2
  local env=(
    "CAMUNDA_CLUSTER_NODEID=$i"
    "CAMUNDA_CLUSTER_SIZE=$BROKERS"
    "CAMUNDA_CLUSTER_PARTITIONCOUNT=$PARTITIONS"
    "CAMUNDA_CLUSTER_REPLICATIONFACTOR=3"
    "CAMUNDA_CLUSTER_INITIALCONTACTPOINTS=localhost:26502,localhost:26512,localhost:26522"
    "CAMUNDA_CLUSTER_NETWORK_HOST=localhost"
    "CAMUNDA_CLUSTER_NETWORK_COMMANDAPI_PORT=$((26501 + 10 * i))"
    "CAMUNDA_CLUSTER_NETWORK_INTERNALAPI_PORT=$((26502 + 10 * i))"
    "CAMUNDA_API_GRPC_PORT=$((26500 + 10 * i))"
    "SERVER_PORT=$((REST_BASE + i))"
    "MANAGEMENT_SERVER_PORT=$((MGMT_BASE + i))"
    "CAMUNDA_DATA_PRIMARYSTORAGE_DIRECTORY=$WORK/broker-$i/data"
    "CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=none"
    "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true"
    "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED=false"
    "CAMUNDA_DATA_SNAPSHOTPERIOD=PT1M"
    "CAMUNDA_DATA_PRIMARYSTORAGE_LOGSTREAM_LOGSEGMENTSIZE=8MB"
    "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_STORE=FILESYSTEM"
    "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_FILESYSTEM_BASEPATH=$(store_of "$scenario")"
  )
  case "$scenario" in
    a-classic) ;;
    b-continuous|d-both|g-brownfield|h-rdbms|h-handover|h-compacted|j-merge)
      env+=("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_CONTINUOUS=true"
            "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_SCHEDULE=PT1M"
            "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_CHECKPOINTINTERVAL=PT15S"
            "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_RETENTION_WINDOW=P1D"
            "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_RETENTION_CLEANUPSCHEDULE=PT1H") ;;
  esac
  case "$scenario" in
    i-eventplane|j-merge)
      # announce every commit entry to the event plane (event-plane.sh start)
      env+=("ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_EVENTPLANE_URL=http://localhost:$EP_PORT"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_EVENTPLANE_USERNAME=demo"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_EVENTPLANE_PASSWORD=demo") ;;
  esac
  case "$scenario" in
    j-merge)
      # manual scenario J: merge often, release and roll up quickly
      # the backup schedule is the continuous block's PT1M, shortened to 30 seconds
      env+=("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_SCHEDULE=PT30S"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_MERGEMAXOBJECTS=50"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_CLEANUPINTERVAL=PT10S"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_CLEANUPGRACEPERIOD=PT30S"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_RETENTIONWINDOW=PT3M"
            "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_RELEASEINTERVAL=PT10S") ;;
  esac
  case "$scenario" in
    h-*)
      env+=("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE=rdbms"
            "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_URL=$(h2_url "$scenario")"
            "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_USERNAME=sa"
            "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_PASSWORD=") ;;
  esac
  case "$scenario" in
    h-compacted|j-merge)
      # small segments, so the log is compacted before the archive starts (j-merge: merges happen often)
      # (the segment size must exceed the max message size, 4 MB by default)
      env+=("CAMUNDA_DATA_PRIMARYSTORAGE_LOGSTREAM_LOGSEGMENTSIZE=1MB"
            "CAMUNDA_CLUSTER_NETWORK_MAXMESSAGESIZE=512KB") ;;
  esac
  case "$scenario" in
    c-archive|d-both|e-kill|f-outage|g-brownfield|h-handover|h-compacted|i-eventplane|j-merge)
      # g-brownfield and h-handover/h-compacted start without the log archive (the old cluster);
      # LOG_ARCHIVE=on enables it for the upgraded cluster, see cluster.sh upgrade.
      if [ "${scenario#g-}" = "$scenario" ] && [ "${scenario#h-}" = "$scenario" ] || [ "${LOG_ARCHIVE:-off}" = on ]; then
        env+=("ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_ENABLED=true"
              "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_CUTINTERVAL=PT1S"
              "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_SNAPSHOTUPLOADINTERVAL=PT1M"
              "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_CHUNKSIZE=50"
              "ZEEBE_BROKER_DATA_BACKUP_LOGARCHIVE_ROOTEVERY=20")
      fi ;;
  esac
  printf '%s\n' "${env[@]}"
}

