/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final SimpleProcessingScheduleService processorActorService;
  private final SimpleProcessingScheduleService differentActorService;
  private final ActorControl differentActor;

  public ExtendedProcessingScheduleServiceImpl(
      final SimpleProcessingScheduleService processorActorService,
      final SimpleProcessingScheduleService differentActorService,
      final ActorControl differentActor) {
    this.processorActorService = processorActorService;
    this.differentActorService = differentActorService;
    this.differentActor = differentActor;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    differentActor.call(
        () -> {
          // we must run in different actor in order to schedule task
          differentActorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable task) {
    processorActorService.runDelayed(delay, task);
  }

  @Override
  public void runDelayed(final Duration delay, final Task task) {
    processorActorService.runDelayed(delay, task);
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    processorActorService.runAtFixedRate(delay, task);
  }
}
