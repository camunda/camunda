# LiveBpmn — One-File Process Runner

Drop a Java file with a `main` method, define a BPMN process inline with lambda workers, hit run.
The runner deploys it to a Camunda 8 cluster, registers the lambdas as workers, fires N instances,
and lets you watch them flow through Operate (and your IDE breakpoints) live.

```java
public static void main(final String[] args) throws Exception {
  try (var cluster = LiveBpmn.cluster().localhost()) {        // or .testcontainer()
    LiveBpmn.createExecutableProcess("order")
        .startEvent()
        .serviceTask("validate", (Job job) -> Map.of("valid", true))
        .serviceTask("ship", (Job job) -> {
          // breakpoint here — fires N times
          return Map.of("trackingId", "T-" + job.getProcessInstanceKey());
        })
        .endEvent()
        .run(5, cluster)
        .await(Duration.ofMinutes(2));
  }
}
```

See [DESIGN.md](DESIGN.md) for the API and architecture, [VISION.md](VISION.md) for where this
could go.

## What you can do with it today

- **Iterate on a real process at the speed of a `main()`.** Define BPMN and worker logic in one
  file, hit run, watch instances flow in Operate. No `mvn deploy`, no docker-compose, no separate
  worker app to keep in sync.
- **Adopt existing `.bpmn` files.** Hand a Modeler-exported file to `LiveBpmn.fromFile(...)` and
  hook lambdas to its element ids — designer drew it, you run it.
- **Set IDE breakpoints inside service-task lambdas.** They fire N times. Step through real
  process execution without a remote debugger or attached worker app.
- **Reproduce a flow from a snippet.** Paste a `main()` into a bug ticket; reviewers run it
  unchanged against their own local cluster. Bug repros become executable.
- **Stress-shape your process.** `RunOptions.of(N).pacing(...)` fires N instances at a chosen
  rate so you can see throughput, gateway splits, and bottlenecks in Operate.

## Cluster modes

`LiveBpmn.cluster()` returns a small factory:

| Mode | When to use | Speed |
|------|-------------|-------|
| `.localhost()` / `.localhost(port)` | You already have Camunda running on `localhost:26500`. **Fast iteration.** | Boot ~once, runs are instant. |
| `.testcontainer()` | Zero-config — boot Camunda in Docker from your JVM. Demos, fresh-machine onboarding. | First run pulls image (~minutes); subsequent boots ~30–60 s. |
| `.using(camundaClient)` | Bring-your-own SDK client (SaaS, custom auth, etc.). | Whatever your client targets. |

## Recommended: run Camunda locally with `c8run` (faster iteration)

`c8run` is the official local distribution — packaged Java + secondary storage, single command,
no Docker required. **Best for tight inner-loop work**: start it once, leave it running, re-run
your `main()` against it as many times as you like in milliseconds.

### macOS — one-command from this repo

