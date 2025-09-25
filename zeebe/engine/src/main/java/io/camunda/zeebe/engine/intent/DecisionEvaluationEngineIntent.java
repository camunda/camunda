/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum DecisionEvaluationEngineIntent implements EngineIntent {
  EVALUATED(0),
  FAILED(1),
  EVALUATE(2);

  private final short value;

  DecisionEvaluationEngineIntent(final int value) {
    this.value = (short) value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return EVALUATED;
      case 1:
        return FAILED;
      case 2:
        return EVALUATE;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case EVALUATED:
      case FAILED:
        return true;
      default:
        return false;
    }
  }
}
