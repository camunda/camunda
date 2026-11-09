# Actor scheduler benchmarks (spike)

Benchmarks used to compare actor scheduler variants for camunda/camunda#68721: `main`, the PR variants (#68725,
#68754), a virtual-thread-per-actor prototype (this branch) and a fair scheduler prototype
(branch `ls/simple-actor-scheduler-spike`).

## Synthetic harness

`src/SchedBench.java` (+ `src/BrokerSim.java`) uses only the public scheduler API and runs against any
`zeebe-scheduler` jar. Scenarios: `pingpong`, `fanout`, `ingress`, `timers`, `fairness`, `ioblock`, `broker`. It
reports throughput, latency percentiles, CPU of scheduler threads and the cgroup's CFS throttling.

Setup (ignored by git):

- `jars/<variant>.jar`: `./gradlew :zeebe-scheduler:jar` on the variant's branch, copied from
  `zeebe/scheduler/build/libs/`.
- `deps.cp`: the scheduler's runtime classpath (one colon-separated line).
- `classes/`: `javac -d classes -cp jars/main.jar:$(cat deps.cp) src/*.java`.

`run1.sh <variant>[@k=v,...] <rep> <cpuQuotaPercent> key=value...` runs one JVM in a systemd scope with a CPU quota,
pinned to CPUs 8-15. Set `JAVA` to a JDK 25+ `java` binary if the one on the `PATH` is older. `run_matrix.py` runs a configured matrix; `analyze.py` aggregates `results*.jsonl`.

## Real broker (`e2e/`)

`run_e2e.sh <reps> <variants> <loads>` starts a fresh Elasticsearch and broker per run (Docker, 3-CPU quota,
3 partitions, cpu/io threads 3/3 like the load-test pods), drives it with the repo's load tester and records
`measure.py` results in `results-e2e.jsonl`. Images are `camunda/camunda:SNAPSHOT` with only the scheduler jar
swapped (`Dockerfile.<variant>`, needs `e2e/<variant>.jar`).

Results files with a suffix (`-before-es-fix`, `-disk-degraded`, `-tmpfs-watermark`, `-contaminated-by-orphans`)
come from runs with known harness problems and are kept only for reference.
