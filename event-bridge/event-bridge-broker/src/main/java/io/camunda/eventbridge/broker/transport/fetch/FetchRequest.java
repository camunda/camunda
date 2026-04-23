/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.fetch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Consumer fetch request. Stateless — the consumer sends the offset on every fetch.
 *
 * @param consumerId identifies the consumer for pending fetch management
 * @param partitionId which partition to read from
 * @param offset position to start reading from (inclusive)
 * @param maxBytes maximum bytes to return in the response
 * @param minBytes minimum bytes before responding (long-poll threshold)
 * @param maxWaitMs maximum time to wait for minBytes (0 = respond immediately)
 */
public record FetchRequest(
    String consumerId, int partitionId, long offset, int maxBytes, int minBytes, long maxWaitMs) {

  /**
   * Decodes a fetch request from a byte array.
   *
   * <p>Wire format:
   *
   * <pre>
   * [consumerIdLength (2)][consumerId (var)][partitionId (4)][offset (8)]
   * [maxBytes (4)][minBytes (4)][maxWaitMs (8)]
   * </pre>
   *
   * @param data the raw request bytes
   * @return decoded fetch request
   * @throws IllegalArgumentException if the data is malformed or too short
   */
  public static FetchRequest decode(final byte[] data) {
    final var buf = ByteBuffer.wrap(data);

    if (buf.remaining() < Short.BYTES) {
      throw new IllegalArgumentException("Fetch request too short: missing consumerId length");
    }

    final int consumerIdLength = buf.getShort() & 0xFFFF;
    if (buf.remaining() < consumerIdLength) {
      throw new IllegalArgumentException(
          "Fetch request too short: consumerId length "
              + consumerIdLength
              + " exceeds remaining "
              + buf.remaining());
    }

    final var consumerIdBytes = new byte[consumerIdLength];
    buf.get(consumerIdBytes);
    final var consumerId = new String(consumerIdBytes, StandardCharsets.UTF_8);

    final int expectedRemaining =
        Integer.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES + Long.BYTES;
    if (buf.remaining() < expectedRemaining) {
      throw new IllegalArgumentException(
          "Fetch request too short: expected "
              + expectedRemaining
              + " remaining bytes, got "
              + buf.remaining());
    }

    final int partitionId = buf.getInt();
    final long offset = buf.getLong();
    final int maxBytes = buf.getInt();
    final int minBytes = buf.getInt();
    final long maxWaitMs = buf.getLong();

    return new FetchRequest(consumerId, partitionId, offset, maxBytes, minBytes, maxWaitMs);
  }
}
