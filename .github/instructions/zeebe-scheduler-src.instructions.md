```yaml
---
applyTo: "zeebe/scheduler/src/**"
---
```
# Zeebe Actor Scheduler Module

Custom cooperative actor-model concurrency framework for Zeebe. Provides single-threaded, non-blocking execution of actors via work-stealing thread groups. This module is the scheduling backbone for the entire Zeebe engine — `StreamProcessor`, broker partitions, exporters, and gateways all run as actors.

## Architecture

The scheduler follows a layered architecture: `ActorScheduler` → `ActorExecutor` → `ActorThreadGroup` (CPU/IO) → `ActorThread` → `ActorTask` → `ActorJob`. Each layer delegates downward.

- **`Actor`** (`Actor.java`): Base class all actors extend. Provides lifecycle hooks (`onActorStarting`, `onActorStarted`, `onActorClosing`, `onActorClosed`, `onActorCloseRequested`) and delegates to `ActorControl`.
- **`ActorControl`** (`ActorControl.java`): API surface for scheduling work within an actor — `run()`, `call()`, `submit()`, `schedule()`, `runAtFixedRate()`, `runAt()`, `runOnCompletion()`, `onCondition()`. All mutating methods enforce they are called from the owning actor thread via `ensureCalledFromWithinActor()`.
- **`ActorTask`** (`ActorTask.java`): One-to-one with an `Actor`. Manages the actor's lifecycle state machine (`STARTING → STARTED → CLOSE_REQUESTED → CLOSING → CLOSED`) and its job queues (fast-lane `Deque<ActorJob>` + external `ManyToOneConcurrentLinkedQueue<ActorJob>`).
- **`ActorJob`** (`ActorJob.java`): Unit of work within a task. Wraps either a `Runnable` or `Callable`. Jobs are pooled per-thread via `BoundedArrayQueue` (capacity 2048) and recycled after execution.
- **`ActorThread`** (`ActorThread.java`): Extends `Thread`. Runs the event loop: drain callbacks → process expired timers → get next task from `TaskScheduler` → execute task. Uses `BackoffIdleStrategy` when idle. Detects actor context via `instanceof` cast (no thread-local).
- **`ActorThreadGroup`** (`ActorThreadGroup.java`): Abstract group of threads with a shared `WorkStealingGroup`. Two concrete subclasses: `CpuThreadGroup` ("zb-actors") and `IoThreadGroup` ("zb-fs-workers").
- **`WorkStealingGroup`** (`WorkStealingGroup.java`): Implements `TaskScheduler`. Maintains per-thread `ActorTaskQueue` instances. Threads pop from their own queue first, then randomly steal from others.
- **`ActorTaskQueue`** (`ActorTaskQueue.java`): Lock-free linked queue with cache-line padding to prevent false sharing. Uses `sun.misc.Unsafe` for ordered/volatile field access.
- **`ActorScheduler`** (`ActorScheduler.java`): Top-level entry point. Builder-pattern construction. Manages lifecycle (`NEW → RUNNING → TERMINATING → TERMINATED`). Submits actors via `submitActor(actor, SchedulingHints)`.

## Key Abstractions

### Subscriptions (reactive wakeup)
- **`ActorSubscription`** (`ActorSubscription.java`): Interface for external work sources. `poll()` checks availability, `getJob()` provides the job, `triggersInPhase()` restricts to lifecycle phases.
- **`ActorFutureSubscription`**: Wakes actor when an `ActorFuture` completes. Uses bitmask for phase filtering.
- **`ActorConditionImpl`**: Signal-based wakeup using `AtomicLong` trigger count. Recurring subscription.
- **`TimerSubscription`**: Interface for timer-based wakeup. Two implementations:
  - `DelayedTimerSubscription`: Relative delay, optionally recurring (used by `schedule()` and `runAtFixedRate()`).
  - `StampedTimerSubscription`: Absolute timestamp, one-shot (used by `runAt()`).
- **`ActorTimerQueue`**: Wraps Agrona `DeadlineTimerWheel` for efficient timer expiry processing.

### Futures
- **`ActorFuture<V>`** (`future/ActorFuture.java`): Core future interface extending `java.util.concurrent.Future`. Supports `join()`, `onComplete()`, `andThen()`, `thenApply()`, `block()`, and conversion to `CompletableFuture`.
- **`CompletableActorFuture<V>`** (`future/CompletableActorFuture.java`): Reusable, garbage-free implementation. Uses `Unsafe.compareAndSwapInt` for state transitions (`AWAITING_RESULT → COMPLETING → COMPLETED`). Blocks non-actor threads via `ReentrantLock` + `Condition`; actor threads must never call `get()` on incomplete futures.
- **`ActorFutureCollector`**: Stream collector that aggregates multiple `ActorFuture` instances into `ActorFuture<List<V>>`.

### Concurrency Control
- **`ConcurrencyControl`** (`ConcurrencyControl.java`): Interface guaranteeing serial execution of scheduled work. Extends `Executor`. Used extensively outside the scheduler to decouple from `Actor`/`ActorControl` directly.

### Clock
- **`ActorClock`** (`clock/ActorClock.java`): Abstraction over system time. `DefaultActorClock` minimizes `System.currentTimeMillis()` calls. `ControlledActorClock` allows deterministic time control in tests.

