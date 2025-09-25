/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum IncidentEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATED((short) 0),

  RESOLVE((short) 1, false),
  RESOLVED((short) 2),
  MIGRATED((short) 3, false);

  private final short value;
  private final boolean shouldBanInstance;

  IncidentEngineIntent(final short value) {
    this(value, true);
  }

  IncidentEngineIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return RESOLVE;
      case 2:
        return RESOLVED;
      case 3:
        return MIGRATED;
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
      case CREATED:
      case RESOLVED:
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
