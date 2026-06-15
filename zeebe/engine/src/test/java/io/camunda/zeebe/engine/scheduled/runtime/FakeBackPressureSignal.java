/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

/** Toggleable {@link BackPressureSignal} for tests. */
public final class FakeBackPressureSignal implements BackPressureSignal {
  private volatile boolean underBackPressure;

  public void setUnderBackPressure(final boolean value) {
    underBackPressure = value;
  }

  @Override
  public boolean isUnderBackPressure() {
    return underBackPressure;
  }
}
