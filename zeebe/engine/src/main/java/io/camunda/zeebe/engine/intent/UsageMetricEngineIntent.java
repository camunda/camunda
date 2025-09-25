/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum UsageMetricEngineIntent implements EngineIntent {
  EXPORT(0),
  EXPORTED(1);

  private final short value;

  UsageMetricEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return this != UsageMetricEngineIntent.EXPORT;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return EXPORT;
      case 1:
        return EXPORTED;
      default:
        return UNKNOWN;
    }
  }
}
