/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.shared.management.ActorClockService.MutableClock;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * An implementation of {@link ActorClockService} which wraps a mutable clock, allowing for
 * modification of the clock from the outside.
 *
 * <p>See {@link io.camunda.zeebe.shared.ActorClockConfiguration} on how to configure/wire it.
 */
public final class ControlledActorClockService implements ActorClockService, MutableClock {
  private final ControlledActorClock clock;

  public ControlledActorClockService(final ControlledActorClock clock) {
    this.clock = clock;
  }

  @Override
  public long epochMilli() {
    return clock.getTimeMillis();
  }

  @Override
  public Optional<MutableClock> mutable() {
    return Optional.of(this);
  }

  @Override
  public void addTime(final Duration offset) {
    clock.addTime(offset);
  }

  @Override
  public void pinTime(final Instant time) {
    clock.setCurrentTime(time);
  }

  @Override
  public void resetTime() {
    clock.reset();
  }
}