The `c8run/` source is part of this monorepo. With Go ≥ 1.25 installed (`brew install go` if you
don't have it):

```bash
cd c8run && go build -o c8run ./cmd/c8run/ && ./c8run start
```

That builds the launcher and starts the full stack. Stop with `cd c8run && ./c8run stop`.

If your `c8run/.env` requires LDAP credentials for the Java artifact pull, see
[`c8run/README.md`](../../c8run/README.md) — add `JAVA_ARTIFACTS_USER` / `JAVA_ARTIFACTS_PASSWORD`
once and you're set.

### macOS — without Go

If you don't want Go on your machine, use the Docker Compose option below instead. It's the same
setup minus the c8run launcher.

### Verify it's up

```bash
curl http://localhost:8080/v2/topology   # → 200 OK with broker info once ready
```

Operate is then at <http://localhost:8080/operate>.

Stop it with `./c8run stop`.

### Wire your demo to it

Just swap `.testcontainer()` for `.localhost()`:

```java
try (var cluster = LiveBpmn.cluster().localhost()) {
  LiveBpmn.createExecutableProcess("order")
      // …
      .run(5, cluster)
      .await(Duration.ofMinutes(1));
}
```

The runner connects via gRPC `localhost:26500` (override via `.localhost(int port)` if needed).

## Alternative: Docker Compose

If you'd rather have everything in containers but skip Testcontainers' per-run boot:

```yaml
# docker-compose.yml
services:
  camunda:
    image: camunda/camunda:8.8.0
    ports:
      - "26500:26500"   # gRPC
      - "8080:8080"     # REST + Operate
    environment:
      SPRING_PROFILES_ACTIVE: broker,consolidated-auth,security
      CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API: "true"
      CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED: "false"
      CAMUNDA_DATABASE_TYPE: rdbms
      CAMUNDA_DATA_SECONDARYSTORAGE_TYPE: rdbms
      CAMUNDA_DATABASE_URL: "jdbc:h2:mem:c8;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
      CAMUNDA_DATABASE_USERNAME: sa
      CAMUNDA_DATABASE_PASSWORD: ""
      ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME: io.camunda.exporter.rdbms.RdbmsExporter
      ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL: PT0S
```

```bash
docker compose up -d
# then in your code use .localhost() — same as the c8run path
```

## Speed comparison

| Path | Cluster boot | Subsequent runs of `main()` |
|------|-------------|--------------------------------|
| `.testcontainer()` | ~30–60 s per JVM run | ~30–60 s per run (new container each time) |
| Docker Compose + `.localhost()` | ~30–60 s once | ~1–2 s per run |
| `c8run` + `.localhost()` | ~10–20 s once | ~1–2 s per run |

For tight iteration cycles, prefer the persistent-cluster paths.

## When `main()` runs

Each `.run(N)` invocation:

1. Generates a per-run prefix `<username>-<5-char-random>` (e.g. `stephan-r7f3a`).
2. Clones the BPMN model, prefixes the `processId` and every bound `zeebeJobType`. Element ids stay clean (so they read normally in Operate).
3. Deploys the rewritten model. Pins to the resulting `processDefinitionKey`.
4. Registers a worker per bound element, each wrapping your lambda in an SDK `JobHandler`.
5. Creates N instances pinned to the deployed key.
6. Returns a `Run` handle.

Find your run in Operate by filtering on the process id (e.g. `stephan-r7f3a-order`).

## Tips

- **Image version pinning for `.testcontainer()`**: defaults to `camunda/camunda:8.9.4` (the stable tag the runner is verified against). Override via JVM arg `-Dio.camunda.process.test.camundaDockerImageVersion=8.7.0` (or whichever stable tag you prefer).
- **Logs**: SLF4J binding is bundled (`slf4j-simple`). The runner emits INFO logs for cluster boot, deploy, worker registration, and instance creation. Container stdout streams into your console under logger `camunda-container` when `.testcontainer()` is used.
- **Cleanup**: `try-with-resources` on `Cluster` closes workers and (for owned clusters) shuts down the container/client. The deployment stays in the cluster for inspection.
- **Existing models**: if you already have a `BpmnModelInstance` from `Bpmn.createExecutableProcess(...)`, adopt it via `LiveBpmn.of(model).bind(elementId, lambda)…run(N, cluster)`.

## Realistic examples

Each lives under `src/main/java/io/camunda/runner/examples/` and has a `main()`:

| Example | What it shows |
|---------|---------------|
| `MinimalDemo` | Smallest demo: one task, inline lambda, showing `job.variable(...)`, `job.complete(map)`, `job.fail(reason)`. The "look at how this reads" example. |
| `OrderDemos.Inline` | Real multi-task order flow with **inline lambdas** in the fluent chain (`validate -> charge -> ship`). |
| `OrderDemos.Bindings` | Same process and handlers as `Inline`, but lambdas attached via `LiveBpmn.of(model).bind(...)`. The migration story for existing `Bpmn.createExecutableProcess(...)` code. |
| `LoadDemo` | Same process at scale: 50 instances paced 100 ms apart so they trickle into Operate visibly. Stress check too — `workersHandled` should always read 50 per task. |

`OrderDemos.Inline` and `OrderDemos.Bindings` live in the **same file** (`OrderDemos.java`) and
share their handlers (`validate`, `charge`, `ship`) so the only difference between them is the
wiring. Run either by right-clicking the inner class in your IDE.

Realistic handler details: handlers simulate I/O latency (~20–150 ms each), read multiple input
variables, branch on amount (e.g. credit-card vs wallet, express vs standard post), and emit
several output variables (`paymentId`, `paymentMethod`, `chargedAt`, `trackingId`, `carrier`).

After instance creation the runner logs a clickable URL (`Operate: http://...:port/operate/processes?process=<prefixedId>`) — paste it into your browser to watch the run flow.

## Variables and gateways

Every service-task lambda takes a `Job` and returns a `Map<String, Object>` of variable updates (or `null` for none):

```java
.serviceTask("validate", (Job job) -> {
  String orderId = job.variable("orderId", String.class);
  double amount  = job.variable("amount", Number.class).doubleValue();
  return Map.of("valid", amount > 0);   // becomes process variables, visible to later tasks
})
```

Provide initial variables with `RunOptions`:

```java
.run(
    RunOptions.of(3)
        .variables(i -> Map.of("orderId", "ORDER-" + (1000 + i), "amount", 19.99 + i)),
    cluster);
```

For exclusive gateways, set FEEL condition expressions on outgoing flows. Use `.moveToNode("<gatewayId>")` to come back to the gateway and add the second branch:

```java
.exclusiveGateway("decision")
.condition("=approved = true")
.serviceTask("ship", (Job j) -> Map.of("status", "shipped"))
.endEvent()
.moveToNode("decision")
.condition("=approved = false")
.serviceTask("reject", (Job j) -> Map.of("status", "rejected"))
.endEvent()
```

`RunOptions` also supports `pacing(Duration)` (spacing between `createInstance` calls), `tags(String...)` (extra instance tags) and `timeout(Duration)`.

## Build

```bash
./mvnw install -pl clients/java-runner -am -Dquickly -T1C
./mvnw verify  -pl clients/java-runner -DskipTests=false -DskipITs -Dquickly   # fast unit tests
./mvnw verify  -pl clients/java-runner -DskipITs=false                          # also boots a container
```

The integration test (`LiveBpmnIT`) skips cleanly if Docker is not available.
