/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum BatchOperationExecutionEngineIntent implements EngineIntent {
  EXECUTE((short) 0),
  EXECUTING((short) 1),
  EXECUTED((short) 2);

  private final short value;

  BatchOperationExecutionEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return EXECUTE;
      case 1:
        return EXECUTING;
      case 2:
        return EXECUTED;

      default:
        return EngineIntent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case EXECUTING:
      case EXECUTED:
        return true;
      default:
        return false;
    }
  }
}
