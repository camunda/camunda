/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.clock;

import io.camunda.zeebe.scheduler.ActorThread;
import java.time.Instant;
import java.time.InstantSource;

public interface ActorClock extends InstantSource {
  boolean update();

  long getTimeMillis();

  long getNanosSinceLastMillisecond();

  long getNanoTime();

  static ActorClock current() {
    final ActorThread current = ActorThread.current();
    return current != null ? current.getClock() : null;
  }

  static long currentTimeMillis() {
    final ActorClock clock = current();
    return clock != null ? clock.getTimeMillis() : System.currentTimeMillis();
  }

  static Instant currentInstant() {
    final ActorClock clock = current();
    return clock != null ? clock.instant() : Instant.now();
  }

  @Override
  default Instant instant() {
    return Instant.ofEpochMilli(getTimeMillis());
  }

  @Override
  default long millis() {
    return getTimeMillis();
  }
}
