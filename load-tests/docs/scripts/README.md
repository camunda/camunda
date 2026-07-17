# Scripts

This folder contains several scripts we wrote to test or debug things.

## Profile.sh

**Usage:**
Run executeProfiling.sh with a pod name, an optional comma-separated list of events, and optional profiler options. It will download the async-profiler package, run in your current namespace, copy `asprof`/`jfrconv`/`libasyncProfiler.so` to the pod, capture all requested events in a single async-profiler attach into one combined JFR recording, convert that recording into one HTML flamegraph per event with `jfrconv` (still on the pod, which already has a JRE), and copy the resulting flamegraphs back to your local disk.

**Syntax:**

```
./executeProfiling.sh <POD-NAME> [EVENTS] [ADDITIONAL-OPTIONS]
```

**Events (default: `cpu,wall,alloc`):**
- `cpu` - CPU profiling
- `wall` - Wall clock time profiling (includes waiting/blocking time). Its flamegraph is converted with `-t` to split by thread for better analysis.
- `alloc` - Memory allocation profiling

All requested events are captured together in one async-profiler session (async-profiler only supports one active profiling session per JVM, but a single session can sample multiple event types at once).

**Additional Options:**
You can pass additional flags to async-profiler as the third parameter. Common options include:
- `-t` - to profile threads separately
- `--title "My Title"` - Set a custom title for the flamegraph
- `--minwidth <percent>` - Omit frames smaller than specified percentage

See [async-profiler documentation](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md) for potential options.

In the examples below, only the `$ ./executeProfiling.sh ...` line is something you run. Everything else, including the `+`-prefixed `kubectl cp` calls, is the script's own `bash -x` trace of what it does automatically (see the copy-results loop near the end of `executeProfiling.sh`) — you don't need to copy anything yourself.

Example with the default events (cpu, wall, alloc captured together, converted into 3 flamegraphs):

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2
...
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-cpu-20260710.html release-8-8-0-alpha6-zeebe-2-flamegraph-cpu-20260710.html
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-wall-20260710.html release-8-8-0-alpha6-zeebe-2-flamegraph-wall-20260710.html
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-alloc-20260710.html release-8-8-0-alpha6-zeebe-2-flamegraph-alloc-20260710.html
tar: Removing leading `/' from member names

```

Example with a single event only:

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 cpu
...
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-cpu-20260710.html release-8-8-0-alpha6-zeebe-2-flamegraph-cpu-20260710.html
```

Example with additional profiler options (applied to the whole capture session):

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 cpu "-t"
...
Profiling for 100 seconds
Done
```

**GitHub Actions Workflow:**
You can also use the [Profile Load Test workflow](https://github.com/camunda/camunda/actions/workflows/profile-load-test.yml) to profile pods running in load tests. This workflow allows you to select the load test name, pod name, event type (cpu/wall/alloc), and optional profiler options through the GitHub UI.

## loadTestMetrics.sh

**Usage:**
Runs every PromQL query defined in `queries.yaml` against a Prometheus HTTP endpoint and emits a `{name: value, ...}` JSON object on stdout. Failed/empty queries are omitted. Used by the [Camunda Load Test Metrics workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test-metrics.yaml) and runnable locally against any reachable Prometheus.

**Syntax:**

```
./loadTestMetrics.sh <namespace> [duration_seconds] [endpoint] [extra_curl_opts]
```

**Arguments:**
- `namespace` — exact namespace label, e.g. `c8-pgoyal-quicker-pr-1234`. Required.
- `duration_seconds` — PromQL range-vector window. Default: `600`.
- `endpoint` — Prometheus base URL. Default: `http://localhost:9090` (assumes `kubectl port-forward` is open).
- `extra_curl_opts` — free-form curl options string, e.g. `--user "u:p"` for HTTP basic auth.

**Examples:**

Local dev (port-forward already open):

```
./loadTestMetrics.sh c8-pgoyal-quicker-pr-1234
```

Against the LDAP-protected ingress:

```
./loadTestMetrics.sh \
  c8-medic-daily-2026-05-08-abc1234-test 600 \
  https://ci-monitor.benchmark.camunda.cloud \
  "--user $PROM_USER:$PROM_PASS" > /tmp/results.json
```

zsh users: quote regex-looking arguments to avoid `no matches found` glob errors.

## PartitionDistribution.sh

**Usage:**

```bash
./partitionDistribution.sh {nodes} {partitionCount} {replicationFactor}
```

This script will calculate the distribution of partitions in a cluster.

_Example Output:_

```bash
$ ./partitionDistribution.sh 3 3 3
Distribution:
P\N|	N 0|	N 1|	N 2
P 0|	L  |	F  |	F
P 1|	F  |	L  |	F
P 2|	F  |	F  |	L

Partitions per Node:
N 0: 3
N 1: 3
N 2: 3
```

