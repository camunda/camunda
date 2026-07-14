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
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * SBE codec for {@link ShuffleEnvelope}, the segment-shuffle wire format — the same
 * generated-encoder/decoder approach the Event Bridge uses for its own protocol. Versioning and
 * schema evolution come from the SBE {@code messageHeader}; a frame that is not this schema/message
 * is rejected. Deterministic: encoding depends only on the envelope's fields, so a re-emitted batch
 * serialises identically.
 *
 * <p>An <em>instance</em> reuses its scratch buffer and encoder/decoder flyweights across {@link
 * #encode} and {@link #decode} calls (returned frames and decoded envelopes are always fresh, owned
 * copies), so an instance must be confined to one thread — one codec per publisher on the encode
 * side, one per decoding thread on the decode side. The stream runtime invokes its {@code
 * MessageDeserializer} only on its single source-loop thread ({@code SourceLoop#toEntry}), so one
 * codec per runtime deserializer satisfies this.
 *
 * <p>This codec is the <em>only</em> class that may touch the SBE-generated {@code shuffle.sbe}
 * package: it maps the facade's {@link ShufflePayloadKind}/{@link ShuffleOperation} onto the
 * generated wire enums, so every other consumer stays independent of the generated encoding.
 */
public final class ShuffleEnvelopeCodec {

  private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ShuffleEnvelopeEncoder encoder = new ShuffleEnvelopeEncoder();

  private final UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ShuffleEnvelopeDecoder decoder = new ShuffleEnvelopeDecoder();
  private final EnvelopeCollector collector = new EnvelopeCollector();

  /**
   * Receives one decoded envelope without materializing it: the header fields once, then one
   * callback per cell — the hot-path alternative to {@link #decode(byte[])}, which allocates a
   * {@link ShuffleEnvelope} plus a {@link CellDelta} per cell.
   *
   * <p><b>Ownership.</b> {@code key} and {@code payload} are fresh, owned copies the visitor may
   * retain indefinitely. That is deliberate: the Stage-2 consumer retains both beyond the call —
   * the key array backs the merger's in-heap cell key ({@code DimensionKeyValue#fromBytes} takes
   * ownership), and a cell whose stream has no applier yet is parked durably with its key and
   * payload — so views would have to be copied by every consumer anyway. All other fields are value
   * types.
   */
  public interface EnvelopeVisitor {

    /**
     * Called once per frame with the envelope header, before any cell. Return {@code false} to skip
     * the cells — the header alone decides dispatch (e.g. reference/upsert frames need no merge),
     * and skipping avoids the per-cell key/payload copies entirely.
     */
    boolean onEnvelope(
        long producedAt,
        int schemaVersion,
        int producerPartition,
        long segment,
        int chunk,
        boolean moreChunks,
        ShufflePayloadKind payloadKind,
        ShuffleOperation operation,
        int cellCount);

    /** Called once per cell, in frame order. See the interface contract for ownership. */
    void onCell(int streamId, long windowStart, byte[] key, byte[] payload);
  }

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

  /**
   * Decodes {@code frame} through the visitor, reusing this instance's decoder flyweights — no
   * per-frame or per-cell object materializes beyond the owned key/payload copies handed to the
   * visitor (see {@link EnvelopeVisitor} for the ownership contract).
   */
  public void decode(final byte[] frame, final EnvelopeVisitor visitor) {
    readBuffer.wrap(frame);
    headerDecoder.wrap(readBuffer, 0);
    if (headerDecoder.schemaId() != ShuffleEnvelopeDecoder.SCHEMA_ID
        || headerDecoder.templateId() != ShuffleEnvelopeDecoder.TEMPLATE_ID) {
      throw new IllegalArgumentException(
          "not a segment shuffle envelope: schemaId="
              + headerDecoder.schemaId()
              + " templateId="
              + headerDecoder.templateId());
    }

    decoder.wrap(
        readBuffer,
        headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());
    final long producedAt = decoder.producedAt();
    final int schemaVersion = decoder.schemaVersion();
    final int producerPartition = decoder.producerPartition();
    final long segment = decoder.segment();
    final int chunk = decoder.chunk();
    final boolean moreChunks = decoder.moreChunks() != 0;
    final ShufflePayloadKind payloadKind = fromWire(decoder.payloadKind());
    final ShuffleOperation operation = fromWire(decoder.operation());

    final CellsDecoder cells = decoder.cells();
    if (!visitor.onEnvelope(
        producedAt,
        schemaVersion,
        producerPartition,
        segment,
        chunk,
        moreChunks,
        payloadKind,
        operation,
        cells.count())) {
      return;
    }
    for (final CellsDecoder cell : cells) {
      final int streamId = cell.streamId();
      final long windowStart = cell.windowStart();
      final byte[] key = new byte[cell.keyLength()];
      cell.getKey(key, 0, key.length);
      final byte[] payload = new byte[cell.payloadLength()];
      cell.getPayload(payload, 0, payload.length);
      visitor.onCell(streamId, windowStart, key, payload);
    }
  }

  /**
   * Decodes {@code frame} into a fully owned {@link ShuffleEnvelope} — the compatibility path for
   * callers that must retain the whole envelope (e.g. the runtime's deserializer, whose result is
   * queued between the source loop and the partition actor). Built on {@link #decode(byte[],
   * EnvelopeVisitor)}, so the envelope's cell list is constructed exactly once, never copied.
   */
  public ShuffleEnvelope decode(final byte[] frame) {
    decode(frame, collector);
    return collector.take();
  }

  /** Reused visitor shell for {@link #decode(byte[])}; its per-frame state is handed off whole. */
  private static final class EnvelopeCollector implements EnvelopeVisitor {

    private long producedAt;
    private int schemaVersion;
    private int producerPartition;
    private long segment;
    private int chunk;
    private boolean moreChunks;
    private ShufflePayloadKind payloadKind;
    private ShuffleOperation operation;
    private List<CellDelta> cells;

    @Override
    public boolean onEnvelope(
        final long producedAt,
        final int schemaVersion,
        final int producerPartition,
        final long segment,
        final int chunk,
        final boolean moreChunks,
        final ShufflePayloadKind payloadKind,
        final ShuffleOperation operation,
        final int cellCount) {
      this.producedAt = producedAt;
      this.schemaVersion = schemaVersion;
      this.producerPartition = producerPartition;
      this.segment = segment;
      this.chunk = chunk;
      this.moreChunks = moreChunks;
      this.payloadKind = payloadKind;
      this.operation = operation;
      cells = new ArrayList<>(cellCount);
      return true;
    }

    @Override
    public void onCell(
        final int streamId, final long windowStart, final byte[] key, final byte[] payload) {
      cells.add(new CellDelta(streamId, windowStart, key, payload));
    }

    /** The collected envelope, releasing the cell list so the next decode cannot alias it. */
    ShuffleEnvelope take() {
      final ShuffleEnvelope envelope =
          new ShuffleEnvelope(
              producedAt,
              schemaVersion,
              producerPartition,
              segment,
              chunk,
              moreChunks,
              payloadKind,
              operation,
              cells);
      cells = null;
      return envelope;
    }
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
