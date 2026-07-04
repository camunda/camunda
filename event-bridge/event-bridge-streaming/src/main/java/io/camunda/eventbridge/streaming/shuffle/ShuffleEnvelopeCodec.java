/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

import io.camunda.eventbridge.streaming.shuffle.sbe.MessageHeaderDecoder;
import io.camunda.eventbridge.streaming.shuffle.sbe.MessageHeaderEncoder;
import io.camunda.eventbridge.streaming.shuffle.sbe.ShuffleEnvelopeDecoder;
import io.camunda.eventbridge.streaming.shuffle.sbe.ShuffleEnvelopeDecoder.CellsDecoder;
import io.camunda.eventbridge.streaming.shuffle.sbe.ShuffleEnvelopeEncoder;
import io.camunda.eventbridge.streaming.shuffle.sbe.ShuffleEnvelopeEncoder.CellsEncoder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * SBE codec for {@link ShuffleEnvelope}, the segment-shuffle wire format — the same
 * generated-encoder/decoder approach the Event Bridge uses for its own protocol. Versioning and
 * schema evolution come from the SBE {@code messageHeader}; a frame that is not this schema/message
 * is rejected. Deterministic: encoding depends only on the envelope's fields, so a re-emitted batch
 * serialises identically.
 */
public final class ShuffleEnvelopeCodec {

  private ShuffleEnvelopeCodec() {}

  public static byte[] encode(final ShuffleEnvelope envelope) {
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final ShuffleEnvelopeEncoder encoder = new ShuffleEnvelopeEncoder();
    final CellsEncoder cells =
        encoder
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .producedAt(envelope.producedAt())
            .schemaVersion(envelope.schemaVersion())
            .producerPartition(envelope.producerPartition())
            .segment(envelope.segment())
            .chunk(envelope.chunk())
            .moreChunks((short) (envelope.moreChunks() ? 1 : 0))
            .payloadKind(envelope.payloadKind())
            .operation(envelope.operation())
            .cellsCount(envelope.cells().size());
    for (final CellDelta cell : envelope.cells()) {
      cells.next().streamId(cell.streamId()).windowStart(cell.windowStart());
      cells.putKey(cell.key(), 0, cell.key().length);
      cells.putPayload(cell.payload(), 0, cell.payload().length);
    }

    final int length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    final byte[] frame = new byte[length];
    buffer.getBytes(0, frame);
    return frame;
  }

  public static ShuffleEnvelope decode(final byte[] frame) {
    final DirectBuffer buffer = new UnsafeBuffer(frame);
    final MessageHeaderDecoder header = new MessageHeaderDecoder();
    header.wrap(buffer, 0);
    if (header.schemaId() != ShuffleEnvelopeDecoder.SCHEMA_ID
        || header.templateId() != ShuffleEnvelopeDecoder.TEMPLATE_ID) {
      throw new IllegalArgumentException(
          "not a segment shuffle envelope: schemaId="
              + header.schemaId()
              + " templateId="
              + header.templateId());
    }

    final ShuffleEnvelopeDecoder decoder = new ShuffleEnvelopeDecoder();
    decoder.wrap(buffer, header.encodedLength(), header.blockLength(), header.version());
    final long producedAt = decoder.producedAt();
    final int schemaVersion = decoder.schemaVersion();
    final int producerPartition = decoder.producerPartition();
    final long segment = decoder.segment();
    final int chunk = decoder.chunk();
    final boolean moreChunks = decoder.moreChunks() != 0;
    final var payloadKind = decoder.payloadKind();
    final var operation = decoder.operation();

    final List<CellDelta> cells = new ArrayList<>();
    for (final CellsDecoder cell : decoder.cells()) {
      final int streamId = cell.streamId();
      final long windowStart = cell.windowStart();
      final byte[] key = new byte[cell.keyLength()];
      cell.getKey(key, 0, key.length);
      final byte[] payload = new byte[cell.payloadLength()];
      cell.getPayload(payload, 0, payload.length);
      cells.add(new CellDelta(streamId, windowStart, key, payload));
    }

    return new ShuffleEnvelope(
        producedAt,
        schemaVersion,
        producerPartition,
        segment,
        chunk,
        moreChunks,
        payloadKind,
        operation,
        cells);
  }
}
