/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum MessageSubscriptionEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATE((short) 0),
  CREATED((short) 1),

  CORRELATING((short) 8),
  CORRELATE((short) 2),
  CORRELATED((short) 3),

  REJECT((short) 4),
  REJECTED((short) 5),

  DELETE((short) 6),
  DELETED((short) 7),

  MIGRATE((short) 9),
  MIGRATED((short) 10);

  private final short value;
  private final boolean shouldBanInstance;

  MessageSubscriptionEngineIntent(final short value) {
    this(value, true);
  }

  MessageSubscriptionEngineIntent(final short value, final boolean shouldBanInstance) {
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
      case CORRELATING:
      case CORRELATED:
      case REJECTED:
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
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return CORRELATE;
      case 3:
        return CORRELATED;
      case 4:
        return REJECT;
      case 5:
        return REJECTED;
      case 6:
        return DELETE;
      case 7:
        return DELETED;
      case 8:
        return CORRELATING;
      case 9:
        return MIGRATE;
      case 10:
        return MIGRATED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}
