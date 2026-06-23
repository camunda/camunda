/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.fetch;

import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.util.function.Consumer;

/**
 * A data container representing a parked long-polling fetch request.
 *
 * <p><b>Performance Design:</b>
 *
 * <ul>
 *   <li>Implements {@link Runnable} to allow direct, zero-allocation scheduling by the Zeebe Actor.
 *   <li>Delegates and pre-calculates target thresholds to provide O(1) access to evaluation
 *       metrics.
 * </ul>
 */
public final class DelayedFetch implements Runnable {

  private final FetchTask task;
  private final long parkedAtBytesSnapshot;
  private final long requiredByteDelta;

  private final Consumer<DelayedFetch> timeoutCallback;
  private ScheduledTimer timeoutTask;

  public DelayedFetch(
      final FetchTask task,
      final long parkedAtBytesSnapshot,
      final long requiredByteDelta,
      final Consumer<DelayedFetch> timeoutCallback) {
    this.task = task;
    this.parkedAtBytesSnapshot = parkedAtBytesSnapshot;
    this.requiredByteDelta = requiredByteDelta;
    this.timeoutCallback = timeoutCallback;
  }

  public FetchTask task() {
    return task;
  }

  public long targetOffset() {
    return task.offset();
  }

  public long targetBytes() {
    return parkedAtBytesSnapshot + requiredByteDelta;
  }

  public boolean isCancelled() {
    return task.isCancelled();
  }

  public void setTimeoutTask(final ScheduledTimer timeoutTask) {
    this.timeoutTask = timeoutTask;
  }

  public void cancelTimer() {
    if (timeoutTask != null) {
      timeoutTask.cancel();
      timeoutTask = null;
    }
  }

  @Override
  public void run() {
    timeoutCallback.accept(this);
  }
}
