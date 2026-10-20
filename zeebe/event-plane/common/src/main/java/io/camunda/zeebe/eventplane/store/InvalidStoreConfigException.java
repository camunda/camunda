/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

/** A store configuration that lacks what its type needs. */
public final class InvalidStoreConfigException extends IllegalArgumentException {

  private final String setting;

  public InvalidStoreConfigException(
      final String setting, final String expected, final String got) {
    super("Expected %s %s, but got %s".formatted(setting, expected, got));
    this.setting = setting;
  }

  /** The setting that is wrong or missing. */
  public String setting() {
    return setting;
  }
}
