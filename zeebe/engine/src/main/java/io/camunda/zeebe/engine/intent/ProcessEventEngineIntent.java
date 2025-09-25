/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessEventEngineIntent implements ProcessInstanceRelatedEngineIntent {
  TRIGGERING((short) 0),
  TRIGGERED((short) 1);

  private final short value;

  ProcessEventEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return true;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return TRIGGERING;
      case 1:
        return TRIGGERED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return true;
  }
}
