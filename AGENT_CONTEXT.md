# One-File Process Runner — Hackday Agent Context

> Read this first before doing anything.

## Project

**One-File Process Runner** — A Java DSL that composes process definition, inline lambda workers,
and deployment into a single `run()` call. Define the flow, plug workers in as inline lambdas,
hit run. The SDK deploys to whatever cluster you're pointed at, opens uniquely-named workers,
fires N instances — and you watch them flow through Operate live, breakpoints firing in your IDE.

**Track:** C2T / P&P (Hackday)

## Motivation

The fluent builder exists. CPT exists. The SDK exists. But to play with Camunda you still
juggle a `.bpmn` file, a worker app, and a deploy step. This project composes them into one.

## What We're Building

A Java library (likely a new module or test utility) that provides a fluent DSL:

```java
process("order")
  .start()
  .serviceTask("validate", ctx -> ctx.complete("valid", true))
  .gateway().when("=valid")
    .serviceTask("ship", ctx -> { /* breakpoint here */ })
  .end()
  .run(100, clusterConfig); // 100 instances → your cluster, your workers, your IDE
```

### What `run()` does under the hood

1. **Generates BPMN XML** from the fluent DSL (using the existing BPMN model API)
2. **Deploys** to the target cluster via the Java SDK / V2 API
3. **Registers workers** with unique names (so multiple runs don't collide)
4. **Creates N process instances** with optional variable generators
5. **Blocks until complete** (or timeout), reporting progress
6. **Cleans up** workers on shutdown

### Key Modules to Build On

| Module | What it provides |
|--------|-----------------|
| `zeebe/bpmn-model` | BPMN model API — programmatic BPMN XML generation |
| `clients/java` | Zeebe Java client — deploy, create instances, register workers |
| `testing/` | `camunda-process-test` — existing test framework with similar patterns |
| `spring-utils/` | Spring Boot starters — cluster connection config |

### Existing Prior Art to Study

- `testing/camunda-process-test/` — the CPT framework already provides fluent process testing
- `clients/java/src/main/java/io/camunda/client/` — the Java client API
- `zeebe/bpmn-model/src/main/java/io/camunda/zeebe/model/bpmn/` — BPMN builder API (`Bpmn.createExecutableProcess()`)

## Tech Stack

- **Java 21** — the module's language
- **Maven** — build system
- **Zeebe Java Client** — cluster communication
- **BPMN Model API** — programmatic process generation
- **JUnit 5 + AssertJ** — tests

## Development Workflow

### Running in This Container

```bash
# Build relevant modules
./mvnw install -pl clients/java,zeebe/bpmn-model -am -Dquickly -T1C

# Build the new module (once created)
./mvnw install -pl <new-module> -am -Dquickly -T1C

# Run tests
./mvnw verify -pl <new-module> -Dtest=ProcessRunnerTest -DskipTests=false -DskipITs -Dquickly

# Format before commit (MANDATORY)
./mvnw license:format spotless:apply -T1C
```

## Orchestration Matrix

> **Design is locked.** See `clients/java-runner/DESIGN.md` for the full API and behaviour.
> The phases below are the *implementation* roadmap, not a design exercise — design questions
> were settled in the interview captured in `livebpmn_decisions.md` (orchestrator memory).

### Phase 0: Module skeleton (zero risk, fast)

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 0.1 | general-purpose | Create `clients/java-runner/` Maven module: `pom.xml` (depends on `clients/java`, `zeebe/bpmn-model`, JUnit 5, AssertJ, Testcontainers), package layout, license headers | Builds |
| 0.2 | Lint | `./mvnw license:format spotless:apply -T1C` | Clean |
| 0.3 | Build | `./mvnw install -pl clients/java-runner -am -Dquickly -T1C` | Pass |
| 0.4 | Commit | `feat: scaffold clients/java-runner module` | Pushed |

### Phase 1: Builder + Job + lambda capture (no cluster yet)

Goal: `LiveBpmn.createExecutableProcess(...)` builds a `BpmnModelInstance` indistinguishable
from `Bpmn.createExecutableProcess(...)` while also capturing lambdas keyed by element id.
`.done()` returns the underlying model — drop-in.

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 1.1 | tdd-engineer | Tests: builder mirrors `Bpmn` shape; `.done()` returns valid `BpmnModelInstance` byte-for-byte equivalent for the no-lambda case; lambdas captured by elementId; `.of(model).bind(id, lambda)` path | Tests fail |
| 1.2 | tdd-engineer | Tests: `Job` interface contract (variables, complete/fail resolution, throw on double-resolve) | Tests fail |
| 1.3 | general-purpose | Implement `LiveBpmn` facade, `RunnableProcessBuilder`, hand-mirrored builder methods (startEvent, endEvent, serviceTask×3 overloads, userTask×3, exclusive/parallel gateway, sequenceFlowTo, condition, name), `.raw()` escape hatch | Tests pass |
| 1.4 | general-purpose | Implement `Job` interface + default impl wrapping `ActivatedJob` + `JobClient`; resolution rules | Tests pass |
| 1.5 | Lint | `./mvnw license:format spotless:apply -T1C` | Clean |
| 1.6 | code-reviewer | Quality, conventions, confidence ≥ 90 | No Critical |
| 1.7 | architecture-critic | Wrapper-builder strategy review (delegation, overload disambiguation, `.raw()` boundary) | No Blockers |
| 1.8 | code-simplifier | Catch unnecessary complexity in the wrapper | Clean |
| 1.9 | Build + commit | `./mvnw install -pl clients/java-runner -am -Dquickly -T1C` then `feat: add LiveBpmn builder and Job API` | Pass |
| **1.10** | **HUMAN CHECKPOINT** | **Stephan reviews builder ergonomics + `Job` shape against the design doc** | **Approval** |

### Phase 2: Runner pipeline (deploy + workers + Run handle, with Cluster)

Goal: `.run(N)` actually runs against a cluster. Smart-default cluster works. `Run.await()`
blocks until terminal. `instances()` / `workers()` / `progress()` return useful data.

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 2.1 | tdd-engineer | Tests: model rewrite (processId + jobType prefixed, elementId clean); validation (binding for missing element fails loud); `processDefinitionKey` pinning on createInstance | Tests fail |
| 2.2 | tdd-engineer | Integration tests using Testcontainer: deploy → workers register → N instances created → `Run.await()` returns when terminal; worker stats track jobsHandled/Failed; instance state polling | Tests fail |
| 2.3 | general-purpose | Implement `Cluster` + `ClusterFactory` (`testcontainer`, `localhost`, `properties`, `using`, `auto`) with lazy materialisation and lifecycle ownership rules | Tests pass |
| 2.4 | general-purpose | Implement runner pipeline: clone-and-rewrite model → validate → deploy → register decorated workers → wait for subscription → create N instances pinned + tagged → return `Run` handle → JVM shutdown hook | Tests pass |
| 2.5 | general-purpose | Implement `Run` handle: `instances()` (SDK search), `workers()` (local stats decorator), `progress()`, `await(Duration)`, `close()` | Tests pass |
| 2.6 | Lint | `./mvnw license:format spotless:apply -T1C` | Clean |
| 2.7 | code-reviewer | Quality + conventions | No Critical |
| 2.8 | security-auditor | Connection handling, credential exposure (especially `properties()` and Testcontainer config), shutdown-hook safety | No Critical/High |
| 2.9 | silent-failure-hunter | Worker-decorator path is async; ensure exceptions are not swallowed; ensure deploy/createInstance failures propagate | None found |
| 2.10 | architecture-critic | Pipeline review — over-engineering, layering, lifecycle ownership clarity | No Blockers |
| 2.11 | product-reviewer | Run end-to-end on smart-default cluster; is the DX what we promised? | SHIP or ITERATE |
| 2.12 | Build + commit | `feat: add LiveBpmn runner pipeline with Cluster and Run handle` | Pass |
| **2.13** | **HUMAN CHECKPOINT** | **Stephan runs `OrderDemo.main()` against a real cluster from IDE; sets a breakpoint; watches Operate** | **Approval** |

### Phase 3: Example + RunOptions + polish

Goal: ship the demo example. Add `RunOptions` (pacing, timeout, tags, variables generator).
Document.

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 3.1 | tdd-engineer | Tests: `RunOptions` (pacing delays creates, timeout aborts await, variables/generator passed through, tags merged with auto-tags) | Tests fail |
| 3.2 | general-purpose | Implement `RunOptions` + the `run(RunOptions)` / `run(RunOptions, Cluster)` overloads and `.on(Cluster)` chain helper | Tests pass |
| 3.3 | general-purpose | Add `OrderDemo.java` under `clients/java-runner/src/main/java/io/camunda/runner/examples/` — runnable from IDE with `main` | Runs |
| 3.4 | general-purpose | README under `clients/java-runner/` linking back to DESIGN.md, with the one-file example as the README's first code block | Reads well |
| 3.5 | Lint | `./mvnw license:format spotless:apply -T1C` | Clean |
| 3.6 | code-reviewer + code-simplifier | Final quality + simplification pass | Clean |
| 3.7 | Build + commit | `feat: add LiveBpmn RunOptions, OrderDemo example, and README` | Pass |
| **3.8** | **HUMAN CHECKPOINT** | **Stephan demos end-to-end on a fresh checkout** | **Done** |

### Phase 4 (optional, time-permitting): dashboard + extra examples

Only if Phase 3 lands cleanly with time to spare.

| Step | Agent | Task | Gate |
|------|-------|------|------|
| 4.1 | general-purpose | ANSI dashboard rendering for `Run` (htop-style table of workers + progress bar). Fallback to periodic stdout when not a TTY. | Works |
| 4.2 | general-purpose | Additional examples: `LoadDemo.java` (1000 instances paced), `AdoptDemo.java` (`.of(existingModel).bind(...)`), `ListenersDemo.java` | Works |
| 4.3 | code-reviewer + code-simplifier | Quality | Clean |
| 4.4 | Build + commit + checkpoint | | Done |

## Conventions (from AGENTS.md)

- Follow Google Java Format (Spotless)
- Tests: `should` prefix, `// given / when / then`, AssertJ, JUnit 5
- Commits: conventional commits, max 120 chars, no scopes
- **Always** run `./mvnw license:format spotless:apply -T1C` before committing
