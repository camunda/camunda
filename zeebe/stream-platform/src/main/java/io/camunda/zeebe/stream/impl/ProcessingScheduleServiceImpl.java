/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Here the implementation is just a suggestion to amke the engine abstraction work. Can be whatever
 * PDT team thinks is best to work with
 */
public class ProcessingScheduleServiceImpl
    implements SimpleProcessingScheduleService, AutoCloseable {

  private static final ScheduledTask NOOP_SCHEDULED_TASK = () -> {};
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final Supplier<StreamProcessor.Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<LogStreamWriter> writerSupplier;
  private final StageableScheduledCommandCache commandCache;
  private final InstantSource clock;
  private final long interval;
  private final ScheduledTaskMetrics metrics;
  private final PriorityQueue<ScheduledTaskImpl> scheduledTasks = new PriorityQueue<>();
  private LogStreamWriter logStreamWriter;
  private ActorControl actorControl;
  private AbortableRetryStrategy writeRetryStrategy;
  private CompletableActorFuture<Void> openFuture;
  // invoked on this service's actor right before a task executes; a non-null future defers the
  // task until the barrier's drain attempt finished (see taskExecutionBarrier(Supplier))
  private Supplier<ActorFuture<Void>> taskExecutionBarrier = () -> null;
  // when set, every task execution first awaits the future supplied for the task's tolerated
  // view staleness — used by the experimental layered-state wiring to freeze buffered state into
  // a fresh read view before an async checker runs, or to reuse a fresh-enough view for polling
  // checkers (see taskFreshnessPreparation(Function))
  private Function<@Nullable Duration, ActorFuture<Void>> taskFreshnessPreparation;

  public ProcessingScheduleServiceImpl(
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<LogStreamWriter> writerSupplier,
      final StageableScheduledCommandCache commandCache,
      final InstantSource clock,
      final Duration interval,
      final ScheduledTaskMetrics metrics) {
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerSupplier = writerSupplier;
    this.commandCache = commandCache;
    this.clock = clock;
    this.interval = interval.toMillis();
    this.metrics = metrics;
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable task) {
    if (actorControl == null) {
      LOG.warn("ProcessingScheduleService hasn't been opened yet, ignore scheduled task.");
      return NOOP_SCHEDULED_TASK;
    }
    return schedule(clock.millis() + delay.toMillis(), task);
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    return runDelayed(delay, toRunnable(task));
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    return runAt(timestamp, toRunnable(task));
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Runnable task) {
    if (actorControl == null) {
      LOG.warn("ProcessingScheduleService hasn't been opened yet, ignore scheduled task.");
      return NOOP_SCHEDULED_TASK;
    }
    return schedule(timestamp, task);
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Runnable task) {
    runDelayed(
        delay,
        guardRunnable(
            () -> {
              try {
                task.run();
              } finally {
                runAtFixedRate(delay, task);
              }
            }));
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    runDelayed(
        delay,
        toRunnable(
            builder -> {
              try {
                return task.execute(builder);
              } finally {
                runAtFixedRate(delay, task);
              }
            }));
  }

  public ActorFuture<Void> open(final ActorControl control) {
    if (openFuture != null) {
      return openFuture;
    }

    openFuture = new CompletableActorFuture<>();
    writeRetryStrategy = new AbortableRetryStrategy(control);

    logStreamWriter = writerSupplier.get();
    actorControl = control;
    openFuture.complete(null);
    actorControl.runAtFixedRate(Duration.ofMillis(interval), this::processScheduledTasks);
    return openFuture;
  }

  @Override
  public void close() {
    actorControl = null;
    logStreamWriter = null;
    writeRetryStrategy = null;
    openFuture = null;
  }

  private void processScheduledTasks() {
    final var now = clock.millis();
    while (scheduledTasks.peek() != null && scheduledTasks.peek().scheduledTime <= now) {
      metrics.decrementScheduledTasks();
      final var expiredTask = scheduledTasks.poll();
      actorControl.submit(expiredTask);
    }
  }

  private ScheduledTask schedule(final long timestamp, final Runnable task) {
    final var scheduledTask = new ScheduledTaskImpl(timestamp, task);
    actorControl.run(
        () -> {
          metrics.incrementScheduledTasks();
          final var delay = timestamp - clock.millis();
          scheduledTasks.add(scheduledTask);
          if (delay < interval / 2) {
            actorControl.schedule(Duration.ofMillis(delay), this::processScheduledTasks);
          }
        });
    return scheduledTask;
  }

  /**
   * Installs a barrier invoked on this service's actor right before every task execution. Used by
   * the experimental layered-state wiring to drain buffered state so persisted-state scans (e.g.
   * due-date checkers) observe every batch committed before the task's preparation began. The
   * barrier returns null when the task may run right now, or a future — never completing
   * exceptionally — after which the task runs (re-checking only the abort condition and the
   * processor phase, not the barrier: one preparation makes one drain attempt, deliberately not
   * chasing batches committed while it drained, which would starve the task under sustained load).
   * Must be set before the service is opened.
   */
  void taskExecutionBarrier(final Supplier<ActorFuture<Void>> barrier) {
    taskExecutionBarrier = Objects.requireNonNull(barrier);
  }

  /**
   * Installs a preparation step awaited on this service's actor right before every task execution,
   * keyed by the task's {@link Task#toleratedViewStaleness()}. Used by the experimental
   * layered-state wiring on the asynchronous schedule services: the supplied future completes once
   * the stream processor froze its buffered state into a fresh read view — or immediately, for a
   * polling task whose current published view is younger than its tolerance — so a view acquired by
   * the task observes every batch committed before the task ran (event-driven tasks) or lags by
   * less than the task's own period (polling tasks). The task executes either way once the future
   * completes; a failed preparation only means a staler view, which is preferable to blocking all
   * scheduled work. Must be set before the service is opened.
   */
  void taskFreshnessPreparation(final Function<@Nullable Duration, ActorFuture<Void>> preparation) {
    taskFreshnessPreparation = Objects.requireNonNull(preparation);
  }

  Runnable guardRunnable(final Runnable task) {
    return new Runnable() {
      @Override
      public void run() {
        if (abortCondition.getAsBoolean()) {
          // it might be that we are closing, then we should just stop
          return;
        }

        final var currentStreamProcessorPhase = streamProcessorPhaseSupplier.get();
        if (currentStreamProcessorPhase != Phase.PROCESSING) {
          LOG.trace(
              "Not able to execute scheduled task right now. [streamProcessorPhase: {}]",
              currentStreamProcessorPhase);
          return;
        }

        final var barrier = taskExecutionBarrier.get();
        if (barrier != null) {
          // committed state is not visible to the task yet (buffered state must drain, or a
          // processing batch is in flight); run once the barrier's drain attempt finished
          actorControl.runOnCompletion(barrier, (ok, error) -> runPrepared(task));
          return;
        }

        runPrepared(task);
      }

      private void runPrepared(final Runnable task) {
        if (abortCondition.getAsBoolean()) {
          return;
        }
        final var phaseAfterBarrier = streamProcessorPhaseSupplier.get();
        if (phaseAfterBarrier != Phase.PROCESSING) {
          // the phase changed while the barrier drained; drop the task, matching the pre-barrier
          // check of this path
          LOG.trace(
              "Not able to execute scheduled task right now. [streamProcessorPhase: {}]",
              phaseAfterBarrier);
          return;
        }
        if (taskFreshnessPreparation != null) {
          // a plain runnable carries no staleness tolerance: run only after the buffered state
          // was frozen into a fresh read view; on preparation failure the task still runs — a
          // staler view beats blocking all scheduled work
          actorControl.runOnCompletion(
              taskFreshnessPreparation.apply(null), (ok, error) -> task.run());
          return;
        }

        task.run();
      }
    };
  }

  Runnable toRunnable(final Task task) {
    return () -> {
      if (abortCondition.getAsBoolean()) {
        // it might be that we are closing, then we should just stop
        return;
      }

      final var currentStreamProcessorPhase = streamProcessorPhaseSupplier.get();
      if (currentStreamProcessorPhase != Phase.PROCESSING) {

        // We want to execute the scheduled tasks only if the StreamProcessor is in the PROCESSING
        // PHASE
        //
        // To make sure that:
        //
        //  * we are not running during replay/init phase (the state might not be up-to-date yet)
        //  * we are not running during suspending
        //
        LOG.trace(
            "Not able to execute scheduled task right now. [streamProcessorPhase: {}]",
            currentStreamProcessorPhase);
        actorControl.submit(toRunnable(task));
        return;
      }

      final var barrier = taskExecutionBarrier.get();
      if (barrier != null) {
        // committed state is not visible to the task yet (buffered state must drain, or a
        // processing batch is in flight); run once the barrier's drain attempt finished
        actorControl.runOnCompletion(barrier, (ok, error) -> executePreparedTask(task));
        return;
      }

      executePreparedTask(task);
    };
  }

  private void executePreparedTask(final Task task) {
    if (abortCondition.getAsBoolean()) {
      return;
    }
    final var phaseAfterBarrier = streamProcessorPhaseSupplier.get();
    if (phaseAfterBarrier != Phase.PROCESSING) {
      // the phase changed while the barrier drained; requeue the task, matching the pre-barrier
      // check of this path
      LOG.trace(
          "Not able to execute scheduled task right now. [streamProcessorPhase: {}]",
          phaseAfterBarrier);
      actorControl.submit(toRunnable(task));
      return;
    }
    if (taskFreshnessPreparation != null) {
      // run only after view freshness was prepared for the task's staleness tolerance — a freeze
      // for event-driven tasks, possibly a reused fresh view for polling ones; on preparation
      // failure the task still runs — a staler view beats blocking all scheduled work
      actorControl.runOnCompletion(
          taskFreshnessPreparation.apply(task.toleratedViewStaleness()),
          (ok, error) -> executeTask(task));
      return;
    }

    executeTask(task);
  }

  private void executeTask(final Task task) {
    if (abortCondition.getAsBoolean()) {
      // it might be that we are closing, then we should just stop
      return;
    }
    final var stagedCache = commandCache.stage();
    final var builder = new BufferedTaskResultBuilder(logStreamWriter::canWriteEvents, stagedCache);
    final var result = task.execute(builder);
    final var recordBatch = result.getRecordBatch();

    // Persist before writing to ensure that we add to the cache before processing can remove it
    // again.
    stagedCache.persist();

    // we need to retry the writing if the dispatcher return zero or negative position (this means
    // it was full during writing)
    // it will be freed from the LogStorageAppender concurrently, which means we might be able to
    // write later
    final var writeFuture =
        writeRetryStrategy.runWithRetry(
            () -> {
              LOG.trace("Write scheduled TaskResult to dispatcher!");
              if (recordBatch.isEmpty()) {
                return true;
              }

              return logStreamWriter
                  .tryWrite(WriteContext.scheduled(), recordBatch.entries())
                  .isRight();
            },
            abortCondition);

    writeFuture.onComplete(
        (v, t) -> {
          if (t != null) {
            stagedCache.rollback();
            // todo handle error;
            //   can happen if we tried to write a too big batch of records
            //   this should resolve if we use the buffered writer were we detect these errors
            //   earlier
            LOG.warn("Writing of scheduled TaskResult failed!", t);
          }
        });
  }

  /** Note: this class has a natural ordering that is inconsistent with equals. */
  private final class ScheduledTaskImpl
      implements ScheduledTask, Comparable<ScheduledTaskImpl>, Runnable {
    private final long scheduledTime;
    private final Runnable runnable;

    private ScheduledTaskImpl(final long scheduledTime, final Runnable runnable) {
      this.scheduledTime = scheduledTime;
      this.runnable = runnable;
    }

    @Override
    public void cancel() {
      actorControl.run(
          () -> {
            if (scheduledTasks.remove(this)) {
              metrics.decrementScheduledTasks();
            }
          });
    }

    @Override
    public int compareTo(final ProcessingScheduleServiceImpl.ScheduledTaskImpl o) {
      return Long.compare(scheduledTime, o.scheduledTime);
    }

    @Override
    public void run() {
      final long taskStart = clock.millis();
      metrics.observeScheduledTaskDelay(taskStart - scheduledTime);
      runnable.run();
      metrics.observeScheduledTaskDuration(clock.millis() - taskStart);
    }
  }
}
