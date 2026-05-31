# Scripts

This folder contains several scripts we wrote to test or debug things.

## Profile.sh

**Usage:**
Run executeProfiling.sh with a pod name, optional event type, and optional profiler options. It will download the async profiler package, run in your current namespace, copy necessary binaries to the pod, run the async profiler, and copy the resulting flamegraph back to your local disk.

**Syntax:**

```
./executeProfiling.sh <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]
```

**Event Types:**
- `cpu` - CPU profiling (default)
- `wall` - Wall clock time profiling (includes waiting/blocking time). Automatically uses `-t` flag to split by thread for better analysis.
- `alloc` - Memory allocation profiling

**Additional Options:**
You can pass additional flags to async-profiler as the third parameter. Common options include:
- `-t` - to profile threads separately
- `--title "My Title"` - Set a custom title for the flamegraph
- `--minwidth <percent>` - Omit frames smaller than specified percentage

See [async-profiler documentation](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md) for potential options.

Example with CPU profiling (default):

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2
...
Profiling for 100 seconds
Done
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-cpu-2025-07-11_19-02-52.html release-8-8-0-alpha6-zeebe-2-flamegraph-cpu-2025-07-11_19-02-52.html
tar: Removing leading `/' from member names

```

Example with wall clock profiling:

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 wall
...
Profiling for 100 seconds
Done
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-wall-2025-07-11_19-05-23.html release-8-8-0-alpha6-zeebe-2-flamegraph-wall-2025-07-11_19-05-23.html
```

Example with additional profiler options:

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 cpu "-t"
...
Profiling for 100 seconds with flamegraph format
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

## pin-camunda-image.sh

**Usage:**
Resolves the mutable `docker.io/camunda/camunda:SNAPSHOT` tag to an immutable digest so the
orchestration image can't drift when a pod is rescheduled mid-test (e.g. spot-VM preemption). It
prints a `--set orchestration.image.digest=sha256:...` Helm flag to stdout (empty when no pin is
needed); diagnostics go to stderr. `newLoadTest.sh` copies it into each namespace folder, where
`make install` runs it and splices the flag into `helm upgrade`. Needs `docker buildx imagetools`
(ships with Docker) or `crane`.

**Syntax:**

```
./pin-camunda-image.sh "<additional_platform_configuration>"
```

It is a no-op (prints nothing, exits 0) when an explicit Camunda image is already chosen — either
via the passed Helm args (`--set orchestration.image.tag=...` / `global.image.tag`, as CI does) or
by a non-`SNAPSHOT` `orchestration.image.tag` in the namespace's
`camunda-platform-values-defaults.yaml` (the "use a different snapshot" flow).

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

