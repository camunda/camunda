/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessInstanceModificationEngineIntent implements EngineIntent,
    ProcessInstanceRelatedEngineIntent {
  MODIFY((short) 0),
  MODIFIED((short) 1);

  private final short value;

  ProcessInstanceModificationEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case MODIFIED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return true;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return MODIFY;
      case 1:
        return MODIFIED;
      default:
        return UNKNOWN;
    }
  }
}
