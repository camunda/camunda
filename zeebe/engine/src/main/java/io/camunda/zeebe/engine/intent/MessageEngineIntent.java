/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum MessageEngineIntent implements EngineIntent {
  PUBLISH((short) 0),
  PUBLISHED((short) 1),

  EXPIRE((short) 2),
  EXPIRED((short) 3);

  private final short value;

  MessageEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case PUBLISHED:
      case EXPIRED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return PUBLISH;
      case 1:
        return PUBLISHED;
      case 2:
        return EXPIRE;
      case 3:
        return EXPIRED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }
}
