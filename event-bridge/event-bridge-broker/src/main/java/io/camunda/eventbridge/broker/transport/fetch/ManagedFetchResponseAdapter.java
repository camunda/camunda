/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.transport.fetch;

import io.atomix.cluster.messaging.ManagedPayload;
import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.FetchResponseEncoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.netty.buffer.ByteBuf;
import java.util.List;

/**
 * Serialises a {@link FetchResponse} as an SBE {@code FetchResponse} message, but streams the
 * variable-length {@code data} bytes zero-copy via {@link SharedFileRegion} (sendfile) instead of
 * copying them through the JVM heap.
 *
 * <p>Wire layout (little-endian, matching the SBE schema):
 *
 * <pre>
 * [SBE message header (8)] [errorCode(1) firstPosition(8) lastPosition(8) highWatermark(8) = 25]
 * [data length (uint32, 4)]            ← all of the above written into the heap buffer
 * [data bytes (N)]                     ← appended as SharedFileRegion(s), streamed from the log
 * </pre>
 *
 * From the receiver's perspective this is an ordinary SBE {@code FetchResponse}; it cannot tell the
 * payload arrived via sendfile.
 */
public class ManagedFetchResponseAdapter implements ManagedPayload {

  private static final int HEADER_LENGTH =
      MessageHeaderEncoder.ENCODED_LENGTH
          + FetchResponseEncoder.BLOCK_LENGTH
          + Integer.BYTES; // var-data length prefix

  private final FetchResponse response;

  public ManagedFetchResponseAdapter(final FetchResponse response) {
    this.response = response;
  }

  @Override
  public int length() {
    return HEADER_LENGTH + dataLength();
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    if (isEmpty()) {
      writeHeader(buffer, 0, -1, highWatermark(), 0);
      return;
    }

    writeHeader(
        buffer,
        response.firstBatchPosition(),
        response.lastBatchPosition(),
        response.highWatermark(),
        response.dataLength());

    final var channel = response.channel();
    for (final var indexEntry : response.entries()) {
      out.add(new SharedFileRegion(channel, indexEntry.position(), indexEntry.length()));
    }
  }

  @Override
  public void release() {
    if (response != null) {
      response.releaseLease();
    }
  }

  private boolean isEmpty() {
    return response == null || response.isEmpty();
  }

  private int dataLength() {
    return response == null ? 0 : response.dataLength();
  }

  private long highWatermark() {
    return response == null ? 0 : response.highWatermark();
  }

  /**
   * Writes the SBE message header, block fields, and the var-data length prefix (little-endian).
   */
  private static void writeHeader(
      final ByteBuf buffer,
      final long firstPosition,
      final long lastPosition,
      final long highWatermark,
      final int dataLength) {
    // SBE message header: blockLength, templateId, schemaId, version (all uint16, little-endian).
    buffer.writeShortLE(FetchResponseEncoder.BLOCK_LENGTH);
    buffer.writeShortLE(FetchResponseEncoder.TEMPLATE_ID);
    buffer.writeShortLE(FetchResponseEncoder.SCHEMA_ID);
    buffer.writeShortLE(FetchResponseEncoder.SCHEMA_VERSION);

    // Block fields, in schema order.
    buffer.writeByte(ErrorCode.NONE.value());
    buffer.writeLongLE(firstPosition);
    buffer.writeLongLE(lastPosition);
    buffer.writeLongLE(highWatermark);

    // var-data 'data' length prefix; the bytes follow as SharedFileRegion(s).
    buffer.writeIntLE(dataLength);
  }
}
