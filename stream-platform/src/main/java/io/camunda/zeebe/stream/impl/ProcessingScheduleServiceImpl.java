/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Implementation that uses an {@link ActorControl} to schedule and execute all tasks. */
public class ProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;
  private final Supplier<StreamProcessor.Phase> streamProcessorPhaseSupplier;
  private final BooleanSupplier abortCondition;
  private final LogStreamWriter logStreamWriter;
  private final ActorControl actorControl;
  private final AbortableRetryStrategy writeRetryStrategy;

  public ProcessingScheduleServiceImpl(
      final Supplier<Phase> streamProcessorPhaseSupplier,
      final BooleanSupplier abortCondition,
      final ActorControl actorControl,
      final LogStreamWriter logStreamWriter) {
    this.streamProcessorPhaseSupplier = streamProcessorPhaseSupplier;
    this.abortCondition = abortCondition;
    this.actorControl = actorControl;
    this.logStreamWriter = logStreamWriter;
    this.writeRetryStrategy = new AbortableRetryStrategy(actorControl);
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    actorControl.run(() -> actorControl.schedule(delay, followUpTask));
  }

  @Override
  public void runDelayed(final Duration delay, final Task task) {
    actorControl.run(() -> runDelayed(delay, toRunnable(task)));
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
      final var builder = new BufferedTaskResultBuilder(logStreamWriter::canWriteEvents);
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
                return logStreamWriter.tryWrite(recordBatch.entries()) >= 0;
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
