/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.util;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import org.agrona.DirectBuffer;

public class EchoRequestResponseHandler implements ServerRequestHandler {

  protected final ServerResponse response = new ServerResponse();

  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    response
        .reset()
        .buffer(buffer, offset, length)
        .requestId(requestId)
        .remoteStreamId(remoteAddress.getStreamId());
    return output.sendResponse(response);
  }
}
