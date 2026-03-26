/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.coordinator;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateRequestDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Parsed coordination request. Extracts the request type and value payload from the SBE envelope in
 * one pass.
 *
 * <p>var request = CoordinateRequest.from(requestBytes); switch (request.type()) { case HEARTBEAT →
 * handleHeartbeat(request.value()); case JOIN_GROUP → handleJoinGroup(request.value()); case L →
 * handleJoinGroup(request.value()); } </pre>
 */
record CoordinationRequest(CoordinateRequestType type, DirectBuffer value) {

  static CoordinationRequest from(final byte[] requestBytes) {
    final var headerDecoder = new MessageHeaderDecoder();
    final var bodyDecoder = new ExecuteCoordinateRequestDecoder();

    final var buffer = new UnsafeBuffer(requestBytes);
    bodyDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

    final var type = bodyDecoder.type();

    final var valueOffset =
        headerDecoder.encodedLength()
            + bodyDecoder.sbeBlockLength()
            + ExecuteCoordinateRequestDecoder.valueHeaderLength();
    final var valueLength = bodyDecoder.valueLength();

    final DirectBuffer value;
    if (valueLength > 0) {
      value = new UnsafeBuffer(requestBytes, valueOffset, valueLength);
    } else {
      value = new UnsafeBuffer(0, 0);
    }

    return new CoordinationRequest(type, value);
  }
}
