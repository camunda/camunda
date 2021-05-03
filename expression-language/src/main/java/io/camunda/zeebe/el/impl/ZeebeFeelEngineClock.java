/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el.impl;

import io.zeebe.util.sched.clock.ActorClock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.camunda.feel.FeelEngineClock;

public final class ZeebeFeelEngineClock implements FeelEngineClock {

  private final ActorClock clock;

  public ZeebeFeelEngineClock(final ActorClock clock) {
    this.clock = clock;
  }

  @Override
  public ZonedDateTime getCurrentTime() {

    final long currentMillis = clock.getTimeMillis();
    final var instant = Instant.ofEpochMilli(currentMillis);
    final var zone = ZoneId.systemDefault();

    return instant.atZone(zone);
  }
}
