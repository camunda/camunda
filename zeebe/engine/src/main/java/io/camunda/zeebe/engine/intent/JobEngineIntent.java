/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum JobEngineIntent implements ProcessInstanceRelatedEngineIntent {
  CREATED((short) 0),

  COMPLETE((short) 1, false),
  COMPLETED((short) 2),

  TIME_OUT((short) 3),
  TIMED_OUT((short) 4),

  FAIL((short) 5, false),
  FAILED((short) 6),

  UPDATE_RETRIES((short) 7, false),
  RETRIES_UPDATED((short) 8),

  /**
   * @deprecated for removal since 8.0.2, removal can only happen if we break backwards
   *     compatibility with older versions because Cancel command can still exist on log streams
   */
  @Deprecated
  CANCEL((short) 9),
  CANCELED((short) 10),

  THROW_ERROR((short) 11, false),
  ERROR_THROWN((short) 12),
  RECUR_AFTER_BACKOFF((short) 13),
  RECURRED_AFTER_BACKOFF((short) 14),

  YIELD((short) 15),
  YIELDED((short) 16),

  UPDATE_TIMEOUT((short) 17),
  TIMEOUT_UPDATED((short) 18),

  MIGRATED((short) 19),

  UPDATE((short) 20),
  UPDATED((short) 21);

  private final short value;
  private final boolean shouldBanInstance;

  JobEngineIntent(final short value) {
    this(value, true);
  }

  JobEngineIntent(final short value, final boolean shouldBanInstance) {
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
        return COMPLETE;
      case 2:
        return COMPLETED;
      case 3:
        return TIME_OUT;
      case 4:
        return TIMED_OUT;
      case 5:
        return FAIL;
      case 6:
        return FAILED;
      case 7:
        return UPDATE_RETRIES;
      case 8:
        return RETRIES_UPDATED;
      case 9:
        return CANCEL;
      case 10:
        return CANCELED;
      case 11:
        return THROW_ERROR;
      case 12:
        return ERROR_THROWN;
      case 13:
        return RECUR_AFTER_BACKOFF;
      case 14:
        return RECURRED_AFTER_BACKOFF;
      case 15:
        return YIELD;
      case 16:
        return YIELDED;
      case 17:
        return UPDATE_TIMEOUT;
      case 18:
        return TIMEOUT_UPDATED;
      case 19:
        return MIGRATED;
      case 20:
        return UPDATE;
      case 21:
        return UPDATED;
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
      case CREATED:
      case COMPLETED:
      case TIMED_OUT:
      case FAILED:
      case RETRIES_UPDATED:
      case CANCELED:
      case ERROR_THROWN:
      case RECURRED_AFTER_BACKOFF:
      case YIELDED:
      case TIMEOUT_UPDATED:
      case MIGRATED:
      case UPDATED:
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
