/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.protocol.EnumValue;

/** A column family for foreign keys in tests, without the RocksDB module's test enums. */
enum TestColumnFamily implements EnumValue {
  TARGET(7);

  private final int value;

  TestColumnFamily(final int value) {
    this.value = value;
  }

  @Override
  public int getValue() {
    return value;
  }
}
