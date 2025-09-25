/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessInstanceCreationEngineIntent implements EngineIntent,
    ProcessInstanceRelatedEngineIntent {
  CREATE(0, false),
  CREATED(1, true),
  CREATE_WITH_AWAITING_RESULT(2, false);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceCreationEngineIntent(final int value, final boolean shouldBanInstance) {
    this((short) value, shouldBanInstance);
  }

  ProcessInstanceCreationEngineIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
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

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return CREATE_WITH_AWAITING_RESULT;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
