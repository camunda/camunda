/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledFuture;

public abstract class AbstractScheduledService {
  protected ThreadPoolTaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

  public boolean isScheduledToRun() {
    return this.scheduledTrigger != null;
  }

  protected abstract void run();

  protected abstract Trigger getScheduleTrigger();

  protected synchronized boolean startScheduling() {
    boolean wasScheduled = false;
    if (this.taskScheduler == null) {
      this.taskScheduler = new ThreadPoolTaskScheduler();
      this.taskScheduler.initialize();
      wasScheduled = true;
    }
    if (this.scheduledTrigger == null) {
      this.scheduledTrigger = this.taskScheduler.schedule(this::run, getScheduleTrigger());
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
