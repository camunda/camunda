/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import java.util.concurrent.TimeUnit;

public final class TimerSubscription implements ActorSubscription, ScheduledTimer, Runnable {
  private final ActorJob job;
  private final ActorTask task;
  private final TimeUnit timeUnit;
  private final long deadline;
  private final boolean isRecurring;
  private volatile boolean isDone = false;
  private volatile boolean isCanceled = false;
  private long timerId = -1L;
  private ActorThread thread;

  public TimerSubscription(
      final ActorJob job, final long deadline, final TimeUnit timeUnit, final boolean isRecurring) {
    this.job = job;
    task = job.getTask();
    this.timeUnit = timeUnit;
    this.deadline = deadline;
    this.isRecurring = isRecurring;
  }

  @Override
  public boolean poll() {
    return isDone;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return isRecurring;
  }

  @Override
  public void onJobCompleted() {

    if (isRecurring && !isCanceled) {
      isDone = false;
      submit();
    }
  }

  @Override
  public void cancel() {
    if (!isCanceled && (!isDone || isRecurring)) {
      task.onSubscriptionCancelled(this);
      isCanceled = true;
      final ActorThread current = ActorThread.current();

      if (current != thread) {
        thread.submittedCallbacks.add(this);
      } else {
        run();
      }
    }
  }

  public long getTimerId() {
    return timerId;
  }

  public void setTimerId(final long timerId) {
    this.timerId = timerId;
  }

  public void submit() {
    thread = ActorThread.current();
    thread.scheduleTimer(this);
  }

  public long getDeadline() {
    return deadline;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  public void onTimerExpired(final TimeUnit timeUnit, final long now) {
    if (!isCanceled) {
      isDone = true;
      task.tryWakeup();
    }
  }

  @Override
  public void run() {
    thread.removeTimer(this);
  }
}
