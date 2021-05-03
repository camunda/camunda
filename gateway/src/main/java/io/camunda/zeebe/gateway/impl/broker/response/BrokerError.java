/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.response;

import io.camunda.zeebe.protocol.impl.encoding.ErrorResponse;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class BrokerError {

  private final ErrorCode code;
  private final String message;

  public BrokerError(final ErrorResponse errorResponse) {
    this(errorResponse.getErrorCode(), BufferUtil.bufferAsString(errorResponse.getErrorData()));
  }

  public BrokerError(final ErrorCode code, final String message) {
    this.code = code;
    this.message = message;
  }

  public ErrorCode getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "BrokerError{" + "code=" + code + ", message='" + message + '\'' + '}';
  }
}
