/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Here the implementation is just a suggestion to amke the engine abstraction work. Can be whatever
 * PDT team thinks is best to work with
 */
public class ProcessingScheduleServiceImpl extends Actor implements ProcessingScheduleService {
  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private AbortableRetryStrategy writeRetryStrategy;
  private final Supplier<StreamProcessor.Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<ActorFuture<LogStreamBatchWriter>> writerAsyncSupplier;
  private LogStreamBatchWriter logStreamBatchWriter;
  private final String actorName;

  public ProcessingScheduleServiceImpl(
      final String name,
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<ActorFuture<LogStreamBatchWriter>> writerAsyncSupplier) {

    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerAsyncSupplier = writerAsyncSupplier;
    actorName = name;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    writeRetryStrategy = new AbortableRetryStrategy(actor);
    final var writerFuture = writerAsyncSupplier.get();
    actor.runOnCompletionBlockingCurrentPhase(
        writerFuture,
        (writer, failure) -> {
          if (failure == null) {
            logStreamBatchWriter = writer;
          } else {
            actor.fail(failure);
          }
        });
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed processing schedule service {}.", getName());
  }

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> precedingTask, final BiConsumer<T, Throwable> followUpTask) {
    scheduleOnActor(() -> actor.runOnCompletion(precedingTask, followUpTask));
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    scheduleOnActor(() -> actor.runDelayed(delay, followUpTask));
  }

  @Override
  public void runDelayed(final Duration delay, final Task task) {
    runDelayed(delay, toRunnable(task));
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

  private void scheduleOnActor(final Runnable task) {
    actor.submit(task);
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
        actor.submit(toRunnable(task));
        return;
      }
      final var builder =
          new DirectTaskResultBuilder(logStreamBatchWriter::canWriteAdditionalEvent);
      final var result = task.execute(builder);

      // we need to retry the writing if the dispatcher return zero or negative position (this means
      // it was full during writing)
      // it will be freed from the LogStorageAppender concurrently, which means we might be able to
      // write later
      final var writeFuture =
          writeRetryStrategy.runWithRetry(
              () -> {
                LOG.trace("Write scheduled TaskResult to dispatcher!");
                logStreamBatchWriter.reset();
                result
                    .getRecordBatch()
                    .forEach(
                        entry ->
                            logStreamBatchWriter
                                .event()
                                .key(entry.key())
                                .metadataWriter(entry.recordMetadata())
                                .sourceIndex(entry.sourceIndex())
                                .valueWriter(entry.recordValue())
                                .done());

                return logStreamBatchWriter.tryWrite() >= 0;
              },
              abortCondition);

      writeFuture.onComplete(
          (v, t) -> {
            if (t != null) {
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
