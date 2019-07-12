/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.exporter;

import java.time.Duration;

/**
 * Represents a single scheduled task through {@link MockController#scheduleTask(Duration,
 * Runnable)}. A call to its {@link #run()} method will only execute the underlying task once.
 */
public class MockScheduledTask implements Runnable {

  private Duration delay;
  private Runnable task;
  private boolean executed;

  MockScheduledTask(Duration delay, Runnable task) {
    this.delay = delay;
    this.task = task;
    this.executed = false;
  }

  public Duration getDelay() {
    return delay;
  }

  public void setDelay(Duration delay) {
    this.delay = delay;
  }

  public Runnable getTask() {
    return task;
  }

  public void setTask(Runnable task) {
    this.task = task;
  }

  @Override
  public void run() {
    if (!wasExecuted()) {
      task.run();
      executed = true;
    }
  }

  public boolean wasExecuted() {
    return executed;
  }
}
