/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.exceptions;

public class OperateFatalException extends RuntimeException {

  public OperateFatalException() {}

  public OperateFatalException(final String message) {
    super(message);
  }

  public OperateFatalException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public OperateFatalException(final Throwable cause) {
    super(cause);
  }
}
