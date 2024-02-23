/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;

public class ExtendedProcessingScheduleServiceImpl implements ProcessingScheduleService {

  private final SimpleProcessingScheduleService processorActorService;
  private final SimpleProcessingScheduleService asyncActorService;
  private final ConcurrencyControl concurrencyControl;
  private final boolean alwaysAsync;

  public ExtendedProcessingScheduleServiceImpl(
      final SimpleProcessingScheduleService processorActorService,
      final SimpleProcessingScheduleService asyncActorService,
      final ConcurrencyControl concurrencyControl,
      final boolean alwaysAsync) {
    this.processorActorService = processorActorService;
    this.asyncActorService = asyncActorService;
    this.concurrencyControl = concurrencyControl;
    this.alwaysAsync = alwaysAsync;
  }

  @Override
  public void runAtFixedRateAsync(final Duration delay, final Task task) {
    concurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          asyncActorService.runAtFixedRate(delay, task);
        });
  }

  @Override
  public void runDelayedAsync(final Duration delay, final Task task) {
    concurrencyControl.run(
        () -> {
          // we must run in different actor in order to schedule task
          asyncActorService.runDelayed(delay, task);
        });
  }

  @Override
  public void runDelayed(final Duration delay, final Runnable task) {
    if (alwaysAsync) {
      concurrencyControl.run(
          () -> {
            // we must run in different actor in order to schedule task
            asyncActorService.runDelayed(delay, task);
          });
    } else {
      processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public void runDelayed(final Duration delay, final Task task) {
    if (alwaysAsync) {
      runDelayedAsync(delay, task);
    } else {
      processorActorService.runDelayed(delay, task);
    }
  }

  @Override
  public void runAtFixedRate(final Duration delay, final Task task) {
    if (alwaysAsync) {
      runAtFixedRateAsync(delay, task);
    } else {
      processorActorService.runAtFixedRate(delay, task);
    }
  }
}
