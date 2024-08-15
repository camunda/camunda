/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.exceptions;

public class PersistenceException extends Exception {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("checkstyle:MutableException")
  private Integer failingRequestId;

  public PersistenceException() {}

  public PersistenceException(final String message) {
    super(message);
  }

  public PersistenceException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public PersistenceException(
      final String message, final Throwable cause, final Integer failingRequestId) {
    super(message, cause);
    this.failingRequestId = failingRequestId;
  }

  public PersistenceException(final Throwable cause) {
    super(cause);
  }

  public Integer getFailingRequestId() {
    return failingRequestId;
  }

  public void setFailingRequestId(final Integer failingRequestId) {
    this.failingRequestId = failingRequestId;
  }
}
