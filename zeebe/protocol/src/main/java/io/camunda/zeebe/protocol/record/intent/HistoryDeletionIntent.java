/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.intent;

public enum HistoryDeletionIntent implements Intent {
  DELETE(0),
  DELETED(1);

  private final short value;

  HistoryDeletionIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case DELETED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return DELETE;
      case 1:
        return DELETED;
      default:
        return Intent.UNKNOWN;
    }
  }
}
