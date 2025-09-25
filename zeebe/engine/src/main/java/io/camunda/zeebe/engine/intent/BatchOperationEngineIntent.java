/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum BatchOperationEngineIntent implements EngineIntent {
  CREATE((short) 0),
  CREATED((short) 1),
  INITIALIZE((short) 2),
  INITIALIZING((short) 3),
  FINISH_INITIALIZATION((short) 4),
  INITIALIZED((short) 5),
  FAIL((short) 6),
  FAILED((short) 7),
  CANCEL((short) 8),
  CANCELED((short) 9),
  SUSPEND((short) 10),
  SUSPENDED((short) 11),
  RESUME((short) 12),
  RESUMED((short) 13),
  COMPLETE((short) 14),
  COMPLETED((short) 15),
  COMPLETE_PARTITION((short) 16),
  PARTITION_COMPLETED((short) 17),
  FAIL_PARTITION((short) 18),
  PARTITION_FAILED((short) 19);

  private final short value;

  BatchOperationEngineIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return INITIALIZE;
      case 3:
        return INITIALIZING;
      case 4:
        return FINISH_INITIALIZATION;
      case 5:
        return INITIALIZED;
      case 6:
        return FAIL;
      case 7:
        return FAILED;
      case 8:
        return CANCEL;
      case 9:
        return CANCELED;
      case 10:
        return SUSPEND;
      case 11:
        return SUSPENDED;
      case 12:
        return RESUME;
      case 13:
        return RESUMED;
      case 14:
        return COMPLETE;
      case 15:
        return COMPLETED;
      case 16:
        return COMPLETE_PARTITION;
      case 17:
        return PARTITION_COMPLETED;
      case 18:
        return FAIL_PARTITION;
      case 19:
        return PARTITION_FAILED;
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
      case INITIALIZED:
      case FAILED:
      case SUSPENDED:
      case CANCELED:
      case RESUMED:
      case COMPLETED:
      case PARTITION_COMPLETED:
      case PARTITION_FAILED:
      case INITIALIZING:
        return true;
      default:
        return false;
    }
  }
}
