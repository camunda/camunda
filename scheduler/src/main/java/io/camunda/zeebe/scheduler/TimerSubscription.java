/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import java.util.concurrent.TimeUnit;

public interface TimerSubscription extends ActorSubscription, ScheduledTimer, Runnable {

  @Override
  boolean poll();

  @Override
  ActorJob getJob();

  @Override
  boolean isRecurring();

  @Override
  void onJobCompleted();

  @Override
  void cancel();

  long getTimerId();

  void setTimerId(long timerId);

  void submit();

  long getDeadline();

  void onTimerExpired(TimeUnit timeUnit, long now);

  @Override
  void run();

  long getTimerExpiredAt();
}
