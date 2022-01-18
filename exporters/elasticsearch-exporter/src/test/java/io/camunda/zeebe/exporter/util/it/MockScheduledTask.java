/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util.it;

import java.time.Duration;

/**
 * Represents a single scheduled task through {@link MockController#scheduleTask(Duration,
 * Runnable)}. A call to its {@link #run()} method will only execute the underlying task once.
 */
public class MockScheduledTask implements Runnable {

  private final Duration delay;
  private final Runnable task;
  private boolean executed;
  private boolean canceled;

  MockScheduledTask(final Duration delay, final Runnable task) {
    this.delay = delay;
    this.task = task;
    executed = false;
    canceled = false;
  }

  public Duration getDelay() {
    return delay;
  }

  @Override
  public void run() {
    if (!wasExecuted() && !isCanceled()) {
      task.run();
      executed = true;
    }
  }

  public boolean wasExecuted() {
    return executed;
  }

  public void cancel() {
    if (!isCanceled()) {
      canceled = true;
    }
  }

  public boolean isCanceled() {
    return canceled;
  }
}
