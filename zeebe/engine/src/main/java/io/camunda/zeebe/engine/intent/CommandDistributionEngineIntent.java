/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

public enum CommandDistributionEngineIntent implements EngineIntent {
  STARTED(0),
  DISTRIBUTING(1),
  ACKNOWLEDGE(2),
  ACKNOWLEDGED(3),
  FINISHED(4),
  ENQUEUED(5),
  CONTINUATION_REQUESTED(6),
  CONTINUED(7),
  FINISH(8),
  CONTINUE(9);

  private final short value;

  CommandDistributionEngineIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case STARTED:
      case DISTRIBUTING:
      case ACKNOWLEDGED:
      case FINISHED:
      case ENQUEUED:
      case CONTINUATION_REQUESTED:
      case CONTINUED:
        return true;
      default:
        return false;
    }
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return STARTED;
      case 1:
        return DISTRIBUTING;
      case 2:
        return ACKNOWLEDGE;
      case 3:
        return ACKNOWLEDGED;
      case 4:
        return FINISHED;
      case 5:
        return ENQUEUED;
      case 6:
        return CONTINUATION_REQUESTED;
      case 7:
        return CONTINUED;
      case 8:
        return FINISH;
      case 9:
        return CONTINUE;
      default:
        return UNKNOWN;
    }
  }
}
