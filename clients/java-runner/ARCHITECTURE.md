# LiveBpmn — Architecture & Implementation Notes

> **What this is:** the implementation walkthrough — what threads run where, how the runtime
> pipeline is shaped, and where the load-bearing pieces live. Pair with [DESIGN.md](DESIGN.md)
> (API rationale) and [README.md](README.md) (how to use it).

## Module map

```
clients/java-runner/src/main/java/io/camunda/runner/
├── LiveBpmn.java              ← public facade + fluent builder (single class)
├── Job.java + JobConsumer.java← public lambda-parameter contract
├── Cluster.java + ClusterFactory.java
├── Run.java + RunOptions.java
└── internal/
    ├── DefaultJob.java        ← Job impl wrapping ActivatedJob + JobClient
    ├── BoundHandler.java      ← sealed { OfFunction | OfConsumer }
    ├── JobHandlerAdapter.java ← user lambda → SDK JobHandler bridge
    ├── ModelRewriter.java     ← deep-clone + prefix
    ├── RunIdGenerator.java    ← <username>-<5-char-base36>
    ├── WorkerRegistration.java← decorates handlers, counts completions
    ├── RunnerPipeline.java    ← orchestrates the whole run
    ├── DefaultRun.java        ← Run handle, await loop, shutdown hook
    ├── LocalContainerCluster.java
    ├── LocalhostCluster.java
    └── UsingCluster.java
```

Public types live in `io.camunda.runner`. Everything in `io.camunda.runner.internal` is an
implementation detail and may change without notice.

## Runtime pipeline (what happens on `.run(...)`)

`RunnerPipeline.execute(model, bindings, opts, cluster)` runs synchronously on the calling
thread. Conceptually a chain of phases:

```
caller thread (main)
  │
  ▼
  validate user-task / unbound-task warnings
  │
  ▼
  RunIdGenerator.generate()                          → "stephan-r7f3a"
  │
  ▼
  ModelRewriter.rewrite(model, runId, bindings.keys) → cloned BpmnModelInstance
  │                                                    + Map<elementId, prefixedJobType>
  ▼
  Cluster.client()                                   → CamundaClient (lazy boot)
  │
  ▼
  client.newDeployResourceCommand().send().join()    → DeploymentEvent (blocks)
  │
  ▼
  WorkerRegistration.register(client, bindings, ..., runId)
  │     for each binding:
  │       client.newWorker()
  │             .jobType(prefixed)
  │             .name(runId+"-"+elementId)
  │             .handler(JobHandlerAdapter wrapping the lambda + AtomicLong counter)
  │             .open()
  │
  ▼
  LockSupport.parkNanos(150 ms)   ← give worker subscriptions time to attach
  │
  ▼
  for i in 0..N-1:
      client.newCreateInstanceCommand()
            .processDefinitionKey(...).variables(...).tags(...).send().join()
      if pacing != null: LockSupport.parkNanos(pacing)
  │
  ▼
  return DefaultRun                ← also installs JVM shutdown hook
```

The whole call returns roughly when the last `createInstance().send().join()` completes. Workers
are now activating jobs in their own pool; the user calls `run.await(timeout)` to block until
the instances reach a terminal state.

## Threading model

LiveBpmn doesn't manage its own thread pool. It rides on three sets of threads:

| Threads | Provided by | What runs there |
|---------|-------------|-----------------|
| **Caller thread** (typically `main`) | The user | The whole pipeline above. The fluent builder, `Bpmn.read*`, model rewrite, all SDK `.send().join()` calls on deploy/createInstance, and the `await()` poll loop. Single-threaded by construction. |
| **SDK job-poller / worker threads** | `CamundaClient` (Netty + an http executor) | One job-poller per registered worker plus a small worker thread pool. Activates jobs from the broker, calls `JobHandlerAdapter.handle(client, activatedJob)`. **The user's lambda runs on these threads.** |
| **JVM shutdown hook** | `Runtime.getRuntime().addShutdownHook(...)` | One thread per `Run`, runs `DefaultRun.closeQuietly()` on exit/Ctrl-C. Closes workers, releases the cluster (if owned). |
| **Testcontainer support** (only with `.testcontainer()`) | Testcontainers (docker-java + zerodep transport) | A handful of background threads watching the container, streaming logs through `Slf4jLogConsumer`. |

Concurrency-relevant code:

