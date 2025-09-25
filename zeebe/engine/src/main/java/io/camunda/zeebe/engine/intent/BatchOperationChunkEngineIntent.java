/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum BatchOperationChunkEngineIntent implements EngineIntent {
  CREATE((short) 0),
  CREATED((short) 1);

  private final short value;

  BatchOperationChunkEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;

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
      case CREATED:
        return true;
      default:
        return false;
    }
  }
}
