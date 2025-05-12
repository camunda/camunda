/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** For testcases */
public final class ControlledActorClock implements ActorClock {
  private final AtomicLong currentTime = new AtomicLong();
  private final AtomicLong currentOffset = new AtomicLong();
  private final AtomicLong updatedTime = new AtomicLong();

  public ControlledActorClock() {
    reset();
  }

  public void pinCurrentTime() {
    setCurrentTime(getCurrentTime());
  }

  public void addTime(final Duration durationToAdd) {
    if (usesPointInTime()) {
      currentTime.addAndGet(durationToAdd.toMillis());
    } else {
      currentOffset.addAndGet(durationToAdd.toMillis());
    }
  }

  public void reset() {
    currentTime.set(-1);
    currentOffset.set(0);
    updatedTime.set(System.currentTimeMillis());
  }

  public Instant getCurrentTime() {
    return Instant.ofEpochMilli(getTimeMillis());
  }

  public void setCurrentTime(final long currentTime) {
    this.currentTime.set(currentTime);
  }

  public void setCurrentTime(final Instant currentTime) {
    this.currentTime.set(currentTime.toEpochMilli());
  }

  private boolean usesPointInTime() {
    return currentTime.get() > 0;
  }

  @Override
  public boolean update() {
    if (usesPointInTime()) {
      updatedTime.set(currentTime.get());
    } else {
      updatedTime.set(System.currentTimeMillis() + currentOffset.get());
    }
    return true;
  }

  @Override
  public long getTimeMillis() {
    return updatedTime.get();
  }

  @Override
  public long getNanosSinceLastMillisecond() {
    return 0;
  }

  @Override
  public long getNanoTime() {
    return 0;
  }

  public long getCurrentTimeInMillis() {
    return getTimeMillis();
  }
}
