/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the batch-level iteration contract of {@link EventStreamReader}: a seek lands on the
 * batch that <em>contains</em> the target position, and read-current-then-advance iteration returns
 * every batch from there exactly once — no skipped first batch, no duplicated last batch.
 */
final class EventStreamReaderTest {

  @Test
  void shouldReturnEveryBatchOnceFromTheStartInOneBlock() {
    // given — a single block holding five single-entry batches at positions 1..5
    final var reader = readerOf(block(batch(1), batch(2), batch(3), batch(4), batch(5)));

    // when
    reader.seekToFirstBatch();

    // then — all five, in order, no skip and no duplicate
    assertThat(drain(reader)).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldReturnEveryBatchOnceAcrossMultipleBlocks() {
    // given — the same five batches split across three blocks
    final var reader =
        readerOf(block(batch(1), batch(2)), block(batch(3), batch(4)), block(batch(5)));

    // when
    reader.seekToFirstBatch();

    // then
    assertThat(drain(reader)).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void shouldStartAtTheBatchContainingTheSeekPosition() {
    // given
    final var reader = readerOf(block(batch(1), batch(2), batch(3), batch(4), batch(5)));

    // when — seek to an exact batch boundary
    reader.seek(3);

    // then — the boundary batch (3) is included, nothing before it, nothing duplicated
    assertThat(drain(reader)).containsExactly(3L, 4L, 5L);
  }

  @Test
  void shouldReturnTheWholeBatchThatContainsAMidBatchPosition() {
    // given — a 3-entry batch covering positions 1..3, then a 2-entry batch covering 4..5
    final var reader = readerOf(block(batch(1, 3), batch(4, 2)));

    // when — ask for position 2, which falls inside the first batch
    reader.seek(2);

    // then — the reader returns the whole boundary batch (start position 1); entry-level skipping
    // of position 1 is the consumer's job, not the reader's
    assertThat(drain(reader)).containsExactly(1L, 4L);
  }

  // -- helpers --

  private static EventStreamReader readerOf(final DirectBuffer... blocks) {
    return new EventStreamReader(new FakeLogStorageReader(Arrays.asList(blocks)));
  }

  /** Reads the current batch's position, then advances — the documented reader contract. */
  private static List<Long> drain(final EventStreamReader reader) {
    final List<Long> positions = new ArrayList<>();
    while (reader.hasNext()) {
      positions.add(reader.position());
      reader.next();
    }
    return positions;
  }

  private static long[] batch(final long position) {
    return new long[] {position, 1};
  }

  private static long[] batch(final long position, final int entryCount) {
    return new long[] {position, entryCount};
  }

  /**
   * Builds a block buffer holding the given batches back to back (header-only, no entry payload).
   */
  private static DirectBuffer block(final long[]... batches) {
    final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
    int offset = 0;
    for (final long[] b : batches) {
      EventBridgeBatch.writeHeader(buffer, offset, 0, 0, (int) b[1]);
      EventBridgeBatch.patchPosition(buffer, offset, b[0]);
      offset += EventBridgeBatch.HEADER_LENGTH;
    }
    return new UnsafeBuffer(buffer, 0, offset);
  }

  /** In-memory LogStorageReader: seek resets to the first block; the reader fine-walks batches. */
  private static final class FakeLogStorageReader implements LogStorageReader {
    private final List<DirectBuffer> blocks;
    private int cursor;

    FakeLogStorageReader(final List<DirectBuffer> blocks) {
      this.blocks = blocks;
    }

    @Override
    public void seek(final long position) {
      cursor = 0;
    }

    @Override
    public boolean hasNext() {
      return cursor < blocks.size();
    }

    @Override
    public DirectBuffer next() {
      return blocks.get(cursor++);
    }

    @Override
    public void close() {}
  }
}
