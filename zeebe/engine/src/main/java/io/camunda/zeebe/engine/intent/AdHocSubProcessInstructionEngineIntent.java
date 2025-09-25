/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum AdHocSubProcessInstructionEngineIntent implements EngineIntent {
  ACTIVATE(0),
  ACTIVATED(1),
  COMPLETE(2),
  COMPLETED(3);

  private final short value;

  AdHocSubProcessInstructionEngineIntent(final int value) {
    this.value = (short) value;
  }

  public short getIntent() {
    return value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case ACTIVATED:
      case COMPLETED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return ACTIVATE;
      case 1:
        return ACTIVATED;
      case 2:
        return COMPLETE;
      case 3:
        return COMPLETED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }
}
