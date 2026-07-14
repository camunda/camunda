/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShuffleEnvelopeCodecTest {

  /** One instance across all encodes and decodes — exercises the reused flyweights both ways. */
  private final ShuffleEnvelopeCodec codec = new ShuffleEnvelopeCodec();

  private static ShuffleEnvelope envelope(
      final ShufflePayloadKind kind,
      final ShuffleOperation operation,
      final boolean moreChunks,
      final List<CellDelta> cells) {
    return new ShuffleEnvelope(
        1_700_000_000_000L, 7, 3, 128L, 0, moreChunks, kind, operation, cells);
  }

  @Test
  void shouldRoundTripABatchOfCellDeltas() {
    // given a segment batch with two cell deltas (different keys / streams)
    final ShuffleEnvelope original =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            false,
            List.of(
                new CellDelta(1, 60_000L, new byte[] {1, 2}, new byte[] {9, 9}),
                new CellDelta(2, 60_000L, new byte[] {3}, new byte[] {8, 8, 8})));

    // when
    final ShuffleEnvelope decoded = codec.decode(codec.encode(original));

    // then (recursive comparison so the byte[] fields compare by content)
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
    assertThat(decoded.cells()).hasSize(2);
    assertThat(decoded.payloadKind()).isEqualTo(ShufflePayloadKind.AGGREGATE_DELTA);
    assertThat(decoded.operation()).isEqualTo(ShuffleOperation.MERGE);
    assertThat(decoded.segment()).isEqualTo(128L);
    assertThat(decoded.producerPartition()).isEqualTo(3);
  }

  @Test
  void shouldCarryTheMoreChunksFlagForATruncatedBatch() {
    // given a chunk that is not the last for its (partition, segment)
    final ShuffleEnvelope original =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            true,
            List.of(new CellDelta(1, 0L, new byte[] {1}, new byte[] {2})));

    // then the "more follows" flag round-trips, so the batch is self-describing on the wire
    assertThat(codec.decode(codec.encode(original)).moreChunks()).isTrue();
  }

  @Test
  void shouldRoundTripAReferenceUpsert() {
    // given a reference record (idempotent by key — needs no segment dedup)
    final ShuffleEnvelope original =
        envelope(
            ShufflePayloadKind.REFERENCE,
            ShuffleOperation.UPSERT,
            false,
            List.of(new CellDelta(0, 0L, new byte[] {7}, new byte[] {0, 0})));

    // then dispatch fields are readable from the header alone
    final ShuffleEnvelope decoded = codec.decode(codec.encode(original));
    assertThat(decoded.payloadKind()).isEqualTo(ShufflePayloadKind.REFERENCE);
    assertThat(decoded.operation()).isEqualTo(ShuffleOperation.UPSERT);
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void shouldRoundTripAnEmptyBatch() {
    final ShuffleEnvelope original =
        envelope(ShufflePayloadKind.AGGREGATE_DELTA, ShuffleOperation.MERGE, false, List.of());
    assertThat(codec.decode(codec.encode(original)).cells()).isEmpty();
  }

  @Test
  void shouldEncodeDeterministically() {
    // given
    final ShuffleEnvelope original =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            false,
            List.of(new CellDelta(1, 0L, new byte[] {5, 5}, new byte[] {6})));

    // then the same envelope encodes to identical bytes (re-emit safe), stable across re-encode
    final byte[] first = codec.encode(original);
    assertThat(codec.encode(original)).isEqualTo(first);
    assertThat(codec.encode(codec.decode(first))).isEqualTo(first);
  }

  @Test
  void shouldKeepEarlierFramesIntactWhenReusingTheCodec() {
    // given two envelopes of different sizes encoded by the same instance (reused scratch buffer)
    final ShuffleEnvelope larger =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            false,
            List.of(
                new CellDelta(1, 60_000L, new byte[] {1, 2, 3, 4}, new byte[] {9, 9, 9, 9}),
                new CellDelta(2, 60_000L, new byte[] {5, 6}, new byte[] {8})));
    final ShuffleEnvelope smaller =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            false,
            List.of(new CellDelta(3, 0L, new byte[] {7}, new byte[] {1})));

    // when: encoding back-to-back
    final byte[] largerFrame = codec.encode(larger);
    final byte[] smallerFrame = codec.encode(smaller);

    // then: the first frame is a copy — the second encode must not have clobbered it
    assertThat(codec.decode(largerFrame)).usingRecursiveComparison().isEqualTo(larger);
    assertThat(codec.decode(smallerFrame)).usingRecursiveComparison().isEqualTo(smaller);
  }

  @Test
  void shouldRejectFrameOfAnotherSchema() {
    // given a frame whose SBE schemaId (uint16 at header offset 4, little-endian) is corrupted
    final byte[] frame =
        codec.encode(
            envelope(
                ShufflePayloadKind.REFERENCE,
                ShuffleOperation.UPSERT,
                false,
                List.of(new CellDelta(0, 0L, new byte[] {1}, new byte[] {1}))));
    frame[4] = (byte) 0xFF;

    // then decoding refuses rather than mis-reading another schema's message
    assertThatThrownBy(() -> codec.decode(frame))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a segment shuffle envelope");
    assertThatThrownBy(() -> codec.decode(frame, new RecordingVisitor()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a segment shuffle envelope");
  }

  @Test
  void shouldDecodeTheSameFrameThroughTheVisitorAsThroughTheMaterializedPath() {
    // given a multi-cell frame
    final ShuffleEnvelope original =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            true,
            List.of(
                new CellDelta(1, 60_000L, new byte[] {1, 2}, new byte[] {9, 9}),
                new CellDelta(2, 120_000L, new byte[] {3}, new byte[] {8, 8, 8}),
                new CellDelta(1, 180_000L, new byte[] {4, 5, 6}, new byte[] {7})));
    final byte[] frame = codec.encode(original);

    // when: decoding once per path
    final ShuffleEnvelope materialized = codec.decode(frame);
    final RecordingVisitor visitor = new RecordingVisitor();
    codec.decode(frame, visitor);

    // then: the visitor observed the same header and the same cells, in order
    assertThat(visitor.cellCount).isEqualTo(3);
    assertThat(visitor.asEnvelope()).usingRecursiveComparison().isEqualTo(materialized);
    assertThat(visitor.asEnvelope()).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void shouldSkipTheCellsWhenTheVisitorDeclinesTheEnvelope() {
    // given a frame the consumer dispatches away on the header alone (e.g. a reference upsert)
    final byte[] frame =
        codec.encode(
            envelope(
                ShufflePayloadKind.REFERENCE,
                ShuffleOperation.UPSERT,
                false,
                List.of(new CellDelta(1, 0L, new byte[] {1}, new byte[] {2}))));
    final RecordingVisitor visitor = new RecordingVisitor().declining();

    // when
    codec.decode(frame, visitor);

    // then: the header was observed, but no cell was materialized
    assertThat(visitor.payloadKind).isEqualTo(ShufflePayloadKind.REFERENCE);
    assertThat(visitor.operation).isEqualTo(ShuffleOperation.UPSERT);
    assertThat(visitor.cellCount).isEqualTo(1);
    assertThat(visitor.cells).isEmpty();
  }

  @Test
  void shouldDecodeIndependentEnvelopesWhenReusingTheInstance() {
    // given two frames of different shapes decoded by the same instance (reused flyweights)
    final ShuffleEnvelope first =
        envelope(
            ShufflePayloadKind.AGGREGATE_DELTA,
            ShuffleOperation.MERGE,
            false,
            List.of(
                new CellDelta(1, 60_000L, new byte[] {1, 2, 3}, new byte[] {9, 9, 9, 9}),
                new CellDelta(2, 60_000L, new byte[] {4}, new byte[] {8})));
    final ShuffleEnvelope second =
        new ShuffleEnvelope(
            1_800_000_000_000L,
            8,
            5,
            256L,
            3,
            true,
            ShufflePayloadKind.REFERENCE,
            ShuffleOperation.DELETE,
            List.of(new CellDelta(7, 0L, new byte[] {5, 6}, new byte[] {1, 1})));

    // when: decoding back-to-back
    final ShuffleEnvelope firstDecoded = codec.decode(codec.encode(first));
    final ShuffleEnvelope secondDecoded = codec.decode(codec.encode(second));

    // then: the earlier decode's result is fully owned — the later decode must not clobber it
    assertThat(firstDecoded).usingRecursiveComparison().isEqualTo(first);
    assertThat(secondDecoded).usingRecursiveComparison().isEqualTo(second);
  }

  /** Rebuilds an envelope from visitor callbacks so equivalence can be asserted structurally. */
  private static final class RecordingVisitor implements ShuffleEnvelopeCodec.EnvelopeVisitor {

    private long producedAt;
    private int schemaVersion;
    private int producerPartition;
    private long segment;
    private int chunk;
    private boolean moreChunks;
    private ShufflePayloadKind payloadKind;
    private ShuffleOperation operation;
    private int cellCount;
    private final List<CellDelta> cells = new ArrayList<>();
    private boolean accept = true;

    RecordingVisitor declining() {
      accept = false;
      return this;
    }

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
      this.cellCount = cellCount;
      return accept;
    }

    @Override
    public void onCell(
        final int streamId, final long windowStart, final byte[] key, final byte[] payload) {
      cells.add(new CellDelta(streamId, windowStart, key, payload));
    }

    ShuffleEnvelope asEnvelope() {
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
}
