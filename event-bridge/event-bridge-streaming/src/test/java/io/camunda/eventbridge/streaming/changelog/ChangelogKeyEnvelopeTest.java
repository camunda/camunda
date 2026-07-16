/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.streaming.changelog.ChangelogKeyEnvelope.Envelope;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ChangelogKeyEnvelopeTest {

  @Test
  void shouldRoundTripTheCfTagAndStoreKey() {
    // given
    final byte[] storeKey = {1, 2, 3, 4, 5};

    // when
    final byte[] enveloped = ChangelogKeyEnvelope.encode(9, storeKey);
    final Envelope decoded = ChangelogKeyEnvelope.decode(enveloped);

    // then
    assertThat(decoded.cfTag()).isEqualTo(9);
    assertThat(decoded.storeKey()).isEqualTo(storeKey);
  }

  @Test
  void shouldPrefixTheStoreKeyWithASingleTagByte() {
    // given
    final byte[] storeKey = {0, 0, 0, 7};

    // when
    final byte[] enveloped = ChangelogKeyEnvelope.encode(3, storeKey);

    // then — cfTag(1) ++ storeKey, the durable layout
    assertThat(enveloped).isEqualTo(new byte[] {3, 0, 0, 0, 7});
  }

  @Test
  void shouldRejectACfTagOutsideAnUnsignedByte() {
    final byte[] storeKey = {0, 0, 0, 0};
    assertThatThrownBy(() -> ChangelogKeyEnvelope.encode(-1, storeKey))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ChangelogKeyEnvelope.encode(256, storeKey))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAcceptTheMaximumUnsignedByteTag() {
    // given/when
    final byte[] enveloped = ChangelogKeyEnvelope.encode(255, new byte[] {0, 0, 0, 0});

    // then — decodes back as unsigned, not as a negative byte
    assertThat(ChangelogKeyEnvelope.decode(enveloped).cfTag()).isEqualTo(255);
  }

  @Test
  void shouldRejectAStoreKeyShorterThanTheMarkerCollisionFloor() {
    assertThatThrownBy(() -> ChangelogKeyEnvelope.encode(1, new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectDecodingAKeyTooShortToBeALegalEnvelope() {
    assertThatThrownBy(() -> ChangelogKeyEnvelope.decode(new byte[] {1, 2, 3, 4}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Property test (streaming ADR 0009): no legal {@code (cfTag, storeKey)} envelope can ever equal
   * {@link ChangelogMarker#KEY} — the reserved key strictly the last record of every cut. The
   * marker is exactly 4 bytes; {@link ChangelogKeyEnvelope#encode} enforces a 4-byte store-key
   * floor, so every envelope is at least 5 bytes and can never match by length alone. Exercised
   * across every legal cfTag and a wide spread of store-key shapes (including ones that
   * deliberately mimic the marker's own byte pattern, e.g. an all-0xFF store key), to demonstrate
   * the floor holds regardless of content, not merely for a lucky sample.
   */
  @Test
  void shouldNeverCollideWithTheReservedMarkerKeyForAnyLegalEnvelope() {
    final Random random = new Random(42);
    for (int cfTag = 0; cfTag <= 0xFF; cfTag++) {
      // Minimum-length store key (the tightest possible margin against the marker).
      assertThat(ChangelogKeyEnvelope.encode(cfTag, new byte[] {1, 2, 3, 4}))
          .isNotEqualTo(ChangelogMarker.KEY);
      // A store key that mimics the marker's own all-0xFF pattern.
      assertThat(
              ChangelogKeyEnvelope.encode(
                  cfTag, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}))
          .isNotEqualTo(ChangelogMarker.KEY);
      // Random longer store keys (GroupedCellStore cell rows, variable-entry keys, etc.).
      final byte[] randomKey = new byte[4 + random.nextInt(32)];
      random.nextBytes(randomKey);
      assertThat(ChangelogKeyEnvelope.encode(cfTag, randomKey)).isNotEqualTo(ChangelogMarker.KEY);
    }
  }

  @Test
  void shouldMatchTheEncodingUsedForAGroupedCellStoreMetaRowKeyTag() {
    // given — a GroupedCellStore-style 4-byte big-endian group meta key, enveloped under the
    // OPEN_SEGMENT cf tag (streaming ADR 0009's Stage-1 multi-cf changelog)
    final int group = 7;
    final byte[] metaKey = ByteBuffer.allocate(Integer.BYTES).putInt(group).array();

    // when
    final byte[] enveloped = ChangelogKeyEnvelope.encode(6, metaKey);
    final Envelope decoded = ChangelogKeyEnvelope.decode(enveloped);

    // then
    assertThat(decoded.cfTag()).isEqualTo(6);
    assertThat(decoded.storeKey()).isEqualTo(metaKey);
  }
}
