/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.transport;

import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

public final class CoordinationResponseEncoder {

  private CoordinationResponseEncoder() {}

  public static byte[] encodeJoinGroup(final BufferWriter response) {
    return encode(response);
  }

  public static byte[] encodeLeaveGroup(final BufferWriter response) {
    return encode(response);
  }

  public static byte[] encodeHeartbeat(final BufferWriter response) {
    return encode(response);
  }

  public static byte[] encodeCommit(final BufferWriter response) {
    return encode(response);
  }

  /**
   * Frames an already-serialized response value (e.g. the bytes a stream's response writer produced
   * after a command committed) into the {@code ExecuteCoordinateResponse} envelope the gateway's
   * broker client decodes. Stream-backed handlers (join/leave/commit, topic admin) return raw value
   * bytes, so they must be wrapped here just like the {@link #encode(BufferWriter)} path does for
   * in-memory responses.
   */
  public static byte[] encodeValue(final byte[] value) {
    final var valueBuffer = new UnsafeBuffer(value);

    final var totalLength =
        MessageHeaderEncoder.ENCODED_LENGTH
            + ExecuteCoordinateResponseEncoder.BLOCK_LENGTH
            + ExecuteCoordinateResponseEncoder.valueHeaderLength()
            + value.length;

    final var bytes = new byte[totalLength];
    final var buffer = new UnsafeBuffer(bytes);

    final var headerEncoder = new MessageHeaderEncoder();
    final var bodyEncoder = new ExecuteCoordinateResponseEncoder();

    bodyEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder).putValue(valueBuffer, 0, value.length);

    return bytes;
  }

  public static byte[] encode(final BufferWriter response) {
    final var valueLength = response.getLength();
    final var byteArray = new byte[valueLength];
    final var valueBuffer = new UnsafeBuffer(byteArray);
    response.write(valueBuffer, 0);

    final var totalLength =
        MessageHeaderEncoder.ENCODED_LENGTH
            + ExecuteCoordinateResponseEncoder.BLOCK_LENGTH
            + ExecuteCoordinateResponseEncoder.valueHeaderLength()
            + valueLength;

    final var bytes = new byte[totalLength];
    final var buffer = new UnsafeBuffer(bytes);

    final var headerEncoder = new MessageHeaderEncoder();
    final var bodyEncoder = new ExecuteCoordinateResponseEncoder();

    bodyEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder).putValue(valueBuffer, 0, valueLength);

    return bytes;
  }
}
