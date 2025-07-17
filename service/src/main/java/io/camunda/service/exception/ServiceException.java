/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

public class ServiceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Status status;

  public ServiceException(final ServiceError error) {
    super(error.message());
    status = error.status();
  }

  public ServiceException(final String message, final Status status) {
    super(message);
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }

  public enum Status {
    DEADLINE_EXCEEDED,
    INVALID_ARGUMENT,
    FORBIDDEN,
    UNAUTHORIZED,
    UNAVAILABLE,
    RESOURCE_EXHAUSTED,
    ABORTED,
    INTERNAL,
    NOT_FOUND,
    ALREADY_EXISTS,
    INVALID_STATE,
    UNKNOWN
  }
}
