/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.clock;

/**
 * Default actor clock implementation; minimizes calls to {@link System#currentTimeMillis()} to once
 * per millisecond.
 */
public final class DefaultActorClock implements ActorClock {
  long timeMillis;

  long nanoTime;

  long nanoTimeOfLastMilli;

  long nanosSinceLastMilli;

  @Override
  public boolean update() {
    updateNanos();

    if (nanosSinceLastMilli >= 1_000_000) {
      timeMillis = System.currentTimeMillis();
      nanoTimeOfLastMilli = nanoTime;
      return true;
    }

    return false;
  }

  @Override
  public long getTimeMillis() {
    return timeMillis;
  }

  @Override
  public long getNanosSinceLastMillisecond() {
    return nanosSinceLastMilli;
  }

  @Override
  public long getNanoTime() {
    return nanoTime;
  }

  private void updateNanos() {
    nanoTime = System.nanoTime();
    nanosSinceLastMilli = nanoTime - nanoTimeOfLastMilli;
  }
}
