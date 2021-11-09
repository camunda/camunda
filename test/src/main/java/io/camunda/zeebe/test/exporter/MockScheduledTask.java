/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter;

import java.time.Duration;

/**
 * Represents a single scheduled task through {@link MockController#scheduleTask(Duration,
 * Runnable)}. A call to its {@link #run()} method will only execute the underlying task once.
 *
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockScheduledTask implements Runnable {

  private Duration delay;
  private Runnable task;
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

  public void setDelay(final Duration delay) {
    this.delay = delay;
  }

  public Runnable getTask() {
    return task;
  }

  public void setTask(final Runnable task) {
    this.task = task;
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
