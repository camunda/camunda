/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ResourceDeletionEngineIntent implements EngineIntent {
  DELETE(0),
  DELETING(1),
  DELETED(2);

  private final short value;

  ResourceDeletionEngineIntent(final int value) {
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
      case DELETING:
      case DELETED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return DELETE;
      case 1:
        return DELETING;
      case 2:
        return DELETED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }
}
