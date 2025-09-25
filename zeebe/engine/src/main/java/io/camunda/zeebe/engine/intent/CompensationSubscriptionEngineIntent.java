/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum CompensationSubscriptionEngineIntent implements EngineIntent {
  CREATED((short) 0),
  TRIGGERED((short) 1),
  COMPLETED((short) 2),
  DELETED((short) 3),
  MIGRATED((short) 4);

  private final short value;

  CompensationSubscriptionEngineIntent(final short value) {
    this.value = value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return TRIGGERED;
      case 2:
        return COMPLETED;
      case 3:
        return DELETED;
      case 4:
        return MIGRATED;
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
    return true;
  }
}
