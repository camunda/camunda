/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum ProcessInstanceMigrationEngineIntent implements EngineIntent,
    ProcessInstanceRelatedEngineIntent {
  MIGRATE((short) 0),
  MIGRATED((short) 1);

  private final short value;

  ProcessInstanceMigrationEngineIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return MIGRATE;
      case 1:
        return MIGRATED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    // Process Instance Migration has transactional error handling. No need to ban the instance.
    return false;
  }
}
