/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.exception;

import java.util.Optional;

public class CamundaSearchException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Reason reason;

  public CamundaSearchException(final String message) {
    this(message, Reason.UNKNOWN);
  }

  public CamundaSearchException(final Throwable cause) {
    this(cause, Reason.UNKNOWN);
  }

  public CamundaSearchException(final String message, final Throwable cause) {
    this(message, cause, Reason.UNKNOWN);
  }

  public CamundaSearchException(final String message, final Reason reason) {
    super(message);
    this.reason = Optional.ofNullable(reason).orElse(Reason.UNKNOWN);
  }

  public CamundaSearchException(final Throwable cause, final Reason reason) {
    super(cause);
    this.reason = Optional.ofNullable(reason).orElse(Reason.UNKNOWN);
  }

  public CamundaSearchException(final String message, final Throwable cause, final Reason reason) {
    super(message, cause);
    this.reason = Optional.ofNullable(reason).orElse(Reason.UNKNOWN);
  }

  public Reason getReason() {
    return reason;
  }

  public enum Reason {
    NOT_FOUND,
    NOT_UNIQUE,
    CONNECTION_FAILED,
    SEARCH_CLIENT_FAILED,
    SEARCH_SERVER_FAILED,
    UNKNOWN
  }
}
