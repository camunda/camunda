/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.LegacyTask;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedCommandWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;

public class ProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final ActorControl actorControl;
  private final LegacyTypedCommandWriter commandWriter;

  public ProcessingScheduleServiceImpl(
      final ActorControl actorControl, final LegacyTypedCommandWriter legacyTypedCommandWriter) {
    this.actorControl = actorControl;
    commandWriter = legacyTypedCommandWriter;
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    scheduleOnActor(() -> actorControl.runDelayed(delay, followUpTask));
  }

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> precedingTask, final BiConsumer<T, Throwable> followUpTask) {
    scheduleOnActor(() -> actorControl.runOnCompletion(precedingTask, followUpTask));
  }

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> precedingTask, final LegacyTask followUpTask) {
    runOnCompletion(
        precedingTask,
        (BiConsumer<T, Throwable>)
            (ok, err) -> {
              followUpTask.run(commandWriter, this);
            });
  }

  @Override
  public void runDelayed(final Duration delay, final LegacyTask followUpTask) {
    runDelayed(
        delay,
        () -> {
          followUpTask.run(commandWriter, this);
        });
  }

  private void scheduleOnActor(final Runnable task) {
    actorControl.submit(task);
  }
}
