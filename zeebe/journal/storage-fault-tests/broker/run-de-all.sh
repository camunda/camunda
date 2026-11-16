#!/usr/bin/env bash
# Runs scenario D/E variants one container at a time; args: "fs:variant" ...
D=$(cd "$(dirname "$0")/.." && pwd)
cd "$D/broker"
for run in "$@"; do
  fs=${run%%:*}; variant=${run#*:}
  echo "===== $fs $variant ($(date -u +%H:%M:%S))"
  docker run --rm --privileged --memory 7g -v "$D:/work" lazyfs-camunda:8.8.41 bash /work/broker/scenario-de.sh "$fs" "$variant" > "de-$fs-$variant.out" 2>&1
  grep -vE 'SLF4J|Killed|export ZEEBE|exec /usr|^fi;|^\s+ZEEBE|JAVA_OPTS|^node [0-9] up|load while|leader snapshot|held segments' "de-$fs-$variant.out" | cut -c1-200
  log="logs-de-$fs-$variant"
  echo "   log gaps >5s (standby?): $(cat $log/node*-install.log 2>/dev/null | grep -oE '^\[2026-[0-9-]+ [0-9:.]+\]' | tr -d '[]' | sort | awk '{split($2,t,":"); s=t[1]*3600+t[2]*60+t[3]; if (p && s-p>5) printf "%.0fs@%s ", s-p, $2; p=s}')"
done
