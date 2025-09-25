/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum TimerEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATED((short) 0),

  TRIGGER((short) 1),
  TRIGGERED((short) 2),

  /**
   * @deprecated for removal since 8.1.0, removal can only happen if we break backwards
   *     compatibility with older versions because Cancel command can still exist on log streams
   */
  @Deprecated
  CANCEL((short) 3),
  CANCELED((short) 4),

  MIGRATED((short) 5);

  private final short value;
  private final boolean shouldBanInstance;

  TimerEngineIntent(final short value) {
    this(value, true);
  }

  TimerEngineIntent(final short value, final boolean shouldBanInstance) {
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
      case TRIGGERED:
      case CANCELED:
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return TRIGGER;
      case 2:
        return TRIGGERED;
      case 3:
        return CANCEL;
      case 4:
        return CANCELED;
      case 5:
        return MIGRATED;
      default:
        return EngineIntent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
