/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.clock;

import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.camunda.feel.FeelEngineClock;

public final class ZeebeFeelEngineClock implements FeelEngineClock {

  private final InstantSource clock;

  public ZeebeFeelEngineClock(final InstantSource clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public ZonedDateTime getCurrentTime() {
    return clock.instant().atZone(ZoneId.systemDefault());
  }
}
