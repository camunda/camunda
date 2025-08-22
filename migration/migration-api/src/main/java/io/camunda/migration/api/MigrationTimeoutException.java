/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.api;

public class MigrationTimeoutException extends RuntimeException {

  private final boolean ignore;

  public MigrationTimeoutException(final String message, final boolean ignore) {
    super(message);
    this.ignore = ignore;
  }

  public boolean isIgnore() {
    return ignore;
  }
}