## Sub-packages

### `retry/` — Actor-aware retry strategies
All implement `RetryStrategy` interface, take `ActorControl` in constructor, return `ActorFuture<Boolean>`:
- **`EndlessRetryStrategy`**: Retries on any exception, yields thread between attempts.
- **`RecoverableRetryStrategy`**: Retries only on `RecoverableException`, fails on others.
- **`AbortableRetryStrategy`**: Fails future on any exception (no exception-based retry).
- **`BackOffRetryStrategy`**: Exponential backoff via `actor.schedule()`, doubling from `initialBackOff` up to `maxBackOff`.
- **`ActorRetryMechanism`**: Shared stateful helper wrapping `OperationToRetry` + terminate condition + future.

### `startup/` — Ordered startup/shutdown process
- **`StartupStep<CONTEXT>`**: Interface with `startup(context)` and `shutdown(context)` returning `ActorFuture<CONTEXT>`.
- **`StartupProcess<CONTEXT>`**: Executes steps sequentially on startup, reverse-order on shutdown. Collects exceptions as suppressed. Supports concurrent shutdown during startup.

### `health/` — Health monitoring
- **`CriticalComponentsHealthMonitor`**: Actor-based health aggregator. Registers `HealthMonitorable` components, polls at 60s intervals, reports aggregate status via `FailureListener`. Uses `ActorControl` for thread-safe state mutations.

## Extension Points

- **New actor**: Extend `Actor`, override lifecycle hooks, schedule work via `actor.run()` / `actor.schedule()` / `actor.runOnCompletion()`. Submit via `actorScheduler.submitActor(myActor)`.
- **New subscription type**: Implement `ActorSubscription`, add to task via `task.addSubscription()`. Must be polled by `ActorTask.pollSubscriptions()`.
- **New retry strategy**: Implement `RetryStrategy`, use `ActorRetryMechanism` for the retry loop, call `actor.run()` / `actor.yieldThread()` for scheduling.
- **New startup step**: Implement `StartupStep<CONTEXT>`, add to `StartupProcess` step list.

## Invariants

- **Never block an actor thread.** Actors must be non-blocking. Use `runOnCompletion()` instead of `future.get()`. `CompletableActorFuture.get()` throws `IllegalStateException` if called from an actor thread on an incomplete future.
- **Actor methods are single-threaded.** `ensureCalledFromWithinActor()` / `ensureCalledFromActorThread()` enforce this at runtime. Never call `ActorControl` methods from external threads except `call()` and `submit()`.
- **`call()` cannot self-call.** `ActorControl.call()` throws if invoked from the same actor task (prevents deadlock).
- **Jobs from non-subscription runnables are one-shot.** The runnable reference is nulled after execution (`ActorJob.invoke()`). Subscription-triggered jobs persist across invocations.
- **`ClosedQueue` rejects submissions.** When an actor closes, its queues are replaced with `ClosedQueue` that fails any submitted job's future with "Actor is closed".
- **Lifecycle phase values are power-of-2 bitmasks** (1, 2, 4, 8, 16, 32) to support bitwise phase filtering in `ActorFutureSubscription`.
- **`ActorTaskQueue` uses cache-line padding** (15 `long` fields) to prevent false sharing between head and tail on different CPU cores.

## Common Pitfalls

- Calling `actor.call()` from within the same actor causes `UnsupportedOperationException`. Use `actor.run()` or `actor.submit()` instead.
- Using `Thread.sleep()` or blocking I/O on an actor thread starves the entire thread group. Use timer subscriptions for delays.
- Forgetting `actor.yieldThread()` in retry loops monopolizes the thread. All retry strategies call it after re-scheduling.
- Modifying `ActorTask.subscriptions` from outside the actor thread causes `ConcurrentModificationException`. All subscription operations are guarded by `ensureCalledFromActorThread()`.
- External job submission after actor closure silently fails the job's future (via `ClosedQueue.offer()`). Check `actor.isClosed()` before submitting if the caller needs to handle this.

## Testing Utilities (in `src/test/java`)

- **`TestConcurrencyControl`**: Synchronous `ConcurrencyControl` implementation for unit tests without starting the scheduler. Executes runnables inline. Detects recursive scheduling to prevent stack overflow.
- **`TestActorFuture`**: Simplified future that completes synchronously.
- **`ControlledActorSchedulerExtension`** / **`ControlledActorSchedulerRule`**: JUnit 5/4 extensions providing a `ControlledActorThread` for deterministic test execution.
- **`ActorSchedulerRule`**: JUnit 4 rule that starts/stops a real `ActorScheduler`.

## Key Reference Files

- `Actor.java` — base class, lifecycle hooks, `ActorBuilder`
- `ActorControl.java` — scheduling API surface (run, call, submit, schedule, conditions, futures)
- `ActorTask.java` — lifecycle state machine, job execution loop, subscription polling
- `ActorThread.java` — event loop (`doWork()`), timer queue, job pooling, idle strategy
- `WorkStealingGroup.java` — per-thread queue + random-offset work stealing
- `future/CompletableActorFuture.java` — garbage-free, CAS-based future with `andThen` chaining
- `retry/BackOffRetryStrategy.java` — exponential backoff pattern using `actor.schedule()`
- `startup/StartupProcess.java` — ordered startup/shutdown with exception aggregation