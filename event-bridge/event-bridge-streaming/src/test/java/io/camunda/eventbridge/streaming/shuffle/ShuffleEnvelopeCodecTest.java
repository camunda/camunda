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

import io.camunda.eventbridge.streaming.shuffle.sbe.Operation;
import io.camunda.eventbridge.streaming.shuffle.sbe.PayloadKind;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ShuffleEnvelopeCodecTest {

  private static ShuffleEnvelope envelope(
      final PayloadKind kind,
      final Operation operation,
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
            PayloadKind.AGGREGATE_DELTA,
            Operation.MERGE,
            false,
            List.of(
                new CellDelta(1, 60_000L, new byte[] {1, 2}, new byte[] {9, 9}),
                new CellDelta(2, 60_000L, new byte[] {3}, new byte[] {8, 8, 8})));

    // when
    final ShuffleEnvelope decoded =
        ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original));

    // then (recursive comparison so the byte[] fields compare by content)
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
    assertThat(decoded.cells()).hasSize(2);
    assertThat(decoded.payloadKind()).isEqualTo(PayloadKind.AGGREGATE_DELTA);
    assertThat(decoded.operation()).isEqualTo(Operation.MERGE);
    assertThat(decoded.segment()).isEqualTo(128L);
    assertThat(decoded.producerPartition()).isEqualTo(3);
  }

  @Test
  void shouldCarryTheMoreChunksFlagForATruncatedBatch() {
    // given a chunk that is not the last for its (partition, segment)
    final ShuffleEnvelope original =
        envelope(
            PayloadKind.AGGREGATE_DELTA,
            Operation.MERGE,
            true,
            List.of(new CellDelta(1, 0L, new byte[] {1}, new byte[] {2})));

    // then the "more follows" flag round-trips, so the batch is self-describing on the wire
    assertThat(ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original)).moreChunks())
        .isTrue();
  }

  @Test
  void shouldRoundTripAReferenceUpsert() {
    // given a reference record (idempotent by key — needs no segment dedup)
    final ShuffleEnvelope original =
        envelope(
            PayloadKind.REFERENCE,
            Operation.UPSERT,
            false,
            List.of(new CellDelta(0, 0L, new byte[] {7}, new byte[] {0, 0})));

    // then dispatch fields are readable from the header alone
    final ShuffleEnvelope decoded =
        ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original));
    assertThat(decoded.payloadKind()).isEqualTo(PayloadKind.REFERENCE);
    assertThat(decoded.operation()).isEqualTo(Operation.UPSERT);
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void shouldRoundTripAnEmptyBatch() {
    final ShuffleEnvelope original =
        envelope(PayloadKind.AGGREGATE_DELTA, Operation.MERGE, false, List.of());
    assertThat(ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original)).cells())
        .isEmpty();
  }

  @Test
  void shouldEncodeDeterministically() {
    // given
    final ShuffleEnvelope original =
        envelope(
            PayloadKind.AGGREGATE_DELTA,
            Operation.MERGE,
            false,
            List.of(new CellDelta(1, 0L, new byte[] {5, 5}, new byte[] {6})));

    // then the same envelope encodes to identical bytes (re-emit safe), stable across re-encode
    final byte[] first = ShuffleEnvelopeCodec.encode(original);
    assertThat(ShuffleEnvelopeCodec.encode(original)).isEqualTo(first);
    assertThat(ShuffleEnvelopeCodec.encode(ShuffleEnvelopeCodec.decode(first))).isEqualTo(first);
  }

  @Test
  void shouldRejectFrameOfAnotherSchema() {
    // given a frame whose SBE schemaId (uint16 at header offset 4, little-endian) is corrupted
    final byte[] frame =
        ShuffleEnvelopeCodec.encode(
            envelope(
                PayloadKind.REFERENCE,
                Operation.UPSERT,
                false,
                List.of(new CellDelta(0, 0L, new byte[] {1}, new byte[] {1}))));
    frame[4] = (byte) 0xFF;

    // then decoding refuses rather than mis-reading another schema's message
    assertThatThrownBy(() -> ShuffleEnvelopeCodec.decode(frame))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a segment shuffle envelope");
  }
}
