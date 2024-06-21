/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractScheduledService {

  protected ThreadPoolTaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

  public synchronized boolean isScheduledToRun() {
    return scheduledTrigger != null;
  }

  protected String getName() {
    return getClass().getSimpleName() + "-";
  }

  protected abstract void run();

  protected abstract Trigger createScheduleTrigger();

  protected synchronized boolean startScheduling() {
    boolean wasScheduled = false;
    if (taskScheduler == null) {
      taskScheduler = new ThreadPoolTaskScheduler();
      taskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
      taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      taskScheduler.setThreadNamePrefix(getName());
      taskScheduler.initialize();
      wasScheduled = true;
    }
    if (scheduledTrigger == null) {
      scheduledTrigger = taskScheduler.schedule(this::run, createScheduleTrigger());
    }
    return wasScheduled;
  }

  protected synchronized void stopScheduling() {
    if (scheduledTrigger != null) {
      scheduledTrigger.cancel(true);
      scheduledTrigger = null;
    }
    if (taskScheduler != null) {
      taskScheduler.destroy();
      taskScheduler = null;
    }
  }
}
