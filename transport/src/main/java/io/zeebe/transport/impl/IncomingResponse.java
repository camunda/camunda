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

  public IncomingResponse(long requestId, DirectBuffer responseBuffer) {
    this.requestId = requestId;
    this.responseBuffer = responseBuffer;
  }

  public long getRequestId() {
    return requestId;
  }

  public DirectBuffer getResponseBuffer() {
    return responseBuffer;
  }
}
