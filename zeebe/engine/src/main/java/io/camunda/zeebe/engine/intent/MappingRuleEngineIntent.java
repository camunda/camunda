/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

import java.util.Arrays;

public enum MappingRuleEngineIntent implements EngineIntent {
  CREATE(0),
  CREATED(1),
  DELETE(2),
  DELETED(3),
  UPDATE(4),
  UPDATED(5);

  private final short value;

  MappingRuleEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case DELETED:
      case UPDATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    return Arrays.stream(values())
        .filter(m -> m.value() == value)
        .findFirst()
        .map(EngineIntent.class::cast)
        .orElse(EngineIntent.UNKNOWN);
  }
}
