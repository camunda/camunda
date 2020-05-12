/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import org.agrona.DirectBuffer;

public class IncomingResponse {
  private final long requestId;
  private final DirectBuffer responseBuffer;
  private final Exception exception;

  public IncomingResponse(
      final long requestId, final DirectBuffer responseBuffer, final Exception exception) {
    this.requestId = requestId;
    this.responseBuffer = responseBuffer;
    this.exception = exception;
  }

  public long getRequestId() {
    return requestId;
  }

  public DirectBuffer getResponseBuffer() {
    return responseBuffer;
  }

  public Exception getException() {
    return exception;
  }
}
