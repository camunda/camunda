# Run Java client job handlers on virtual threads by default

**DRI**: Joshua Wulf

**Status**: Proposed

**Deciders**
- Jonathan Lukas

**Purpose**: Decide how the Java client and the Spring Boot starter execute job handlers when
the user has not configured any job handling threads or executor, and pin down the compatibility
guarantees for users who have.

**Audience**: Engineers working on the Java client job worker and the Camunda Spring Boot
starter, and the teams (Connectors, Camunda Process Test, docs) that build on them.

## Context

### The problem

Out of the box, the Java client runs every job handler of every job worker of a client on **one
platform thread**:

- Plain client: `CamundaClientImpl.buildExecutorService()` creates
  `Executors.newFixedThreadPool(numJobWorkerExecutionThreads)`, and
  `DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS` is `1`.
- Spring Boot starter: `CamundaClientExecutorService.createDefault(execution-threads)` creates a
  single `ScheduledThreadPoolExecutor` of `execution-threads` (default `1`) and uses it for
  scheduling (polling, stream management, command retry back-off) **and** job handling.

Each worker already bounds its in-flight jobs with `maxJobsActive` (default 32), and polling was
decoupled from handling (#40058), so the worker keeps activating jobs up to that bound. But all
those jobs queue behind the one handling thread. A single handler that blocks (a slow HTTP call, a
missing timeout, a deadlock) stops every job of every worker of that client. Activated jobs sit in
the queue until their job timeout expires, are re-activated, and queue again. From the outside
this looks like the worker has stopped: the symptom reported in SUPPORT-34723. Users who try to
fix it by raising `maxJobsActive` get more queued jobs, not more parallelism, which is the
confusion in #14662.

### How the other Camunda 8 SDKs do it

The C#, Go, Python, TypeScript/JavaScript and Rust SDKs expose one concurrency knob (a maximum
number of concurrently active jobs per worker, typically defaulting to around 10) and size the
execution substrate themselves: the .NET thread pool, goroutines, the tokio runtime, asyncio or a
thread pool sized from the cap, Node's event loop or worker threads. None of them asks the user for
a thread count.

### Why the Java client has a thread count

The setting dates from the 2017 "job subscription" design (the setter parameter was still named
`numSubscriptionThreads`), when a fixed pool of platform threads was the idiomatic way to run
blocking work on the JVM and a thread cost around a megabyte of stack. A default of one thread was
a conservative choice for a pool that also did the polling. Neither constraint holds for the
client in 2026:

- Virtual threads are final since JDK 21 (JEP 444). JDK 24 removed pinning on `synchronized`
  (JEP 491), and JDK 25 is the current LTS.
