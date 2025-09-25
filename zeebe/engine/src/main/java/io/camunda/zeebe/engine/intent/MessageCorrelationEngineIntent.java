/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum MessageCorrelationEngineIntent implements EngineIntent {
  CORRELATE(1),
  CORRELATING(2),
  CORRELATED(3),
  NOT_CORRELATED(4);

  private final short value;

  MessageCorrelationEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CORRELATING:
      case CORRELATED:
      case NOT_CORRELATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 1:
        return CORRELATE;
      case 2:
        return CORRELATING;
      case 3:
        return CORRELATED;
      case 4:
        return NOT_CORRELATED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }
}
