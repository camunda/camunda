/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.InvalidSegmentException;
import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.segment.Metadata;
import io.camunda.zeebe.eventplane.segment.SegmentWriter;
import io.camunda.zeebe.eventplane.segment.TailTooShortException;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Merges consecutive segments of one history into the bytes of a rollup. */
final class RollupBuilder {

  private final ObjectStore store;

  RollupBuilder(final ObjectStore store) {
    this.store = store;
  }

  /**
   * Reads each segment of the chain in full, and appends the entries that the rollup doesn't have
   * yet: an entry that is not above the last one appended is a repeat from a second leader, and is
   * skipped.
   *
   * @param chain at least one segment, each starting after the one before it
   * @return the rollup, or empty if a segment is gone from the store, which another maintenance may
   *     have rolled up
   * @throws InvalidSegmentException if a segment's bytes are not those of a segment
   * @throws UnexpectedSegmentException if a segment holds other entries than its name says
   */
  Optional<Rollup> build(final LogId log, final UUID history, final List<StoredSegment> chain)
      throws IOException {
    final var out = new ByteArrayOutputStream();
    long latestRecordTimestamp = NO_POSITION;
    long lastIndex = 0;
    final Footer footer;
    try (final var writer = new SegmentWriter(out, log, history, Level.ROLLUP)) {
      for (final var segment : chain) {
        final var content = store.get(segment.key());
        if (content.isEmpty()) {
          return Optional.empty();
        }
        final var input = content.get();
        final var inputFooter = readFooter(input, log, history, segment);
        latestRecordTimestamp =
            Math.max(latestRecordTimestamp, inputFooter.latestRecordTimestamp());
        final var body = new ByteArrayInputStream(input, 0, (int) inputFooter.bodyLength());
        try (final var reader = new EntryReader(body)) {
          for (var entry = reader.next(); entry != null; entry = reader.next()) {
            if (entry.index() > lastIndex) {
              writer.append(entry);
              lastIndex = entry.index();
            }
          }
        }
      }
      footer = writer.finish(latestRecordTimestamp);
    }
    final long firstIndex = chain.getFirst().firstIndex();
    final long expectedLastIndex = chain.getLast().lastIndex();
    if (footer.firstIndex() != firstIndex || footer.lastIndex() != expectedLastIndex) {
      throw new UnexpectedSegmentException(
          chain.getFirst().key(),
          "entries %d to %d together with the %d segments after it"
              .formatted(firstIndex, expectedLastIndex, chain.size() - 1),
          "entries %d to %d".formatted(footer.firstIndex(), footer.lastIndex()));
    }
    return Optional.of(new Rollup(out.toByteArray(), footer));
  }

  /** Reads the footer of a whole segment, and checks it and the metadata against the key. */
  private static Footer readFooter(
      final byte[] content, final LogId log, final UUID history, final StoredSegment segment) {
    final var footer = Footer.read(ByteBuffer.wrap(content));
    final Metadata metadata;
    try {
      metadata = Metadata.read(ByteBuffer.wrap(content), 0, footer);
    } catch (final TailTooShortException e) {
      // The tail is the whole segment, so it starts at or before the metadata.
      throw new UnexpectedSegmentException(
          segment.key(), "its metadata in the bytes it has", String.valueOf(e.getMessage()), e);
    }
    if (!footer.history().equals(history)
        || footer.firstIndex() != segment.firstIndex()
        || footer.lastIndex() != segment.lastIndex()) {
      throw new UnexpectedSegmentException(
          segment.key(),
          "history %s and entries %d to %d, as its key says"
              .formatted(history, segment.firstIndex(), segment.lastIndex()),
          "history %s and entries %d to %d"
              .formatted(footer.history(), footer.firstIndex(), footer.lastIndex()));
    }
    if (!metadata.log().equals(log)) {
      throw new UnexpectedSegmentException(
          segment.key(), "the log " + log, "the log " + metadata.log());
    }
    return footer;
  }

  /**
   * A rollup, ready to PUT.
   *
   * @param content the object's bytes
   * @param footer its footer, which holds the range of indexes
   */
  record Rollup(byte[] content, Footer footer) {}
}
