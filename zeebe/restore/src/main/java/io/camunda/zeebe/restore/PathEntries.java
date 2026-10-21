/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.NoSuchObjectException;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.restore.BucketRestoreException.LogEndsBeforeTarget;
import io.camunda.zeebe.restore.BucketRestoreException.MissingEntry;
import io.camunda.zeebe.restore.BucketRestoreException.UnexpectedEntry;
import io.camunda.zeebe.restore.PathSegments.PathSegment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends the entries of a path through a partition from the bucket to a journal, byte for byte, as
 * segment entries are the journal's own.
 */
final class PathEntries {

  /** The target that appends everything the bucket holds. */
  static final long LATEST = Long.MAX_VALUE;

  private static final int RELISTS = 3;

  private final ObjectStore store;
  private final LogId log;
  private final PathSegments segments;

  PathEntries(final ObjectStore store, final LogId log, final PathSegments segments) {
    this.store = store;
    this.log = log;
    this.segments = segments;
  }

  /**
   * Resets the journal to {@code first}, and appends the path's entries from there, until the first
   * entry with a record past the target or the bucket's end.
   *
   * @throws MissingEntry if the bucket doesn't hold the entry at {@code first}
   * @throws LogEndsBeforeTarget if the bucket ends before a target other than {@link #LATEST}
   */
  Appended append(final Journal journal, final long first, final long target) throws IOException {
    journal.reset(first);
    long next = first;
    long position = Entry.NO_POSITION;
    int relists = 0;
    while (true) {
      final var segment = segments.holding(next);
      if (segment.isEmpty()) {
        break;
      }
      final List<Entry> entries;
      try {
        entries = read(segment.get(), next);
      } catch (final NoSuchObjectException e) {
        // a segment can disappear while a reader reads, when maintenance replaces it
        if (++relists > RELISTS) {
          throw e;
        }
        segments.refresh();
        continue;
      }
      for (final var entry : entries) {
        if (entry.hasRecords() && entry.lastPosition() > target) {
          return new Appended(next - 1, position);
        }
        journal.append(entry.checksum(), entry.serializedRecord());
        if (entry.hasRecords()) {
          position = entry.lastPosition();
        }
        next++;
      }
    }
    if (next == first) {
      throw new MissingEntry(log.partition(), first);
    }
    if (target != LATEST && position < target) {
      throw new LogEndsBeforeTarget(log.partition(), target, next - 1, position);
    }
    return new Appended(next - 1, position);
  }

  /** The segment's footer, which says which entries and positions it holds. */
  Footer footer(final PathSegment segment) throws IOException {
    final long start = Math.max(0, segment.size() - Footer.USUAL_TAIL_LENGTH);
    final var tail = store.getRange(segment.key(), start, Footer.USUAL_TAIL_LENGTH);
    return Footer.read(ByteBuffer.wrap(tail));
  }

  /** The segment's entries from {@code next} up to the last that serves the path. */
  private List<Entry> read(final PathSegment segment, final long next) throws IOException {
    final var content =
        store.get(segment.key()).orElseThrow(() -> new NoSuchObjectException(segment.key()));
    final var footer = Footer.read(ByteBuffer.wrap(content));
    final var entries = new ArrayList<Entry>();
    long expected = next;
    try (final var reader =
        new EntryReader(new ByteArrayInputStream(content, 0, (int) footer.bodyLength()))) {
      for (var entry = reader.next();
          entry != null && expected <= segment.lastServed();
          entry = reader.next()) {
        if (entry.index() < next) {
          continue;
        }
        if (entry.index() != expected) {
          throw new UnexpectedEntry(log.partition(), segment.key(), expected, entry.index());
        }
        entries.add(entry);
        expected++;
      }
    }
    if (expected <= segment.lastServed()) {
      throw new UnexpectedEntry(log.partition(), segment.key(), expected, -1);
    }
    return entries;
  }

  /** The journal's last appended index, and the last record's position, or -1 if none. */
  record Appended(long lastIndex, long position) {}
}
