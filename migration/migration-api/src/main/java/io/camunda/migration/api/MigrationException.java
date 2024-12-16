/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.api;

import java.io.Serial;

public class MigrationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 845931450938L;

  public MigrationException(final String message) {
    super(message);
  }

  public MigrationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
