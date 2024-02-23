/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db;

public record ConsistencyChecksSettings(
    boolean enablePreconditions, boolean enableForeignKeyChecks) {
  private static final boolean DEFAULT_ENABLE_PRECONDITIONS = false;
  private static final boolean DEFAULT_ENABLE_FOREIGN_KEY_CHECKS = false;

  /** Intended for tests, uses the default settings for all consistency checks. */
  public ConsistencyChecksSettings() {
    this(DEFAULT_ENABLE_PRECONDITIONS, DEFAULT_ENABLE_FOREIGN_KEY_CHECKS);
  }
}
