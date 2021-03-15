/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.clock;

import java.time.Duration;
import java.time.Instant;

/** For testcases */
public final class ControlledActorClock implements ActorClock {
  private volatile long currentTime;
  private volatile long currentOffset;

  public ControlledActorClock() {
    reset();
  }

  public void pinCurrentTime() {
    setCurrentTime(getCurrentTime());
  }

  public void addTime(final Duration durationToAdd) {
    if (usesPointInTime()) {
      currentTime += durationToAdd.toMillis();
    } else {
      currentOffset += durationToAdd.toMillis();
    }
  }

  public void reset() {
    currentTime = -1;
    currentOffset = 0;
  }

  public Instant getCurrentTime() {
    return Instant.ofEpochMilli(getTimeMillis());
  }

  public void setCurrentTime(final long currentTime) {
    this.currentTime = currentTime;
  }

  public void setCurrentTime(final Instant currentTime) {
    this.currentTime = currentTime.toEpochMilli();
  }

  protected boolean usesPointInTime() {
    return currentTime > 0;
  }

  protected boolean usesOffset() {
    return currentOffset > 0;
  }

  @Override
  public boolean update() {
    return true;
  }

  @Override
  public long getTimeMillis() {
    if (usesPointInTime()) {
      return currentTime;
    } else {
      long now = System.currentTimeMillis();
      if (usesOffset()) {
        now = now + currentOffset;
      }
      return now;
    }
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
