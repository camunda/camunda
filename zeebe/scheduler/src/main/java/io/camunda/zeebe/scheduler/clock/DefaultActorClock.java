/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.clock;

/** Default actor clock implementation; reads the wall clock once per {@link #update()}. */
public final class DefaultActorClock implements ActorClock {
  private long timeMillis = System.currentTimeMillis();

  @Override
  public boolean update() {
    final long now = System.currentTimeMillis();
    final boolean changed = now != timeMillis;
    timeMillis = now;
    return changed;
  }

  @Override
  public long getTimeMillis() {
    return timeMillis;
  }
}
