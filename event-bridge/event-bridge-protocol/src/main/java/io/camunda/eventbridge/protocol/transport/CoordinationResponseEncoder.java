/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.transport;

import io.camunda.eventbridge.protocol.CoordinateRejectionType;
import io.camunda.eventbridge.protocol.ExecuteCoordinateResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Frames a coordinator reply into the {@code ExecuteCoordinateResponse} envelope the gateway's
 * broker client decodes. Two shapes share one wire layout:
 *
 * <ul>
 *   <li><b>success</b> — {@code rejectionType=NONE}, the {@code value} var-data carries the
 *       response payload, {@code rejectionReason} is empty.
 *   <li><b>rejection</b> — {@code rejectionType} is set, {@code rejectionReason} carries the
 *       reason, {@code value} is empty. The gateway surfaces it as a {@code BrokerRejection}.
 * </ul>
 */
public final class CoordinationResponseEncoder {

  private static final byte[] EMPTY = new byte[0];

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
   * after a command committed) as a successful reply. Stream-backed handlers (join/leave/commit,
   * topic admin) return raw value bytes, so they must be wrapped here just like {@link
   * #encode(BufferWriter)} does for in-memory responses.
   */
  public static byte[] encodeValue(final byte[] value) {
    return frame(CoordinateRejectionType.NONE, value, EMPTY);
  }

  public static byte[] encode(final BufferWriter response) {
    final var value = new byte[response.getLength()];
    response.write(new UnsafeBuffer(value), 0);
    return frame(CoordinateRejectionType.NONE, value, EMPTY);
  }

  /**
   * Frames a command rejection: the reply carries no value, only the rejection type and reason. The
   * gateway decodes this into a {@code BrokerRejection} and maps it to an HTTP status — so a
   * rejected command surfaces as an error to the caller, not a success.
   */
  public static byte[] encodeRejection(final RejectionType type, final String reason) {
    final var reasonBytes = reason == null ? EMPTY : reason.getBytes(StandardCharsets.UTF_8);
    return frame(CoordinationRejections.toWire(type), EMPTY, reasonBytes);
  }

  private static byte[] frame(
      final CoordinateRejectionType rejectionType, final byte[] value, final byte[] reason) {
    final var totalLength =
        MessageHeaderEncoder.ENCODED_LENGTH
            + ExecuteCoordinateResponseEncoder.BLOCK_LENGTH
            + ExecuteCoordinateResponseEncoder.valueHeaderLength()
            + value.length
            + ExecuteCoordinateResponseEncoder.rejectionReasonHeaderLength()
            + reason.length;

    final var bytes = new byte[totalLength];
    final var buffer = new UnsafeBuffer(bytes);

    new ExecuteCoordinateResponseEncoder()
        .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
        .rejectionType(rejectionType)
        .putValue(new UnsafeBuffer(value), 0, value.length)
        .putRejectionReason(new UnsafeBuffer(reason), 0, reason.length);

    return bytes;
  }
}
