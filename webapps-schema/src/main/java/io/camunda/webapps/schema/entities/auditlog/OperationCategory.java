/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

public enum OperationCategory {
  ADMIN(0),
  OPERATOR(1),
  USER_TASK(2);

  private final short value;

  OperationCategory(final int value) {
    this.value = (short) value;
  }

  public short getValue() {
    return value;
  }

  public static OperationCategory fromValue(final short value) {
    switch (value) {
      case 0:
        return ADMIN;
      case 1:
        return OPERATOR;
      case 2:
        return USER_TASK;
      default:
        throw new IllegalArgumentException("Unknown Operation Category value: " + value);
    }
  }
}
