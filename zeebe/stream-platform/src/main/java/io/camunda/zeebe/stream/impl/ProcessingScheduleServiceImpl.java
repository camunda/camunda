/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.util.Unit.unit;
import static java.util.Objects.requireNonNull;

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
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
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
  private @Nullable LogStreamWriter logStreamWriter;
  private @Nullable ActorControl actorControl;
  private @Nullable AbortableRetryStrategy writeRetryStrategy;
  private @Nullable CompletableActorFuture<Void> openFuture;

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
    return schedule(actorControl, clock.millis() + delay.toMillis(), task);
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
    return schedule(actorControl, timestamp, task);
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
    openFuture.complete(unit());
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
      requireNonNull(actorControl).submit(expiredTask);
    }
  }

  // actorControl is the same as the field, but guaranteed to be non-null
  private ScheduledTask schedule(
      final ActorControl actorControl, final long timestamp, final Runnable task) {
    final var scheduledTask = new ScheduledTaskImpl(timestamp, task);
    actorControl.run(
        () -> {
          metrics.incrementScheduledTasks();
          final var delay = timestamp - clock.millis();
          scheduledTasks.add(scheduledTask);
          if (delay < interval / 2) {
            requireNonNull(actorControl)
                .schedule(Duration.ofMillis(delay), this::processScheduledTasks);
          }
        });
    return scheduledTask;
  }

  Runnable guardRunnable(final Runnable task) {
    return () -> {
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

      task.run();
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
        requireNonNull(actorControl).submit(toRunnable(task));
        return;
      }

      final var stagedCache = commandCache.stage();
      final var builder =
          new BufferedTaskResultBuilder(
              requireNonNull(logStreamWriter)::canWriteEvents, stagedCache);
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
          requireNonNull(writeRetryStrategy)
              .runWithRetry(
                  () -> {
                    LOG.trace("Write scheduled TaskResult to dispatcher!");
                    if (recordBatch.isEmpty()) {
                      return true;
                    }

                    return requireNonNull(logStreamWriter)
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
    };
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
      requireNonNull(actorControl)
          .run(
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
