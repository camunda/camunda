/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.commandapi;

import io.camunda.zeebe.protocol.record.ErrorCode;

public final class ErrorResponseException extends RuntimeException {

  private final ErrorResponse errorResponse;

  public ErrorResponseException(final ErrorResponse errorResponse) {
    super(
        String.format(
            "Unexpected error from broker (code: '%s'): %s",
            errorResponse.getErrorCode().name(), errorResponse.getErrorData()));
    this.errorResponse = errorResponse;
  }

  public ErrorResponse getErrorResponse() {
    return errorResponse;
  }

  public ErrorCode getErrorCode() {
    return errorResponse.getErrorCode();
  }

  public String getErrorMessage() {
    return errorResponse.getErrorData();
  }
}
