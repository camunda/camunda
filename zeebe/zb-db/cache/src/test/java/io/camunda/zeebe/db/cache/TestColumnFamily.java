/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.protocol.EnumValue;

/** Declared out of value order, so tests can tell sorting by value from sorting by ordinal. */
enum TestColumnFamily implements EnumValue {
  SECOND(2),
  FIRST(1);

  private final int value;

  TestColumnFamily(final int value) {
    this.value = value;
  }

  @Override
  public int getValue() {
    return value;
  }
}
