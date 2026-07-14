/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.eventbridge.batch.BatchBuilder;
import io.camunda.eventbridge.batch.BatchReader;
import io.camunda.eventbridge.client.FetchResult.Outcome;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link FetchResult#parse(byte[])} reads entries in place (no payload copy) and
 * classifies the empty / out-of-range / malformed outcomes.
 */
final class FetchResultTest {

  private static final int HEADER_SIZE = Long.BYTES * 3 + Integer.BYTES;

  @Test
  void shouldReadEntriesFromParsedBody() {
    // given: a fetch body carrying a batch with two entries
    final byte[] batch =
        new BatchBuilder()
            .add("k1", "v1".getBytes(StandardCharsets.UTF_8))
            .add("k2", "v2".getBytes(StandardCharsets.UTF_8))
            .build();
    final byte[] body = fetchBody(10L, 11L, 42L, batch);

    // when
    final var result = FetchResult.parse(body);

    // then
    assertThat(result.outcome()).isEqualTo(Outcome.OK);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.isEmpty()).isFalse();
    assertThat(result.firstBatchPosition()).isEqualTo(10L);
    assertThat(result.lastBatchPosition()).isEqualTo(11L);
    assertThat(result.highWatermark()).isEqualTo(42L);

    final List<byte[]> values = new ArrayList<>();
    for (final BatchReader.Entry entry : result.entries()) {
      values.add(entry.value());
    }
    assertThat(values)
        .extracting(v -> new String(v, StandardCharsets.UTF_8))
        .containsExactly("v1", "v2");
  }

  @Test
  void shouldSkipKeyCopiesOnTheFetchPath() {
    // given: a batch whose entries carry keys on the wire
    final byte[] batch =
        new BatchBuilder()
            .add("k1", "v1".getBytes(StandardCharsets.UTF_8))
            .add("k2", "v2".getBytes(StandardCharsets.UTF_8))
            .build();
    final var result = FetchResult.parse(fetchBody(10L, 11L, 42L, batch));

    // when: iterating the fetch-path entries
    final List<BatchReader.Entry> entries = result.entries();

    // then: positions and values match the key-copying reader, but no key bytes are materialized
    final List<BatchReader.Entry> withKeys =
        BatchReader.read(batch, 0, batch.length, Long.MIN_VALUE);
    assertThat(entries).hasSameSizeAs(withKeys);
    for (int i = 0; i < entries.size(); i++) {
      assertThat(entries.get(i).position()).isEqualTo(withKeys.get(i).position());
      assertThat(entries.get(i).value()).isEqualTo(withKeys.get(i).value());
      assertThat(entries.get(i).key()).isEmpty();
    }
  }

  @Test
  void shouldNotCopyPayloadAndTolerateTrailingBytesInSharedArray() {
    // given: the parsed body is shared; a per-entry copy must not alias the response array
    final byte[] batch =
        new BatchBuilder().add("k", "value".getBytes(StandardCharsets.UTF_8)).build();
    final byte[] body = fetchBody(1L, 1L, 5L, batch);
    final var result = FetchResult.parse(body);

    // when: read a value copy, then mutate the original response array beyond the header
    final BatchReader.Entry entry = result.entries().iterator().next();
    final byte[] valueCopy = entry.value();
    Arrays.fill(body, HEADER_SIZE, body.length, (byte) 0);

    // then: the previously returned copy is unaffected
    assertThat(new String(valueCopy, StandardCharsets.UTF_8)).isEqualTo("value");
  }

  @Test
  void shouldVisitTheSameEntriesTheListIterationReturns() {
    // given: a fetch body whose first batch begins before the requested start offset
    final byte[] batch =
        new BatchBuilder()
            .add("k0", "v0".getBytes(StandardCharsets.UTF_8))
            .add("k1", "v1".getBytes(StandardCharsets.UTF_8))
            .add("k2", "v2".getBytes(StandardCharsets.UTF_8))
            .build();
    final var result = FetchResult.parse(fetchBody(0L, 2L, 42L, batch));
    final long startOffset = 1L;
    final List<BatchReader.Entry> listed = result.entries(startOffset);

    // when: visiting entries in place from the same start offset
    final List<Long> positions = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    result.forEachEntry(
        startOffset,
        (position, data, valueOffset, valueLength) -> {
          positions.add(position);
          values.add(new String(data, valueOffset, valueLength, StandardCharsets.UTF_8));
        });

    // then: the cursor visits exactly the listed tail — same positions and values, same skipping
    assertThat(positions)
        .containsExactlyElementsOf(listed.stream().map(BatchReader.Entry::position).toList());
    assertThat(values)
        .containsExactlyElementsOf(
            listed.stream().map(e -> new String(e.value(), StandardCharsets.UTF_8)).toList());
    assertThat(positions).containsExactly(1L, 2L);
  }

  @Test
  void shouldVisitNothingOnAnEmptyResult() {
    // given
    final var result = FetchResult.empty(7L);

    // when / then: the cursor is a safe no-op on a result without data
    result.forEachEntry(
        Long.MIN_VALUE,
        (position, data, valueOffset, valueLength) -> fail("no entry should be visited"));
  }

  @Test
  void shouldReturnEmptyForNullOrEmptyBody() {
    assertThat(FetchResult.parse(null).outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(FetchResult.parse(new byte[0]).outcome()).isEqualTo(Outcome.EMPTY);
  }

  @Test
  void shouldReturnEmptyWhenHeaderAdvertisesNoData() {
    // given: a header with dataLength 0
    final byte[] body = fetchBody(0L, -1L, 7L, new byte[0]);

    // when / then
    final var result = FetchResult.parse(body);
    assertThat(result.outcome()).isEqualTo(Outcome.EMPTY);
    assertThat(result.highWatermark()).isEqualTo(7L);
  }

  @Test
  void shouldFailOnOverrunningDataLength() {
    // given: a header claiming more data than the body carries
    final byte[] body =
        ByteBuffer.allocate(HEADER_SIZE).putLong(1L).putLong(1L).putLong(1L).putInt(999).array();

    // when / then
    assertThat(FetchResult.parse(body).outcome()).isEqualTo(Outcome.FAILED);
  }

  @Test
  void shouldExposeOutOfRangeAndFailedFactories() {
    assertThat(FetchResult.outOfRange().isOutOfRange()).isTrue();
    assertThat(FetchResult.failed("boom").outcome()).isEqualTo(Outcome.FAILED);
    assertThat(FetchResult.failed("boom").error()).isEqualTo("boom");
  }

  /** Builds the fetch wire body: firstPos|lastPos|highWatermark|dataLength|data. */
  private static byte[] fetchBody(
      final long firstPos, final long lastPos, final long highWatermark, final byte[] data) {
    return ByteBuffer.allocate(HEADER_SIZE + data.length)
        .putLong(firstPos)
        .putLong(lastPos)
        .putLong(highWatermark)
        .putInt(data.length)
        .put(data)
        .array();
  }
}
