/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Here the implementation is just a suggestion to amke the engine abstraction work. Can be whatever
 * PDT team thinks is best to work with
 */
public class ProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final ActorControl actorControl;
  private final StreamProcessorContext streamProcessorContext;

  public ProcessingScheduleServiceImpl(final StreamProcessorContext streamProcessorContext) {
    actorControl = streamProcessorContext.getActor();
    this.streamProcessorContext = streamProcessorContext;
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
    runAtFixedRate(delay, toRunnable(task));
  }

  private void scheduleOnActor(final Runnable task) {
    actorControl.submit(task);
  }

  Runnable toRunnable(final Task task) {
    return () -> {
      final var builder = new DirectTaskResultBuilder(streamProcessorContext);
      final var result = task.execute(builder);
      result.writeRecordsToStream(streamProcessorContext.getLogStreamBatchWriter());
    };
  }
}
