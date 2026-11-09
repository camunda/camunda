#!/usr/bin/env bash
# Usage: start_load.sh <rate PI/s> <bpmn: typical|one_task> [workers] [workerCapacity]
# Starts N workers and one starter (the repo's load tester) on CPUs 0-7, like the load-test pods.
set -euo pipefail
rate=$1; model=$2; workers=${3:-6}; capacity=${4:-30}
JAR=${LOAD_TESTER_JAR:-$(git -C "$(dirname "$0")" rev-parse --show-toplevel)/load-tests/load-tester/build/libs/camunda-load-tester-8.11.0-SNAPSHOT.jar}
J=${JAVA:-java}
case $model in
  typical) bpmn=bpmn/typical_process.bpmn ;;
  one_task) bpmn=bpmn/one_task.bpmn ;;
  *) echo "unknown model $model" >&2; exit 1 ;;
esac
pkill -f "camunda-load-tester.*spring.profiles.active" 2>/dev/null || true
sleep 1
cd "$(dirname "$0")"
for i in $(seq 1 "$workers"); do
  SERVER_PORT=$((9710 + i)) LOAD_TESTER_WORKER_CAPACITY=$capacity \
    nohup taskset -c 0-7 $J -Xmx512m -jar $JAR --spring.profiles.active=worker > worker-$i.log 2>&1 &
done
SERVER_PORT=9702 LOAD_TESTER_STARTER_RATE=$rate LOAD_TESTER_STARTER_BPMN_XML_PATH=$bpmn \
  nohup taskset -c 0-7 $J -Xmx1g -jar $JAR --spring.profiles.active=starter > starter.log 2>&1 &
echo "load started: $rate PI/s, model $model, $workers workers x capacity $capacity"
