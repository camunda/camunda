/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum MessageStartEventSubscriptionEngineIntent implements EngineIntent {
  CREATED((short) 0),
  CORRELATED((short) 1),
  DELETED((short) 2);

  private final short value;

  MessageStartEventSubscriptionEngineIntent(final short value) {
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
        return CREATED;
      case 1:
        return CORRELATED;
      case 2:
        return DELETED;
      default:
        return UNKNOWN;
    }
  }
}
