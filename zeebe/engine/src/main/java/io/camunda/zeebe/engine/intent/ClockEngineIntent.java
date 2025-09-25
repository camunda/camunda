/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ClockEngineIntent implements EngineIntent {
  PIN((short) 0, false),
  PINNED((short) 1, true),
  RESET((short) 2, false),
  RESETTED((short) 3, true);

  private final short value;
  private final boolean isEvent;

  ClockEngineIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return PIN;
      case 1:
        return PINNED;
      case 2:
        return RESET;
      case 3:
        return RESETTED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }
}
