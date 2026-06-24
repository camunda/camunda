/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.camunda.feel.FeelEngineClock;

public class TestFeelEngineClock implements FeelEngineClock {

  private Instant currentTime = null;

  @Override
  public ZonedDateTime getCurrentTime() {
    return currentTime.atZone(ZoneId.systemDefault());
  }

  public void setCurrentTime(final Instant currentTime) {
    this.currentTime = currentTime;
  }
}
