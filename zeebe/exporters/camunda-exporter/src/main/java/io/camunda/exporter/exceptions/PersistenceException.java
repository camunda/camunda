/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.exceptions;

public class PersistenceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PersistenceException() {}

  public PersistenceException(final String message) {
    super(message);
  }

  public PersistenceException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public PersistenceException(final Throwable cause) {
    super(cause);
  }
}
