#!/usr/bin/env bash
# Usage: run1.sh <variant> <rep> <cpuQuotaPercent> key=value...   -> prints the RESULT json line
# variant: <jar>[@key=value,...] with keys spins, spinners, streak, budget (simplified scheduler)
#          vthread-spin<N> (virtual-thread prototype with spin)
set -euo pipefail
cd "$(dirname "$0")"
variant=$1; rep=$2; quota=$3; shift 3
jar=${variant%%@*}; extra=""
case $variant in
  vthread-spin*) jar=vthread; extra="-Dzeebe.actor.virtualSpinNanos=${variant#vthread-spin}" ;;
esac
if [[ $variant == *@* ]]; then
  IFS=',' read -ra kvs <<< "${variant#*@}"
  for kv in "${kvs[@]}"; do
    k=${kv%%=*}; v=${kv#*=}
    case $k in
      spins) extra="$extra -Dzeebe.actor.idleSpins=$v" ;;
      spinners) extra="$extra -Dzeebe.actor.maxSpinners=$v" ;;
      streak) extra="$extra -Dzeebe.actor.runNextStreak=$v" ;;
      budget) extra="$extra -Dzeebe.actor.jobBudget=$v" ;;
      threads) extra="$extra -Dzeebe.actor.simpleThreads=$v" ;;
      *) echo "unknown variant key $k" >&2; exit 1 ;;
    esac
  done
fi
J=${JAVA:-java}
exec systemd-run --user --scope -q -p CPUQuota=${quota}% -- \
  taskset -c "${BENCH_CPUS:-8-15}" \
  $J -Xms2g -Xmx2g --enable-native-access=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED \
  -Dbench.variant=$variant -Dbench.rep=$rep $extra \
  -cp classes:jars/$jar.jar:$(cat deps.cp) SchedBench "$@" 2>&1 | grep '^RESULT ' | sed 's/^RESULT //'
