# LiveBpmn — One-File Process Runner (Design)

> Status: design locked. No implementation yet. See `AGENT_CONTEXT.md` for phasing.

## Goal

Let a developer write **one** Java file with a `main` method that:

1. Defines a BPMN process inline (same shape as today's `Bpmn.createExecutableProcess(...)`).
2. Plugs in workers as inline lambdas next to each service/user task.
3. Calls `.run(N)`.

…and have it deploy, register workers, fire N instances, and let them flow through Operate
live — with breakpoints firing in the IDE.

```java
public static void main(String[] args) {
  LiveBpmn.createExecutableProcess("order")
      .startEvent()
      .serviceTask("validate", job -> Map.of("valid", true))
      .serviceTask("ship",     job -> { /* breakpoint */ })
      .endEvent()
      .run(100)             // smart default cluster
      .await();
}
```

The novelty is **runtime composition** — process definition, worker logic, deployment,
instance lifetime, and observability all settle into one fluent expression. The BPMN
modelling syntax is unchanged; lambdas slot into existing builder method positions.

## Module

`clients/java-runner/` — new Maven module. Depends on `clients/java` (CamundaClient) and
`zeebe/bpmn-model`. Does not modify either. Designed for eventual extraction into a
standalone repo.

## Public API surface

### Single class: `LiveBpmn`

`LiveBpmn` is both the facade *and* the builder type — one class, one import, slim surface.
Static factory methods construct instances; instance methods continue the chain.

```java
public final class LiveBpmn {
  // --- static factories ---
  public static LiveBpmn createExecutableProcess(String id);
  public static LiveBpmn of(BpmnModelInstance existingModel);
  public static ClusterFactory cluster();

  // --- instance builder methods (mirror the bpmn-model builder shape) ---
  // see below
}
```

### Builder methods on `LiveBpmn`

Mirrors the shape of the BPMN model builder — same method names, same return-type chaining —
for the **subset** we hand-write. Everything else is reachable via `.raw()`.

```java
LiveBpmn
    .startEvent() / startEvent(id)
    .serviceTask(String id, Function<Job, Map<String, Object>> handler)   // return = vars
    .serviceTask(String id, Consumer<Job> handler)                        // auto-complete
    .serviceTask(String id, Consumer<ServiceTaskBuilder> configure)       // pass-through (existing shape)
    .userTask(...)                  // same overload pattern
    .exclusiveGateway() / parallelGateway() / eventBasedGateway()
    .sequenceFlowTo(String id) / .condition(String) / .name(String)
    .boundaryEvent(...)             // limited v1 surface
    .endEvent() / endEvent(id)
    .raw()                          // -> AbstractFlowNodeBuilder<?,?> for unmirrored methods

    .bind(String elementId, Function<Job, Map<String, Object>> handler)   // for .of(model)
    .bind(String elementId, Consumer<Job> handler)

    .done()                         // -> BpmnModelInstance (drop-in)
    .run(int n)                     // -> Run, smart default cluster
    .run(int n, Cluster cluster)    // -> Run
    .run(RunOptions opts)           // -> Run, smart default cluster
    .run(RunOptions opts, Cluster cluster)
    .on(Cluster cluster)            // -> LiveBpmn (configures, returns builder)
```

**Method overloads on `serviceTask` / `userTask`:** the existing
`Consumer<ServiceTaskBuilder>` form is preserved, and we add `Function<Job, Map>` and
`Consumer<Job>` overloads. Java picks the right one when the lambda parameter is explicitly
typed; ambiguous lambdas need a cast. Documented quirk.

### `Job`

```java
public interface Job {
  // mirrors ActivatedJob
  long getKey();
  String getType();
  long getProcessInstanceKey();
  String getElementId();
  int getRetries();
  Map<String, Object> variables();
  <T> T variable(String name, Class<T> type);
  String variablesAsJson();

  // control
  void complete();
  void complete(Map<String, Object> variables);
  void complete(String key, Object value);
  void fail(String reason);
  void fail(String reason, int retries);
}
```

**Resolution rules** (enforced by the runner wrapper):
- Throwing → SDK auto-fail path (decrement retries, incident at 0).
- `Function<Job,Map>` overload: returned non-null Map → `complete(map)`. Null/empty → `complete()`.
- `Consumer<Job>` overload: if `complete` or `fail` was called → that result wins. If neither
  was called → auto-`complete()`.
- Calling both `complete` and `fail` → `IllegalStateException` (bug in user code).

`BpmnError` (modelled business error via `newThrowErrorCommand`) is **out of scope for v1**.

### `Cluster` and `ClusterFactory`

```java
public interface Cluster extends AutoCloseable {
  CamundaClient client();   // materialise lazily, cache, runner-managed
  boolean ownsClient();     // true unless built via using(...)
  void close();
}

public final class ClusterFactory {
  public Cluster testcontainer();
  public Cluster testcontainer(Consumer<TestcontainerOptions> configure);
  public Cluster localhost();
  public Cluster localhost(int port);
  public Cluster properties(Properties props);   // wraps CamundaClientBuilder
  public Cluster using(CamundaClient client);    // bring-your-own; runner does NOT close
  public Cluster auto();                         // smart default
}
```

**`auto()` resolver:**
1. Probe `localhost:26500` for ~1 second.
2. If unreachable, boot a Testcontainer (log line: "no localhost cluster, starting Docker
   container…", followed by version + start time).
3. If both fail, throw with an actionable message ("install Docker, run `c8run start`, or
   pass an explicit cluster").

Lifecycle: cluster materialises at `run()` time. Runner closes the client (and tears down
containers) on JVM shutdown for clusters it built. `using(client)` clusters are the user's
responsibility. Multi-run reuse works: same `Cluster` instance can be passed to multiple
`run()` calls.

### `RunOptions`

```java
public final class RunOptions {
  public static RunOptions of(int instances);
  public RunOptions pacing(Duration between);          // default eager
  public RunOptions timeout(Duration max);             // default no timeout
  public RunOptions tags(String... extraTags);         // in addition to auto-tags
  public RunOptions variables(Map<String, Object> vars);
  public RunOptions variables(IntFunction<Map<String, Object>> generator);  // per-instance
}
```

### `Run`

```java
public interface Run extends AutoCloseable {
  String runId();
  String processId();
  long processDefinitionKey();
  Instant startedAt();

  List<InstanceView> instances();    // SDK search; ~seconds lag
  List<WorkerView> workers();        // local, real-time
  Progress progress();

  void await();
  void await(Duration timeout);

  void close();   // closes workers; deployment stays
}

public record InstanceView(long key, State state, String currentElement,
                           Map<String, Object> variables, Instant createdAt, Instant completedAt) {
  public enum State { ACTIVE, COMPLETED, INCIDENT, TERMINATED }
}

public record WorkerView(String jobType, String elementId,
                         long jobsHandled, long jobsFailed, long jobsInFlight,
                         Duration avgHandleTime, Duration p95HandleTime) {}

public record Progress(int total, int active, int completed, int withIncident) {}
```

`await()` returns when all created instances are in a terminal state (COMPLETED, TERMINATED,
or INCIDENT) — incidents count as terminal so a stuck instance does not hang `await` forever;
they surface in `progress()` and `instances()`.

## Internal pipeline

What `run(...)` does, in order:

1. **Generate a `runId`:** `<username>-<5-char-base36>`.
2. **Clone** the `BpmnModelInstance` so we never mutate what the user passed in.
3. **Rewrite the clone:** prefix the `processId` (`order` → `stephan-r7f3a-order`); for every
   bound element, prefix the `zeebeJobType` (`validate` → `stephan-r7f3a-validate`).
   `elementId`s stay clean.
4. **Validate:** every bound elementId exists in the model; every service task with a bound
   handler has a `zeebeJobType`. Fail loudly with a clear message otherwise.
5. **Materialise** the `Cluster` (boot container if needed; build `CamundaClient`).
6. **Deploy** the rewritten model. Capture `processDefinitionKey`.
7. **Register workers** via `client.newWorker().jobType(prefixed).handler(decoratedHandler)`.
   The decorator wraps the user's lambda to: track stats, apply the resolution rules,
   translate `Job` ↔ `ActivatedJob` and `JobClient`.
8. **Wait briefly** (~100ms) for worker subscriptions to be active, so newly-created
   instances find their workers immediately.
9. **Create N instances**, pinned to `processDefinitionKey`, with auto-tags
   (`runId:<short>`, `user:<username>`, `script:<callerSimpleClassName>`) plus any
   `RunOptions.tags`. Apply pacing if set.
10. **Return** the `Run` handle. Register a JVM shutdown hook → `run.close()`.

Step 3 — model rewrite — is the most subtle bit. Use the BPMN model API to walk the
`BpmnModelInstance` and update the relevant attributes; do not regenerate XML by hand.

## Determinism / isolation guarantees

The prefix on `processId` + `jobType` is the load-bearing isolation primitive:

- **No other worker on the cluster can ever activate jobs from this run** (job type uniqueness).
- **`createInstance` targets exactly this run's process definition** (pinning by
  `processDefinitionKey`, not just `bpmnProcessId`).
- **Multiple devs running concurrently don't collide** (random suffix in the prefix).

Tags are *complementary* — they make instances findable in Operate, but they don't influence
job routing and thus can't replace prefixing for isolation.

## Examples

`OrderDemo.java` (lives under `clients/java-runner/src/main/java/io/camunda/runner/examples/`):

```java
public final class OrderDemo {
  public static void main(String[] args) {
    LiveBpmn.createExecutableProcess("order")
        .startEvent()
        .serviceTask("validate", job -> Map.of("valid", true))
        .serviceTask("charge",   job -> {
          var orderId = job.variable("orderId", String.class);
          // breakpoint here — see it fire 100×
        })
        .serviceTask("ship",     job -> Map.of("trackingId", "T-" + job.getProcessInstanceKey()))
        .endEvent()
        .run(100)
        .await();
  }
}
```

## Codebase context (existing patterns we lean on / mirror)

There are ~2,866 `Bpmn.createExecutableProcess` call sites in the monorepo. We do **not**
migrate any. Representative shapes we mirror:

- **Most common (test code):** `Bpmn.createExecutableProcess("p").startEvent().serviceTask("t", b -> b.zeebeJobType("t")).endEvent().done();`
- **Engine tests:** `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/activity/ServiceTaskTest.java` — canonical service-task shapes.
- **Client tests:** `clients/java/src/test/java/io/camunda/client/process/DeployProcessTest.java` — deploy-then-instance flows.
- **CPT (camunda-process-test):** `testing/camunda-process-test-java/src/test/java/io/camunda/process/test/api/TestCasesIT.java` — fluent process testing patterns; the closest prior art philosophically, though our runtime model differs.

These are good test fixtures and migration probes — picking any of them and asking "could
this be expressed as a `LiveBpmn` chain?" is a useful pressure test.

## Out of scope for v1

See `.claude/agent-memory/orchestrator/livebpmn_out_of_scope.md` for the deferral list with
reasoning. Highlights: BPMN errors, multi-tenancy, dashboard TUI, multi-instance fan-out
sugar, async/external completion, auto-mirroring the full bpmn-model builder hierarchy.
