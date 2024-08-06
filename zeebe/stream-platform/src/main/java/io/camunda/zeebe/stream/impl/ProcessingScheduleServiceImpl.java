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
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
  private LogStreamWriter logStreamWriter;
  private ActorControl actorControl;
  private AbortableRetryStrategy writeRetryStrategy;
  private CompletableActorFuture<Void> openFuture;

  public ProcessingScheduleServiceImpl(
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<LogStreamWriter> writerSupplier,
      final StageableScheduledCommandCache commandCache) {
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerSupplier = writerSupplier;
    this.commandCache = commandCache;
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Runnable followUpTask) {
    if (actorControl == null) {
      LOG.warn("ProcessingScheduleService hasn't been opened yet, ignore scheduled task.");
      return NOOP_SCHEDULED_TASK;
    }
    final var scheduledTimer = actorControl.schedule(delay, followUpTask);
    return scheduledTimer::cancel;
  }

  @Override
  public ScheduledTask runDelayed(final Duration delay, final Task task) {
    return runDelayed(delay, toRunnable(task));
  }

  @Override
  public ScheduledTask runAt(final long timestamp, final Task task) {
    if (actorControl == null) {
      LOG.warn("ProcessingScheduleService hasn't been opened yet, ignore scheduled task.");
      return NOOP_SCHEDULED_TASK;
    }
    final var scheduledTimer = actorControl.runAt(timestamp, toRunnable(task));
    return scheduledTimer::cancel;
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
    return openFuture;
  }

  @Override
  public void close() {
    actorControl = null;
    logStreamWriter = null;
    writeRetryStrategy = null;
    openFuture = null;
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

      final var stagedCache = commandCache.stage();
      final var builder =
          new BufferedTaskResultBuilder(logStreamWriter::canWriteEvents, stagedCache);
      final var result = task.execute(builder);
      final var recordBatch = result.getRecordBatch();

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
            if (t == null) {
              stagedCache.persist();
            } else {
              // todo handle error;
              //   can happen if we tried to write a too big batch of records
              //   this should resolve if we use the buffered writer were we detect these errors
              // earlier
              LOG.warn("Writing of scheduled TaskResult failed!", t);
            }
          });
    };
  }
}
