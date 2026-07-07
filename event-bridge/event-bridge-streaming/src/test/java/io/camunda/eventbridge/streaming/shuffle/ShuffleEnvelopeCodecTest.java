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

import java.util.List;
import org.junit.jupiter.api.Test;

final class ShuffleEnvelopeCodecTest {

  /** One instance across all encodes — also exercises the reused scratch buffer/flyweights. */
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
    final ShuffleEnvelope decoded = ShuffleEnvelopeCodec.decode(codec.encode(original));

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
    assertThat(ShuffleEnvelopeCodec.decode(codec.encode(original)).moreChunks()).isTrue();
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
    final ShuffleEnvelope decoded = ShuffleEnvelopeCodec.decode(codec.encode(original));
    assertThat(decoded.payloadKind()).isEqualTo(ShufflePayloadKind.REFERENCE);
    assertThat(decoded.operation()).isEqualTo(ShuffleOperation.UPSERT);
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void shouldRoundTripAnEmptyBatch() {
    final ShuffleEnvelope original =
        envelope(ShufflePayloadKind.AGGREGATE_DELTA, ShuffleOperation.MERGE, false, List.of());
    assertThat(ShuffleEnvelopeCodec.decode(codec.encode(original)).cells()).isEmpty();
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
    assertThat(codec.encode(ShuffleEnvelopeCodec.decode(first))).isEqualTo(first);
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
    assertThat(ShuffleEnvelopeCodec.decode(largerFrame))
        .usingRecursiveComparison()
        .isEqualTo(larger);
    assertThat(ShuffleEnvelopeCodec.decode(smallerFrame))
        .usingRecursiveComparison()
        .isEqualTo(smaller);
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
    assertThatThrownBy(() -> ShuffleEnvelopeCodec.decode(frame))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a segment shuffle envelope");
  }
}
