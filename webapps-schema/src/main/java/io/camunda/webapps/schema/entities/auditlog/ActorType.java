/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

public enum ActorType {
  USER(0),
  CLIENT(1);

  private final short value;

  ActorType(final int value) {
    this.value = (short) value;
  }

  public short getValue() {
    return value;
  }

  public static ActorType fromValue(final short value) {
    switch (value) {
      case 0:
        return USER;
      case 1:
        return CLIENT;
      default:
        throw new IllegalArgumentException("Unknown Audit Log Actor Type value: " + value);
    }
  }
}
