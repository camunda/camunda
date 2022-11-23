/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Here the implementation is just a suggestion to amke the engine abstraction work. Can be whatever
 * PDT team thinks is best to work with
 */
public class ProcessingScheduleServiceImpl implements ProcessingScheduleService, AutoCloseable {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private final Supplier<StreamProcessor.Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final Supplier<ActorFuture<LogStreamBatchWriter>> writerAsyncSupplier;
  private LogStreamBatchWriter logStreamBatchWriter;
  private ActorControl actorControl;
  private CompletableActorFuture<Void> openFuture;

  // this flag ensures that we don't overlap multiple scheduled tasks; if a tasks is in the process
  // of being written out, then new tasks are buffered until that's finished
  private boolean isWriting;

  public ProcessingScheduleServiceImpl(
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final Supplier<ActorFuture<LogStreamBatchWriter>> writerAsyncSupplier) {
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.writerAsyncSupplier = writerAsyncSupplier;
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    useActorControl(() -> actorControl.runDelayed(delay, followUpTask));
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

  private void useActorControl(final Runnable task) {
    if (actorControl == null) {
      LOG.debug("ProcessingScheduleService hasn't been opened yet, ignore scheduled task.");
      return;
    }
    task.run();
  }

  public ActorFuture<Void> open(final ActorControl control) {
    if (openFuture != null) {
      return openFuture;
    }

    openFuture = new CompletableActorFuture<>();
    final var writerFuture = writerAsyncSupplier.get();
    control.runOnCompletion(
        writerFuture,
        (writer, failure) -> {
          if (failure == null) {
            logStreamBatchWriter = writer;
            actorControl = control;
            openFuture.complete(null);
          } else {
            openFuture.completeExceptionally(failure);
          }
        });
    return openFuture;
  }

  @Override
  public void close() {
    actorControl = null;
    logStreamBatchWriter = null;
    openFuture = null;
  }

  Runnable toRunnable(final Task task) {
    return () -> {
      if (abortCondition.getAsBoolean()) {
        // it might be that we are closing, then we should just stop
        return;
      }

      final var currentStreamProcessorPhase = streamProcessorPhaseSupplier.get();
      if (currentStreamProcessorPhase != Phase.PROCESSING || isWriting) {

        // We want to execute the scheduled tasks only if the StreamProcessor is in the PROCESSING
        // PHASE
        //
        // To make sure that:
        //
        //  * we are not running during replay/init phase (the state might not be up-to-date yet)
        //  * we are not running during suspending
        //
        LOG.trace(
            "Not able to execute scheduled task right now. [streamProcessorPhase: {}, isWriting: {}]",
            currentStreamProcessorPhase,
            isWriting);
        actorControl.submit(toRunnable(task));
        return;
      }
      final var builder =
          new BufferedTaskResultBuilder(logStreamBatchWriter::canWriteAdditionalEvent);
      final var result = task.execute(builder);

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

      // no need to check the abortCondition in the callback as the callback is not executed if
      // close was requested
      final ActorFuture<Long> writeFuture = actorControl.createFuture();

      // ensure we don't overlap new writes until we're done with this write batch
      isWriting = true;
      writeRecords(writeFuture);
      writeFuture.onComplete(
          (v, t) -> {
            isWriting = false;

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

  private void writeRecords(final ActorFuture<Long> result) {
    actorControl.runOnCompletion(
        logStreamBatchWriter.tryWrite(),
        (position, error) -> {
          if (error != null) {
            // we need to retry the writing if the dispatcher return zero or negative position
            // (this means it was full during writing). it will be freed from the
            // LogStorageAppender concurrently, which means we might be able to write later
            if (error instanceof RecoverableException) {
              LOG.trace("Failed to write records, retrying...", error);
              actorControl.run(() -> writeRecords(result));
              actorControl.yieldThread();
            } else {
              result.completeExceptionally(error);
            }
          } else {
            result.complete(position);
          }
        });
  }
}
