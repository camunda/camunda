/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.protocol.EnumValue;

/**
 * Contains only one column family {@link #DEFAULT}, which can be used for tests or simple
 * databases.
 */
public enum DefaultColumnFamily implements EnumValue {
  DEFAULT(0);

  private final int value;

  DefaultColumnFamily(final int value) {
    this.value = value;
  }

  @Override
  public int getValue() {
    return value;
  }
}
