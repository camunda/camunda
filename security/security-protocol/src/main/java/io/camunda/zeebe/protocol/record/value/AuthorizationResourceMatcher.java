/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

public enum AuthorizationResourceMatcher {
  UNSPECIFIED(0),
  ANY(1),
  ID(2);

  private final short value;

  AuthorizationResourceMatcher(final int value) {
    this.value = (short) value;
  }

  public short getIntent() {
    return value;
  }

  public static AuthorizationResourceMatcher from(final short value) {
    switch (value) {
      case 0:
        return UNSPECIFIED;
      case 1:
        return ANY;
      case 2:
        return ID;
      default:
        return UNSPECIFIED;
    }
  }

  public short value() {
    return value;
  }
}
