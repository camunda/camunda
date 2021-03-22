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
import java.util.concurrent.atomic.AtomicLong;

/** For testcases */
public final class ControlledActorClock implements ActorClock {
  private final ActorClock delegate = new DefaultActorClock();
  private final AtomicLong currentTime = new AtomicLong();
  private final AtomicLong currentOffset = new AtomicLong();

  public ControlledActorClock() {
    reset();
  }

  public void pinCurrentTime() {
    setCurrentTime(getCurrentTime());
  }

  public void addTime(final Duration durationToAdd) {
    final long fixedTime = currentTime.get();

    if (fixedTime > 0) {
      currentTime.addAndGet(durationToAdd.toMillis());
    } else {
      currentOffset.addAndGet(durationToAdd.toMillis());
    }
  }

  public void reset() {
    currentTime.set(-1);
    currentOffset.set(0);
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

  @Override
  public boolean update() {
    return delegate.update();
  }

  @Override
  public long getTimeMillis() {
    final long fixedTime = currentTime.get();

    if (fixedTime > 0) {
      return fixedTime;
    } else {
      return delegate.getTimeMillis() + currentOffset.get();
    }
  }

  @Override
  public long getNanosSinceLastMillisecond() {
    return delegate.getNanosSinceLastMillisecond();
  }

  @Override
  public long getNanoTime() {
    return delegate.getNanoTime();
  }

  public long getCurrentTimeInMillis() {
    return getTimeMillis();
  }
}
