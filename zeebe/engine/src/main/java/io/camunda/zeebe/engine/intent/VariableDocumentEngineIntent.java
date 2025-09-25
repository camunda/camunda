/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum VariableDocumentEngineIntent implements EngineIntent {
  UPDATE(0),
  UPDATED(1),
  UPDATING(2),
  UPDATE_DENIED(3);

  private final short value;

  VariableDocumentEngineIntent(final int value) {
    this((short) value);
  }

  VariableDocumentEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case UPDATED:
      case UPDATING:
      case UPDATE_DENIED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return UPDATE;
      case 1:
        return UPDATED;
      case 2:
        return UPDATING;
      case 3:
        return UPDATE_DENIED;
      default:
        return UNKNOWN;
    }
  }
}
