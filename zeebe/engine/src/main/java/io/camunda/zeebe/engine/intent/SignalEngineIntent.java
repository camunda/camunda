/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum SignalEngineIntent implements EngineIntent {
  BROADCAST((short) 0),
  BROADCASTED((short) 1);

  private final short value;

  SignalEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case BROADCASTED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return BROADCAST;
      case 1:
        return BROADCASTED;
      default:
        return UNKNOWN;
    }
  }
}
