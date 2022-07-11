/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.function.BiConsumer;

public class ProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final ActorControl actorControl;

  public ProcessingScheduleServiceImpl(final ActorControl actorControl) {
    this.actorControl = actorControl;
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable followUpTask) {
    scheduleOnActor(() -> actorControl.runDelayed(delay, followUpTask));
  }

  @Override
  public <T> void runOnSuccess(
      final ActorFuture<T> precedingTask, final BiConsumer<T, Throwable> followUpTask) {
    scheduleOnActor(() -> actorControl.runOnCompletion(precedingTask, followUpTask));
  }

  @Override
  public ActorFuture<Void> call(final Runnable action) {
    return actorControl.call(action);
  }

  private void scheduleOnActor(final Runnable task) {
    actorControl.submit(task);
  }
}
