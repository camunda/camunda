/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessMessageSubscriptionEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATING((short) 0),
  CREATE((short) 1),
  CREATED((short) 2),

  CORRELATE((short) 3),
  CORRELATED((short) 4),

  DELETING((short) 5),
  DELETE((short) 6),
  DELETED((short) 7),

  MIGRATED((short) 8);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessMessageSubscriptionEngineIntent(final short value) {
    this(value, true);
  }

  ProcessMessageSubscriptionEngineIntent(final short value, final boolean shouldBanInstance) {
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
      case CREATING:
      case CREATED:
      case CORRELATED:
      case DELETING:
      case DELETED:
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATING;
      case 1:
        return CREATE;
      case 2:
        return CREATED;
      case 3:
        return CORRELATE;
      case 4:
        return CORRELATED;
      case 5:
        return DELETING;
      case 6:
        return DELETE;
      case 7:
        return DELETED;
      case 8:
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
