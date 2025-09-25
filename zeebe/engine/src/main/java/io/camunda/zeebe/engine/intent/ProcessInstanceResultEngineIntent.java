/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessInstanceResultEngineIntent implements EngineIntent,
    ProcessInstanceRelatedEngineIntent {
  COMPLETED(0, false);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceResultEngineIntent(final int value, final boolean shouldBanInstance) {
    this((short) value, shouldBanInstance);
  }

  ProcessInstanceResultEngineIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
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
        return COMPLETED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
