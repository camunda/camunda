/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.SimpleProcessingScheduleService;
import io.camunda.zeebe.engine.api.Task;
import java.time.Duration;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final SimpleProcessingScheduleService processorActorService;
  private final SimpleProcessingScheduleService differentActorService;

  public ExtendedProcessingScheduleServiceImpl(
      final SimpleProcessingScheduleService processorActorService,
      final SimpleProcessingScheduleService differentActorService) {
    this.processorActorService = processorActorService;
    this.differentActorService = differentActorService;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    differentActorService.runAtFixedRate(delay, task);
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
