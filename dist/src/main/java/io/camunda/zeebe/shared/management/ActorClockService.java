/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A service which wraps an {@link io.camunda.zeebe.util.sched.clock.ActorClock} instance. We can't
 * directly use an actor clock since the interface is by nature immutable, so instead we wrap it.
 */
@FunctionalInterface
public interface ActorClockService {

  /** @return the current instant of the clock */
  long epochMilli();

  /** @return a mutable variant of the underlying clock, or nothing if the clock is immutable */
  default Optional<MutableClock> mutable() {
    return Optional.empty();
  }

  /** A mutable variant of this service. */
  interface MutableClock {
    /**
     * Adds the offset to the current clock.
     *
     * @param offset the time offset to add to the current time
     */
    void addTime(final Duration offset);

    /**
     * Pins the clock to the given time.
     *
     * @param time the time at which to pin the current clock
     */
    void pinTime(final Instant time);

    /** Resets the clock to use the system time */
    void resetTime();
  }
}