- Polling no longer shares the handling thread (#40058), and `maxJobsActive` already bounds how
  many jobs a worker holds, so a second bound in the form of a thread count is not needed for
  back-pressure.
- `camunda-spring-boot-starter-virtual-threads` (#41070, #42408) already offers a virtual-thread
  job handling executor as an opt-in module. It had to be a separate module because the client
  targets Java 8 and the starter Java 17.

The client still compiles for Java 8, and the starter for Java 17, so both must keep working on
JVMs without virtual threads.

## Decision

**D1. `maxJobsActive` is the one knob for handler concurrency.** It already bounds the jobs a
worker holds. By default it now also bounds how many of that worker's handlers run at the same
time.

**D2. When job handling is not configured, run each job handler on its own virtual thread.**
"Not configured" means none of `numJobWorkerExecutionThreads(int)`, the
`camunda.client.worker.threads` property, or `jobHandlingExecutor(...)` was set on the builder;
in the Spring Boot starter, `camunda.client.execution-threads` (or its legacy aliases, or the
per-client `camunda.clients.<name>.execution-threads`) was not set and no
`CamundaClientExecutorService` bean was provided. The client creates a thread-per-task executor
of virtual threads named `job-worker-virtual-N` (the prefix used by the virtual-threads starter),
owns it, and shuts it down, interrupting running handlers, when the client closes. In the starter,
scheduling moves to its own single platform thread, the layout the virtual-threads starter already
uses.

**D3. Explicit configuration keeps its current behavior exactly.**

|                                            Configuration                                             |                           Job handlers run on                           |
|------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| nothing set, JDK 21+                                                                                 | a new virtual thread per job (new)                                      |
| nothing set, JDK 8 to 20                                                                             | one platform thread, as before                                          |
| threads = `n` (n >= 1)                                                                               | fixed pool of `n` platform threads, as before                           |
| threads = `0`                                                                                        | the scheduling executor, as before                                      |
| custom `jobHandlingExecutor`                                                                         | the custom executor, as before, ownership as passed                     |
| Spring, `execution-threads` not set, JDK 21+                                                         | a new virtual thread per job (new); scheduling on one platform thread   |
| Spring, `execution-threads` not set, JDK 17 to 20                                                    | one shared scheduled platform thread, as before                         |
| Spring, `execution-threads = n`                                                                      | one shared scheduled pool of `n` for scheduling and handling, as before |
| Spring, custom `CamundaClientExecutorService` bean, or `camunda-spring-boot-starter-virtual-threads` | the bean's executors, as before                                         |

**D4. Detect virtual threads at runtime; don't raise the baseline.** The client looks up
`Thread.ofVirtual()` and `Executors.newThreadPerTaskExecutor(ThreadFactory)` reflectively
(`io.camunda.client.impl.util.VirtualThreads`). If they're missing, or they throw (JDK 19/20
without `--enable-preview`), it falls back to today's single platform thread and logs that at
debug level.

**D5. Distinguish "not set" from "set to 1" without changing public API.**
`CamundaClientConfiguration#getNumJobWorkerExecutionThreads()` still returns `int`, and still
returns `1` when nothing is configured. `CamundaClientBuilderImpl` records internally whether the
value was set, and `withConfiguration(...)` carries that over when copying from another builder. A
third-party `CamundaClientConfiguration` passed to `CamundaClient.newClient(configuration)` or
`newClientBuilder(configuration)` can't express "not set", so its value counts as set, and those
integrations keep today's behavior. In the starter, `CamundaClientProperties#executionThreads`
defaults to `null` instead of `1`, so an unset property stays distinguishable from `1`.

**D6. Keep `numJobWorkerExecutionThreads` / `execution-threads`, and give them a new purpose.**
They're not deprecated. They become the way to get a fixed pool of platform threads, e.g. to cap
CPU-bound parallelism across all workers of a client, and `1` restores one-at-a-time handling.

**D7. Don't couple to `spring.threads.virtual.enabled`.** That Spring Boot property defaults to
`false` and governs Spring's own executors (web server, `@Async`, scheduling). Following it would
leave the default (and the SUPPORT-34723 symptom) unchanged for almost every Spring user, and it
would give Spring users an opt-out that plain-Java users don't have. The starter follows the same
rule as the client, and `execution-threads` is the opt-out in both. The virtual-threads starter
module keeps working unchanged. It still forces virtual threads when `execution-threads` is set,
but it isn't needed for the default case any more.

## Consequences

- Out of the box on JDK 21+, a blocked or slow handler no longer stalls the other jobs of the
  client. Each worker handles up to `maxJobsActive` jobs concurrently, which is how the other
  SDKs behave.
- **Behavior change for unconfigured applications on JDK 21+:** handlers that used to run one at
  a time now run concurrently, up to `maxJobsActive` per worker. Handlers that share mutable
  state without synchronization, rely on ordering, or call a downstream system that tolerates
  only one caller will see new interleavings. Opt-out: set `numJobWorkerExecutionThreads(1)` /
  `camunda.client.execution-threads=1`.
- More concurrent handlers mean more concurrent job completions. Over REST these share the HTTP
  connection pool (`maxHttpConnections`, default 100). If the concurrent handlers of all workers
  exceed it, completions queue for a connection. They don't fail.
- Handlers that do heavy CPU work, or that depend on `ThreadLocal` caching of expensive
  resources, are better served by a fixed pool (D6). On JDK 21 to 23, a handler that blocks
  inside `synchronized` pins its carrier thread. That limits throughput but is no worse than
  today's single thread.
- The handling executor is now a thread-per-task executor, not a `ThreadPoolExecutor`, so
  thread-pool gauges from `ExecutorServiceMetrics` don't apply to it. Task timings still do.

## Alternatives considered

- **Raise the default thread count (e.g. to `maxJobsActive` or the CPU count).** It reduces the
  symptom but keeps a second, confusing knob. The right number depends on each worker's
  `maxJobsActive` and on how much of a handler's time is spent blocked, which the client can't
  know.
- **Size a platform pool from the sum of `maxJobsActive` across workers.** Workers are opened
  after the executor is built, so the pool would have to resize dynamically. Virtual threads make
  that unnecessary.
- **Raise the client baseline to Java 21.** It's a breaking change for Java 8/11/17 users, and
  runtime detection gives the same result on 21+.
- **Add a public `isNumJobWorkerExecutionThreadsSet()` or an `Optional`/`Integer` getter to
  `CamundaClientConfiguration`.** It grows the public API for a distinction that only the
  client's own builder and the starter need to make, and external implementations would still
  have to opt in.
- **Make the new default opt-in (e.g. follow `spring.threads.virtual.enabled`).** It's
  non-breaking, but it leaves new users with the surprising default, which is the problem being
  fixed (D7).

## Backward-compatibility guarantees

- No public API is removed or changes signature. `getNumJobWorkerExecutionThreads()` returns the
  same values as before.
- Any explicit thread count (`>= 1` or `0`), property, legacy Spring alias, or custom executor
  or `CamundaClientExecutorService` bean behaves exactly as before.
- On JVMs without virtual threads, nothing changes.
- The only observable change is D2 for applications that configured nothing, on JDK 21+. The
  opt-out is a single property.

## Follow-ups

- Update the job worker and Spring Boot starter pages in camunda/camunda-docs (thread
  configuration, `execution-threads` default, the behavior change above).
- Camunda Process Test (`CamundaProcessTestDefaultConfiguration`) builds its own shared,
  non-owned executor and keeps today's single-thread default. Consider aligning it separately.
- Consider deprecating `camunda-spring-boot-starter-virtual-threads` once the default has
  shipped.
- 

# 59635 (job worker capacity leak) is a separate bug and not addressed here.

## References

- SUPPORT-34723 (stalled workers with the default single handling thread)
- camunda/camunda#14662 (thread setting has no effect on parallel job execution)
- camunda/camunda#40058 (separate job handling executor), #40903 (`0` = reuse scheduling executor)
- camunda/camunda#41070, #42408 (virtual-threads Spring Boot starter module)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444),
  [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- [Guidance: Using Virtual Threads at Camunda](../../virtual_threads.md)

