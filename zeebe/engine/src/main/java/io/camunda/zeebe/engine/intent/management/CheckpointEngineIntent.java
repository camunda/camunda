/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent.management;

import io.camunda.zeebe.engine.intent.EngineIntent;

public enum CheckpointEngineIntent implements EngineIntent {
  CREATE(0),
  CREATED(1),
  IGNORED(2),
  CONFIRM_BACKUP(3),
  CONFIRMED_BACKUP(4);

  private final short value;

  CheckpointEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case IGNORED:
      case CONFIRMED_BACKUP:
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
        return IGNORED;
      case 3:
        return CONFIRM_BACKUP;
      case 4:
        return CONFIRMED_BACKUP;
      default:
        return UNKNOWN;
    }
  }
}