- `DefaultJob.resolved` is **`volatile`**. The SDK's `JobHandler` contract says `handle()` is
  invoked single-threaded for one activation, so user code calling `complete`/`fail` from the
  same thread is the normal path. `volatile` covers the rare case of a user spawning a thread
  inside their lambda and resolving from there. Double-resolve still throws
  `IllegalStateException`.
- `WorkerRegistration.handled` is a `Map<String, AtomicLong>` — keys are jobTypes, values are
  per-jobType handle counters. The map is built once before any worker opens, so reads from
  multiple worker threads don't race against the structure; only the `AtomicLong` cells mutate.
- `LocalContainerCluster.client()` is **`synchronized`** so concurrent first-callers don't both
  boot a container.
- `LiveBpmn` is **not thread-safe.** It's a single-shot builder used from one thread.
- `DefaultRun.await(...)` polls in a loop using `LockSupport.parkNanos(...)` (no `Thread.sleep`,
  per repo convention). Termination check uses `client.newProcessInstanceSearchRequest()` against
  the secondary store; expect ~1–2 s lag from the SDK to the broker's exporter to the search
  index. `await` accepts that lag and tolerates an instance briefly looking ACTIVE after it has
  actually completed.

## Model rewrite (`ModelRewriter`)

The user passes an `BpmnModelInstance` (built via `LiveBpmn.createExecutableProcess(...).done()`,
or adopted via `LiveBpmn.of(model)` / `fromFile` / `fromClasspath`). The pipeline must NOT mutate
that instance — the user may keep using it.

**Strategy:** XML round-trip clone.

```java
final byte[] xml = Bpmn.convertToString(original).getBytes(UTF_8);
final BpmnModelInstance clone = Bpmn.readModelFromStream(new ByteArrayInputStream(xml));
```

Then walk the cloned tree:

1. **Process id** — find the single `Process` element, read `id`, set
   `prefix + "-" + originalId`.
2. **Job types on bound elements** — for each `elementId` in `bindings.keySet()`, look up the
   `ServiceTask` in the clone, find its `ZeebeTaskDefinition` extension, set the `type`
   attribute to `prefix + "-" + elementId`.
3. **Element ids and BPMN structure** — left unchanged. (Element ids are what Operate displays;
   prefixing them would muddy the user-visible names.)

`ModelRewriter.Rewritten` is a small record returned from rewrite:

```java
record Rewritten(BpmnModelInstance model, String prefixedProcessId,
                 Map<String, String> jobTypesByElementId)
```

The job-type map is consumed downstream by `WorkerRegistration` (to subscribe with the prefixed
type) and by `DefaultRun.workersHandled()` (to translate prefixed-type counters back to clean
elementId keys for the user).

## Lambda dispatch (`JobHandlerAdapter`)

Each binding value is a `BoundHandler` (sealed: `OfFunction(Function<Job, Map>) |
OfConsumer(JobConsumer)`). At worker registration time, `WorkerRegistration` wraps each handler
in a `JobHandlerAdapter` instance that implements the SDK's `JobHandler`:

```java
public void handle(JobClient client, ActivatedJob activatedJob) throws Exception {
  final DefaultJob job = (DefaultJob) Job.of(client, activatedJob);
  if (functionForm != null) {
    final Map<String, Object> result = functionForm.apply(job);
    if (result == null || result.isEmpty()) {
      job.complete();
    } else {
      job.complete(result);
    }
  } else {
    consumerForm.accept(job);
    if (!job.isResolved()) {
      job.complete();
    }
  }
  // throw → SDK auto-fail path; nothing caught here.
}
```

Resolution rules (the user-visible contract):

- Function returning `null` or empty Map → adapter calls `complete()` with no variables.
- Function returning a non-empty Map → adapter calls `complete(map)`.
- Consumer that calls `job.complete*` or `job.fail*` → that result wins; the adapter detects
  resolution via `DefaultJob.isResolved()` (package-private hook) and skips auto-complete.
- Consumer that returns without resolving → adapter auto-completes with no variables.
- Any uncaught exception → propagates out of `handle()` → the SDK fails the job, decrements
  retries, creates an incident at zero.

`DefaultJob.complete()` / `fail()` make synchronous SDK calls (`...send().join()`) so failures
surface as exceptions rather than silently dropping into the dropped-future void.

## Per-run isolation

The per-run prefix `<username>-<5-char-base36>` (e.g. `stephan-r7f3a`) is the load-bearing
isolation primitive. Two simultaneously-run scripts on the same cluster don't collide because:

