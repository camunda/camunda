/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link BatchReader#readSkippingKeys(byte[], int, int, long)} decodes the same
 * positions and values as the key-copying {@link BatchReader#read(byte[], int, int, long)}, only
 * without materializing per-entry key copies.
 */
final class BatchReaderTest {

  @Test
  void shouldDecodeSameEntriesWithAndWithoutKeyCopies() {
    // given: a batch mixing keyed, keyless and empty-value entries
    final byte[] batch =
        new BatchBuilder().add("k1", bytes("v1")).add(bytes("v2")).add("k3", new byte[0]).build();

    // when
    final List<BatchReader.Entry> withKeys =
        BatchReader.read(batch, 0, batch.length, Long.MIN_VALUE);
    final List<BatchReader.Entry> withoutKeys =
        BatchReader.readSkippingKeys(batch, 0, batch.length, Long.MIN_VALUE);

    // then: same positions and values entry-by-entry; the key is uniformly empty when skipped
    assertThat(withoutKeys).hasSameSizeAs(withKeys);
    for (int i = 0; i < withKeys.size(); i++) {
      assertThat(withoutKeys.get(i).position()).isEqualTo(withKeys.get(i).position());
      assertThat(withoutKeys.get(i).value()).isEqualTo(withKeys.get(i).value());
      assertThat(withoutKeys.get(i).key()).isEmpty();
    }
    assertThat(new String(withKeys.get(0).key(), StandardCharsets.UTF_8)).isEqualTo("k1");
  }

  @Test
  void shouldApplyFromPositionFilterWhenSkippingKeys() {
    // given: a batch of three entries starting at the builder's base position
    final byte[] batch =
        new BatchBuilder().add(bytes("v0")).add(bytes("v1")).add(bytes("v2")).build();
    final List<BatchReader.Entry> all = BatchReader.read(batch, 0, batch.length, Long.MIN_VALUE);
    final long secondPosition = all.get(1).position();

    // when: reading from the second entry's position
    final List<BatchReader.Entry> tail =
        BatchReader.readSkippingKeys(batch, 0, batch.length, secondPosition);

    // then: the same tail the key-copying reader returns
    assertThat(tail)
        .extracting(BatchReader.Entry::position)
        .containsExactly(all.get(1).position(), all.get(2).position());
  }

  private static byte[] bytes(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
