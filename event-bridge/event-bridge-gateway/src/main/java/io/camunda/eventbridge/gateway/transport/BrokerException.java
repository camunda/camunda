/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.transport;

import io.camunda.eventbridge.core.protocol.ErrorCode;

/**
 * Thrown by {@link BrokerRequestRouter} when a broker returns an application-level error code (i.e.
 * {@link ErrorCode} != {@code NONE}) or when all routing attempts are exhausted.
 */
public final class BrokerException extends RuntimeException {

  private final ErrorCode errorCode;

  public BrokerException(final ErrorCode errorCode, final String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public BrokerException(final ErrorCode errorCode, final String message, final Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /** The SBE error code returned by the broker (never {@link ErrorCode#NONE}). */
  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
