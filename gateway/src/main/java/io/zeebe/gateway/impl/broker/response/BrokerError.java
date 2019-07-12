/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.response;

import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.util.buffer.BufferUtil;

public class BrokerError {

  private final ErrorCode code;
  private final String message;

  public BrokerError(ErrorResponse errorResponse) {
    this(errorResponse.getErrorCode(), BufferUtil.bufferAsString(errorResponse.getErrorData()));
  }

  public BrokerError(ErrorCode code, String message) {
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