1. **Process id** is prefixed → deployments don't overlap.
2. **Job type** is prefixed → no other worker on the cluster (production or another dev) will
   activate jobs from this run.
3. **`processDefinitionKey` pinning** on `createInstance` → even if someone else racily deploys
   the same `bpmnProcessId` between this run's deploy and createInstance, this run targets
   exactly its own deployed version.

Tags are complementary, not load-bearing: `runId:<short>`, `user:<username>`,
`script:<callerSimpleName>` — auto-applied to every instance for searchability in Operate. They
don't influence routing.

## Cluster lifecycle

`Cluster` is an `AutoCloseable` spec, not a built client. Materialisation is lazy and happens
inside `RunnerPipeline.execute(...)` when it calls `cluster.client()` for the first time.

| Cluster impl | Owned by runner? | What `close()` does |
|--------------|------------------|---------------------|
| `LocalContainerCluster` | yes | `client.close()` then `container.stop()`. |
| `LocalhostCluster` | yes (the client; the broker is external) | `client.close()`. The localhost broker keeps running. |
| `UsingCluster` | no | no-op. The user owns the `CamundaClient`. |

`Cluster.ownsClient()` reflects this. `try-with-resources` on the cluster is the canonical
disposal path; the JVM shutdown hook installed by `DefaultRun` is the safety net for Ctrl-C.

## Run handle (`DefaultRun`)

Constructed by `RunnerPipeline.execute(...)` after instances are created. State:

- `runId`, `processId` (prefixed), `processDefinitionKey`, `startedAt`.
- `instances` — a `List<Long>` of created process-instance keys.
- `cluster`, `workers` — for `close()`.
- `restAddress` — for composing `operateUrl()`.
- `jobTypeToElementId` — for translating `workersHandled()` keys back to clean elementIds.

`await(timeout)`:

1. Loops, polling `client.newProcessInstanceSearchRequest()` with the recorded instance keys.
2. An instance counts as terminal in state COMPLETED or TERMINATED, or with an unresolved
   incident.
3. Sleeps `~500 ms` between polls via `LockSupport.parkNanos`.
4. Returns when all instances are terminal or the timeout elapses.

`close()`:

1. Closes each `JobWorker` (cancels SDK subscriptions cleanly so they don't briefly steal jobs
   after exit).
2. If `cluster.ownsClient()`, calls `cluster.close()`.
3. Removes the JVM shutdown hook (so a second close from the hook doesn't double-trigger).

The deployment in the cluster is *not* deleted on close — the user can keep inspecting it in
Operate.

## Logging

SLF4J everywhere. The pom ships `slf4j-simple` at compile scope so example `main()` runs in an
IDE see output without a Maven re-import. Library consumers with their own SLF4J binding still
win (slf4j picks one provider).

Notable log lines (INFO):
- `LocalContainerCluster` — container start, ready ports.
- `RunnerPipeline` — deployed processDefinitionKey, registered worker, created N instances,
  Operate URL.
- `WorkerRegistration` — handle counts on shutdown.

Container stdout/stderr is streamed through `Slf4jLogConsumer` under logger
`camunda-container` so the user can see what Camunda is doing inside the container during the
~30–60s startup window.

## Build & test

- Java 21, Maven, Spotless (Google Java Format).
- Unit tests: 52 of them, covering builder behaviour, model rewrite, Job contract, lambda
  resolution, RunOptions, `operateUrl` composition.
- One integration test (`LiveBpmnIT`) boots a real Testcontainer and runs the pipeline end-to-end.
  Skipped when Docker is not available so the unit-test run stays fast.

```bash
./mvnw verify -pl clients/java-runner -DskipTests=false -DskipITs -Dquickly   # fast (unit)
./mvnw verify -pl clients/java-runner -DskipITs=false                          # also IT
```

## Where extension would go

For the [VISION.md](VISION.md) items:

- **Agentic substrate** — `LiveBpmn.fromString(javaSource)` slots in next to `fromFile`/`fromClasspath`,
  delegating to the JDK's `javax.tools.JavaCompiler`. The compiled class then re-enters the same
  pipeline. No changes needed below `LiveBpmn`.
- **Property-based testing** — `RunOptions` already takes an `IntFunction<Map<String, Object>>`
  generator; layering invariants on top is a thin wrapper around `Run.await()` followed by
  `Run.instances()` inspection. Mostly additive on top of what's there.
- **Executable onboarding** — pure docs/IDE integration; no runtime change.

The lambda-as-handler primitive is the kernel; everything in VISION.md slides on top without
touching it.
