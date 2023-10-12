/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalMetaStore;
import java.util.Collection;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SegmentsFlusher {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentsFlusher.class);

  private final JournalMetaStore metaStore;

  // no need to make this volatile if callers always acquire the write-lock beforehand
  private long lastFlushedIndex;

  SegmentsFlusher(final JournalMetaStore metaStore) {
    this.metaStore = Objects.requireNonNull(metaStore, "must specify a meta store");
    lastFlushedIndex = metaStore.loadLastFlushedIndex();
  }

  void setLastFlushedIndex(final long lastFlushedIndex) {
    this.lastFlushedIndex = lastFlushedIndex;
    metaStore.storeLastFlushedIndex(lastFlushedIndex);
    metaStore.flushMetaStore();
  }

  long nextFlushIndex() {
    return lastFlushedIndex + 1;
  }

  /**
   * Fetches all segments with a last index greater than or equal to current {@link
   * #lastFlushedIndex}. These are then flushed in order. The {@link Segment#lastIndex()} of the
   * last successful segment to be flushed will be stored in the given {@link JournalMetaStore}.
   *
   * @param dirtySegments the list of segments which need to be flushed
   */
  void flush(final Collection<? extends FlushableSegment> dirtySegments) {
    final var segmentsCount = dirtySegments.size();
    long flushedIndex = -1;

    if (segmentsCount == 0) {
      LOGGER.debug(
          "No segments to flush for index {}; there may be nothing to flush", flushedIndex);
      return;
    }

    try {
      for (final var segment : dirtySegments) {
        final long lastSegmentIndex = segment.lastIndex();
        if (segment.flush()) {
          flushedIndex = lastSegmentIndex;
        }
      }
    } finally {
      // store whatever we managed to flush to avoid doing it again
      if (flushedIndex > lastFlushedIndex) {
        setLastFlushedIndex(flushedIndex);

        LOGGER.trace(
            "Flushed {} segment(s), from index {} to index {}",
            segmentsCount,
            lastFlushedIndex,
            flushedIndex);
      }
    }
  }
}
