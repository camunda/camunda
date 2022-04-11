/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledFuture;

public abstract class AbstractScheduledService {
  protected ThreadPoolTaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

  public synchronized boolean isScheduledToRun() {
    return this.scheduledTrigger != null;
  }

  protected String getName() {
    return getClass().getSimpleName() + "-";
  }

  protected abstract void run();

  protected abstract Trigger createScheduleTrigger();

  protected synchronized boolean startScheduling() {
    boolean wasScheduled = false;
    if (this.taskScheduler == null) {
      this.taskScheduler = new ThreadPoolTaskScheduler();
      this.taskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
      this.taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      this.taskScheduler.setThreadNamePrefix(getName());
      this.taskScheduler.initialize();
      wasScheduled = true;
    }
    if (this.scheduledTrigger == null) {
      this.scheduledTrigger = this.taskScheduler.schedule(this::run, createScheduleTrigger());
    }
    return wasScheduled;
  }

  protected synchronized void stopScheduling() {
    if (scheduledTrigger != null) {
      this.scheduledTrigger.cancel(true);
      this.scheduledTrigger = null;
    }
    if (this.taskScheduler != null) {
      this.taskScheduler.destroy();
      this.taskScheduler = null;
    }
  }

}
