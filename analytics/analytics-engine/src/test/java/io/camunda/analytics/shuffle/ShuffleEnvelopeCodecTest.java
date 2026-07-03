/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.shuffle.sbe.Operation;
import io.camunda.analytics.shuffle.sbe.PayloadKind;
import org.junit.jupiter.api.Test;

final class ShuffleEnvelopeCodecTest {

  private static ShuffleEnvelope envelope(
      final PayloadKind kind, final Operation operation, final byte[] payload) {
    return new ShuffleEnvelope(
        1_700_000_000_000L,
        7,
        42,
        new byte[] {1, 2, 3},
        60_000L,
        3,
        128L,
        kind,
        operation,
        payload);
  }

  @Test
  void shouldRoundTripAggregateDelta() {
    // given
    final ShuffleEnvelope original =
        envelope(PayloadKind.AGGREGATE_DELTA, Operation.MERGE, new byte[] {9, 8, 7, 6});

    // when
    final ShuffleEnvelope decoded =
        ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original));

    // then (recursive comparison so the byte[] fields compare by content)
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
    assertThat(decoded.payloadKind()).isEqualTo(PayloadKind.AGGREGATE_DELTA);
    assertThat(decoded.operation()).isEqualTo(Operation.MERGE);
    assertThat(decoded.segment()).isEqualTo(128L);
    assertThat(decoded.producerPartition()).isEqualTo(3);
  }

  @Test
  void shouldRoundTripReferenceUpsert() {
    // given a reference record (e.g. process-definition metadata) riding the same shuffle
    final ShuffleEnvelope original =
        envelope(PayloadKind.REFERENCE, Operation.UPSERT, new byte[] {0, 0});

    // when
    final ShuffleEnvelope decoded =
        ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original));

    // then Stage 2 can dispatch on (payloadKind, operation) from the header alone
    assertThat(decoded.payloadKind()).isEqualTo(PayloadKind.REFERENCE);
    assertThat(decoded.operation()).isEqualTo(Operation.UPSERT);
    assertThat(decoded).usingRecursiveComparison().isEqualTo(original);
  }

  @Test
  void shouldRoundTripEmptyKeyAndPayload() {
    final ShuffleEnvelope original = envelope(PayloadKind.REFERENCE, Operation.DELETE, new byte[0]);
    final ShuffleEnvelope decoded =
        ShuffleEnvelopeCodec.decode(ShuffleEnvelopeCodec.encode(original));
    assertThat(decoded.operation()).isEqualTo(Operation.DELETE);
    assertThat(decoded.payload()).isEmpty();
  }

  @Test
  void shouldEncodeDeterministically() {
    // given
    final ShuffleEnvelope original =
        envelope(PayloadKind.AGGREGATE_DELTA, Operation.MERGE, new byte[] {5, 5, 5});

    // then the same envelope encodes to identical bytes (re-emit safe), and re-encode is stable
    final byte[] first = ShuffleEnvelopeCodec.encode(original);
    assertThat(ShuffleEnvelopeCodec.encode(original)).isEqualTo(first);
    assertThat(ShuffleEnvelopeCodec.encode(ShuffleEnvelopeCodec.decode(first))).isEqualTo(first);
  }

  @Test
  void shouldRejectFrameOfAnotherSchema() {
    // given a frame whose SBE schemaId (uint16 at header offset 4, little-endian) is corrupted
    final byte[] frame =
        ShuffleEnvelopeCodec.encode(envelope(PayloadKind.REFERENCE, Operation.UPSERT, new byte[0]));
    frame[4] = (byte) 0xFF;

    // then decoding refuses rather than mis-reading another schema's message
    assertThatThrownBy(() -> ShuffleEnvelopeCodec.decode(frame))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an analytics shuffle envelope");
  }
}
