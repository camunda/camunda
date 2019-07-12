/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.RemoteAddress;
import org.agrona.DirectBuffer;

public class ClientResponseImpl implements ClientResponse {
  private final RemoteAddress remoteAddres;
  private final long requestId;
  private final DirectBuffer responseBuffer;

  public ClientResponseImpl(IncomingResponse incomingResponse, RemoteAddress remoteAddress) {
    this.remoteAddres = remoteAddress;
    this.requestId = incomingResponse.getRequestId();
    this.responseBuffer = incomingResponse.getResponseBuffer();
  }

  @Override
  public RemoteAddress getRemoteAddress() {
    return remoteAddres;
  }

  @Override
  public long getRequestId() {
    return requestId;
  }

  @Override
  public DirectBuffer getResponseBuffer() {
    return responseBuffer;
  }
}
