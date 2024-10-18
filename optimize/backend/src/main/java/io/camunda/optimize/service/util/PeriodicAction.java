/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static java.lang.String.format;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public class PeriodicAction {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(PeriodicAction.class);
  private final ScheduledExecutorService executorService;
  private final String actionName;
  private final Runnable onSchedule;

  public PeriodicAction(final String actionName, final Runnable onSchedule) {
    this.actionName = actionName;
    this.onSchedule = onSchedule;
    executorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat(actionName + "-progress-%d").build());
  }

  public void start() {
    log.debug(format("Scheduling periodic action %s", actionName));
    executorService.scheduleAtFixedRate(onSchedule, 0, 30, TimeUnit.SECONDS);
  }

  public void stop() {
    try {
      log.debug(format("Stopping periodic action %s", actionName));
      executorService.shutdownNow();
    } catch (final Exception e) {
      log.error(format("Failed to stop periodic action %s", actionName));
    }
  }
}
