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
import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
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
 *
 * <p>An <em>instance</em> encodes: it reuses its scratch buffer and encoder flyweights across
 * {@link #encode} calls (the returned frame is always a fresh copy), so an instance must not be
 * shared by concurrently encoding threads — one codec per publisher. {@link #decode(byte[])} is
 * static and allocates per call.
 *
 * <p>This codec is the <em>only</em> class that may touch the SBE-generated {@code shuffle.sbe}
 * package: it maps the facade's {@link ShufflePayloadKind}/{@link ShuffleOperation} onto the
 * generated wire enums, so every other consumer stays independent of the generated encoding.
 */
public final class ShuffleEnvelopeCodec {

  private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ShuffleEnvelopeEncoder encoder = new ShuffleEnvelopeEncoder();

  public byte[] encode(final ShuffleEnvelope envelope) {
    final CellsEncoder cells =
        encoder
            .wrapAndApplyHeader(buffer, 0, headerEncoder)
            .producedAt(envelope.producedAt())
            .schemaVersion(envelope.schemaVersion())
            .producerPartition(envelope.producerPartition())
            .segment(envelope.segment())
            .chunk(envelope.chunk())
            .moreChunks((short) (envelope.moreChunks() ? 1 : 0))
            .payloadKind(toWire(envelope.payloadKind()))
            .operation(toWire(envelope.operation()))
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
    final ShufflePayloadKind payloadKind = fromWire(decoder.payloadKind());
    final ShuffleOperation operation = fromWire(decoder.operation());

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

  // Facade <-> wire mapping: keeps the SBE-generated enums out of the public envelope API. The
  // facade enums are exhaustive, so encoding needs no default; decoding rejects a frame whose
  // enum value this codec version does not know (NULL_VAL / SBE_UNKNOWN) instead of guessing.

  private static PayloadKind toWire(final ShufflePayloadKind kind) {
    return switch (kind) {
      case AGGREGATE_DELTA -> PayloadKind.AGGREGATE_DELTA;
      case REFERENCE -> PayloadKind.REFERENCE;
    };
  }

  private static ShufflePayloadKind fromWire(final PayloadKind kind) {
    return switch (kind) {
      case AGGREGATE_DELTA -> ShufflePayloadKind.AGGREGATE_DELTA;
      case REFERENCE -> ShufflePayloadKind.REFERENCE;
      default -> throw new IllegalArgumentException("unknown payload kind on the wire: " + kind);
    };
  }

  private static Operation toWire(final ShuffleOperation operation) {
    return switch (operation) {
      case MERGE -> Operation.MERGE;
      case UPSERT -> Operation.UPSERT;
      case DELETE -> Operation.DELETE;
    };
  }

  private static ShuffleOperation fromWire(final Operation operation) {
    return switch (operation) {
      case MERGE -> ShuffleOperation.MERGE;
      case UPSERT -> ShuffleOperation.UPSERT;
      case DELETE -> ShuffleOperation.DELETE;
      default -> throw new IllegalArgumentException("unknown operation on the wire: " + operation);
    };
  }
}
