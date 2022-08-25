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
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Here the implementation is just a suggestion to amke the engine abstraction work. Can be whatever
 * PDT team thinks is best to work with
 */
public class ProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final ActorControl actorControl;
  private final StreamProcessorContext streamProcessorContext;
  private final AbortableRetryStrategy writeRetryStrategy;

  // todo remove context from CTOR; will be cleaned up after engine abstraction is done
  public ProcessingScheduleServiceImpl(final StreamProcessorContext streamProcessorContext) {
    actorControl = streamProcessorContext.getActor();
    this.streamProcessorContext = streamProcessorContext;
    writeRetryStrategy = new AbortableRetryStrategy(actorControl);
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    scheduleOnActor(() -> actorControl.runDelayed(delay, followUpTask));
  }

  @Override
  public void runDelayed(final Duration delay, final Task task) {
    runDelayed(delay, toRunnable(task));
  }

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> precedingTask, final BiConsumer<T, Throwable> followUpTask) {
    scheduleOnActor(() -> actorControl.runOnCompletion(precedingTask, followUpTask));
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    /* TODO preliminary implementation; with the direct access
     * this only works because this class is scheduled on the same actor as the
     * stream processor.
     */
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
    actorControl.submit(task);
  }

  Runnable toRunnable(final Task task) {
    return () -> {
      if (streamProcessorContext.getAbortCondition().getAsBoolean()) {
        // it might be that we are closing, then we should just stop
        return;
      }

      final var currentStreamProcessorPhase = streamProcessorContext.getStreamProcessorPhase();
      if (currentStreamProcessorPhase != Phase.PROCESSING
          || streamProcessorContext.isInProcessing()) {

        // We want to execute the scheduled tasks only if the StreamProcessor is in the PROCESSING
        // PHASE, but no processing is currently happening.
        //
        // To make sure that:
        //
        //  * we are not running during replay/init phase (the state might not be up-to-date yet)
        //  * we are not running during suspending
        //  * we are not interfering with the current ongoing processing,
        //    such that all transaction changes are available during our task execution
        Loggers.PROCESS_PROCESSOR_LOGGER.trace(
            "Not able to execute scheduled task right now. [streamProcessorPhase: {}, inProcessing: {}]",
            currentStreamProcessorPhase,
            streamProcessorContext.isInProcessing());
        actorControl.submit(toRunnable(task));
        return;
      }
      final var logStreamBatchWriter = streamProcessorContext.getLogStreamBatchWriter();
      final var builder =
          new DirectTaskResultBuilder(
              streamProcessorContext, logStreamBatchWriter::canWriteAdditionalEvent);
      final var result = task.execute(builder);

      // we need to retry the writing if the dispatcher return zero or negative position (this means
      // it was full during writing)
      // it will be freed from the LogStorageAppender concurrently, which means we might be able to
      // write later
      final var writeFuture =
          writeRetryStrategy.runWithRetry(
              () -> {
                Loggers.PROCESS_PROCESSOR_LOGGER.trace("Write scheduled TaskResult to dispatcher!");
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
              streamProcessorContext.getAbortCondition());

      writeFuture.onComplete(
          (v, t) -> {
            if (t != null) {
              // todo handle error;
              //   can happen if we tried to write a too big batch of records
              //   this should resolve if we use the buffered writer were we detect these errors
              // earlier
              Loggers.PROCESS_PROCESSOR_LOGGER.warn("Writing of scheduled TaskResult failed!", t);
            }
          });
    };
  }
}
