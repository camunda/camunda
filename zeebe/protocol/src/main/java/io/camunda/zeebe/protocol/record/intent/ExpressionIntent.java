/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.intent;

public enum ExpressionIntent implements Intent {
  EVALUATE(0),
  EVALUATED(1),
  REJECTED(2);

  private final short value;

  ExpressionIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return EVALUATE;
      case 1:
        return EVALUATED;
      case 2:
        return REJECTED;
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
      case EVALUATE:
        return false;
      default:
        return true;
    }
  }
}
