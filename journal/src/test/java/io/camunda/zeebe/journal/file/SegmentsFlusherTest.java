/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.journal.util.MockJournalMetastore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SegmentsFlusherTest {
  private final MockJournalMetastore metaStore = new MockJournalMetastore();
  private final SegmentsFlusher flusher = new SegmentsFlusher(-1);

  @Test
  void shouldFlushAllSegments() {
    // given
    final var firstSegment = new TestSegment(15);
    final var secondSegment = new TestSegment(30);

    // when
    flusher.flush(metaStore, List.of(firstSegment, secondSegment));

    // then
    assertThat(firstSegment.flushed).isTrue();
    assertThat(secondSegment.flushed).isTrue();
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(30L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFlush() {
    // given
    final var dirtySegments = List.of(new TestSegment(15), new TestSegment(30, false));

    // when
    flusher.flush(metaStore, dirtySegments);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(15L);
  }

  @Test
  void shouldStoreLastFlushedIndexOnPartialFailedFlush() {
    // given
    final var error = new UncheckedIOException(new IOException("Cannot allocate memory"));
    final var dirtySegments = List.of(new TestSegment(15), new TestSegment(30, error));

    // when
    assertThatCode(() -> flusher.flush(metaStore, dirtySegments)).isSameAs(error);

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(15L);
  }

  private static final class TestSegment implements FlushableSegment {
    private final long lastIndex;
    private final boolean shouldFlush;
    private final RuntimeException flushError;
    private boolean flushed;

    private TestSegment(final long lastIndex) {
      this(lastIndex, true);
    }

    private TestSegment(final long lastIndex, final boolean shouldFlush) {
      this(lastIndex, shouldFlush, null);
    }

    private TestSegment(final long lastIndex, final RuntimeException flushError) {
      this(lastIndex, true, flushError);
    }

    private TestSegment(
        final long lastIndex, final boolean shouldFlush, final RuntimeException flushError) {
      this.lastIndex = lastIndex;
      this.shouldFlush = shouldFlush;
      this.flushError = flushError;
    }

    @Override
    public long lastIndex() {
      return lastIndex;
    }

    @Override
    public boolean flush() {
      if (flushError != null) {
        throw flushError;
      }

      flushed = shouldFlush;
      return shouldFlush;
    }
  }
}
