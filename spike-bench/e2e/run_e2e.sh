#!/usr/bin/env bash
# End-to-end runs against a real broker: fresh Elasticsearch + broker per run, load tester, measure.
# Usage: run_e2e.sh <reps> <variant,...> <load,...>   loads: typical50 | max300 | max<rate>
set -uo pipefail
cd "$(dirname "$0")"
reps=$1; IFS=',' read -ra variants <<< "$2"; IFS=',' read -ra loads <<< "$3"
WARMUP=${WARMUP:-150}; MEASURE=${MEASURE:-120}

fresh_es() {
  # the previous broker must be gone first: otherwise its exporter writes into the new
  # Elasticsearch before the next broker creates the schema (dynamic mappings, failed starts)
  docker rm -f -v bench-broker >/dev/null 2>&1
  docker rm -f -v bench-es >/dev/null 2>&1
  docker run -d --name bench-es --network bench-net --cpuset-cpus=0-5 -m 6g \
    --mount type=tmpfs,destination=/usr/share/elasticsearch/data,tmpfs-mode=1777,tmpfs-size=4g \
    -e discovery.type=single-node -e xpack.security.enabled=false -e ES_JAVA_OPTS="-Xms1g -Xmx1g" \
    -p 127.0.0.1:9200:9200 docker.elastic.co/elasticsearch/elasticsearch:9.5.5 >/dev/null
  timeout 180 bash -c 'until curl -sf localhost:9200/_cluster/health >/dev/null; do sleep 2; done'
}

for rep in $(seq 1 "$reps"); do
  for load in "${loads[@]}"; do
    # alternate variant order between reps
    order=("${variants[@]}"); if (( rep % 2 == 0 )); then order=($(printf '%s\n' "${variants[@]}" | tac)); fi
    for v in "${order[@]}"; do
      echo "$(date +%T) rep=$rep load=$load variant=$v"
      pkill -f "camunda-load-tester.*spring.profiles.active" 2>/dev/null; sleep 1
      fresh_es
      ./start_broker.sh "$v" false || { echo "broker failed"; docker logs bench-broker > "broker-failed-$rep-$load-$v.log" 2>&1; continue; }
      case $load in
        typical50) ./start_load.sh 50 typical 8 30 ;;
        max*) ./start_load.sh "${load#max}" one_task 6 30 ;;
      esac
      sleep "$WARMUP"
      python3 measure.py "$MEASURE" "e2e rep=$rep load=$load variant=$v" > /dev/null
      tail -1 results-e2e.jsonl | python3 -c "import json,sys; d=json.load(sys.stdin); print('   ', {k:d[k] for k in ('cores','throttled_pct','pi_created_per_s','jobs_completed_per_s','create_pi_p99_ms','complete_job_p99_ms')})"
    done
  done
done
pkill -f "camunda-load-tester.*spring.profiles.active" 2>/dev/null
docker rm -f -v bench-broker bench-es >/dev/null 2>&1
echo done
