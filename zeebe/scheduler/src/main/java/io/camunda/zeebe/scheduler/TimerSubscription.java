/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.clock.ActorClock;
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

  long getDeadline(ActorClock now);

  void onTimerExpired(TimeUnit timeUnit, long now);

  @Override
  void run();

  long getTimerExpiredAt();
}
