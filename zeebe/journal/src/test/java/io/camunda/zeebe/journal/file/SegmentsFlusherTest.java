/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SegmentsFlusherTest {
  private final MockJournalMetastore metaStore = new MockJournalMetastore();
  private final SegmentsFlusher flusher = new SegmentsFlusher(metaStore);

  @Test
  void shouldFlushAllSegments() throws FlushException {
    // given
    final var firstSegment = new TestSegment(15);
    final var secondSegment = new TestSegment(30);

    // when
    flusher.flush(List.of(firstSegment, secondSegment));

    // then
    assertThat(firstSegment.flushed).isTrue();
    assertThat(secondSegment.flushed).isTrue();
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(30L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFlush() throws FlushException {
    // given
    final var dirtySegments = List.of(new TestSegment(15), new TestSegment(30, false));

    // when
    assertThatThrownBy(() -> flusher.flush(dirtySegments));

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFailedFlush() {
    // given
    final var error = new FlushException(new IOException("Cannot allocate memory"));
    final var dirtySegments = List.of(new TestSegment(15), new TestSegment(30, error));

    // when
    assertThatCode(() -> flusher.flush(dirtySegments)).isSameAs(error);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(15L);
  }

  private static final class TestSegment implements FlushableSegment {
    private final long lastIndex;
    private final boolean shouldFlush;
    private final FlushException flushError;
    private boolean flushed;

    private TestSegment(final long lastIndex) {
      this(lastIndex, true);
    }

    private TestSegment(final long lastIndex, final boolean shouldFlush) {
      this(lastIndex, shouldFlush, null);
    }

    private TestSegment(final long lastIndex, final FlushException flushError) {
      this(lastIndex, true, flushError);
    }

    private TestSegment(
        final long lastIndex, final boolean shouldFlush, final FlushException flushError) {
      this.lastIndex = lastIndex;
      this.shouldFlush = shouldFlush;
      this.flushError = flushError;
    }

    @Override
    public long lastIndex() {
      return lastIndex;
    }

    @Override
    public void flush() throws FlushException {
      if (flushError != null) {
        throw flushError;
      }

      flushed = shouldFlush;
      if (!shouldFlush) {
        throw new FlushException(new IOException("flush failed"));
      }
    }
  }
}
