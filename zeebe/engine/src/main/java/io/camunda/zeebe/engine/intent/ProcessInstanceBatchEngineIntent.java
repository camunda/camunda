/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessInstanceBatchEngineIntent implements ProcessInstanceRelatedEngineIntent {
  TERMINATE(0),
  ACTIVATE(1),
  TERMINATED(2),
  ACTIVATED(3);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceBatchEngineIntent(final int value) {
    this(value, true);
  }

  ProcessInstanceBatchEngineIntent(final int value, final boolean shouldBanInstance) {
    this.value = (short) value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return TERMINATE;
      case 1:
        return ACTIVATE;
      case 2:
        return TERMINATED;
      case 3:
        return ACTIVATED;
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
      case TERMINATED:
      case ACTIVATED:
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
