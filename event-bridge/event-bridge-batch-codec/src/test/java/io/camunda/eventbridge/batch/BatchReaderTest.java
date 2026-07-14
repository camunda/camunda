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
import java.util.ArrayList;
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

  @Test
  void shouldVisitExactlyWhatTheListReaderReturns() {
    // given: two concatenated batches (the second patched to continue the position sequence),
    // mixing keyed, keyless and empty-value entries
    final byte[] first =
        new BatchBuilder().add("k1", bytes("v1")).add(bytes("v2")).add("k3", new byte[0]).build();
    final byte[] second = new BatchBuilder().add(bytes("v4")).add("k5", bytes("v5")).build();
    BatchFormat.putLongLE(second, BatchFormat.POSITION_OFFSET, 3L);
    final byte[] data = concat(first, second);

    // when: scanning with the cursor and with the list reader
    final List<BatchReader.Entry> visited = new ArrayList<>();
    BatchReader.forEachSkippingKeys(
        data,
        0,
        data.length,
        Long.MIN_VALUE,
        (position, array, valueOffset, valueLength) ->
            visited.add(
                new BatchReader.Entry(
                    position, new byte[0], copy(array, valueOffset, valueLength))));
    final List<BatchReader.Entry> listed =
        BatchReader.readSkippingKeys(data, 0, data.length, Long.MIN_VALUE);

    // then: the cursor visits exactly the listed entries — same order, positions and values
    assertThat(visited).hasSameSizeAs(listed);
    for (int i = 0; i < listed.size(); i++) {
      assertThat(visited.get(i).position()).isEqualTo(listed.get(i).position());
      assertThat(visited.get(i).value()).isEqualTo(listed.get(i).value());
    }
  }

  @Test
  void shouldApplyTheSameFromPositionFilterWhenVisiting() {
    // given: a batch of three entries
    final byte[] batch =
        new BatchBuilder().add(bytes("v0")).add(bytes("v1")).add(bytes("v2")).build();
    final List<BatchReader.Entry> all =
        BatchReader.readSkippingKeys(batch, 0, batch.length, Long.MIN_VALUE);
    final long secondPosition = all.get(1).position();

    // when: visiting from the second entry's position
    final List<Long> visitedPositions = new ArrayList<>();
    BatchReader.forEachSkippingKeys(
        batch,
        0,
        batch.length,
        secondPosition,
        (position, array, valueOffset, valueLength) -> visitedPositions.add(position));

    // then: the identical tail the list reader returns for that fromPosition
    assertThat(visitedPositions)
        .containsExactlyElementsOf(
            BatchReader.readSkippingKeys(batch, 0, batch.length, secondPosition).stream()
                .map(BatchReader.Entry::position)
                .toList());
  }

  private static byte[] concat(final byte[] first, final byte[] second) {
    final byte[] data = new byte[first.length + second.length];
    System.arraycopy(first, 0, data, 0, first.length);
    System.arraycopy(second, 0, data, first.length, second.length);
    return data;
  }

  private static byte[] copy(final byte[] data, final int offset, final int length) {
    final byte[] out = new byte[length];
    System.arraycopy(data, offset, out, 0, length);
    return out;
  }

  private static byte[] bytes(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }
}
