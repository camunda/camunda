/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ResourceEngineIntent implements EngineIntent {
  CREATED((short) 0),
  DELETED((short) 1),
  FETCH((short) 2),
  FETCHED((short) 3);

  private final short value;

  ResourceEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return DELETED;
      case 2:
        return FETCH;
      case 3:
        return FETCHED;
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
      case DELETED:
      case FETCHED:
        return true;
      default:
        return false;
    }
  }
}
