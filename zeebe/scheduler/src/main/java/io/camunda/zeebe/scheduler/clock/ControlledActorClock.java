/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.clock;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** For testcases */
public final class ControlledActorClock implements ActorClock {

  private static final Logger LOG = LoggerFactory.getLogger(ControlledActorClock.class);

  private volatile long currentTime;
  private volatile long currentOffset;
  private volatile long updatedTime;
  private volatile boolean isModified;

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
    isModified = true;
  }

  public void reset() {
    currentTime = -1;
    currentOffset = 0;
    updatedTime = System.currentTimeMillis();
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

  private boolean usesPointInTime() {
    return currentTime > 0;
  }

  @Override
  public boolean update() {
    if (usesPointInTime()) {
      updatedTime = currentTime;
    } else {
      updatedTime = System.currentTimeMillis() + currentOffset;
    }
    if (isModified) {
      LOG.debug("Traveled through time to {}", Instant.ofEpochMilli(updatedTime));
      isModified = false;
    }
    return true;
  }

  @Override
  public long getTimeMillis() {
    return updatedTime;
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
