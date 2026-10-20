/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.Frame;
import io.camunda.zeebe.eventplane.segment.Metadata;
import io.camunda.zeebe.eventplane.segment.TailTooShortException;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a segment in the store with ranged GETs: its tail first, for the footer and the frame
 * index, then its body from the frame that a reader needs, a frame at a time.
 */
final class SegmentFile {

  private SegmentFile() {}

  /**
   * The segment's footer and metadata, from its last {@link Footer#USUAL_TAIL_LENGTH} bytes, or
   * more if the metadata is longer than usual. Checks them against the segment's name and log.
   *
   * @throws io.camunda.zeebe.eventplane.store.NoSuchObjectException if the segment is gone
   * @throws UnexpectedSegmentException if the footer or metadata contradict the name or the log
   */
  static Tail readTail(final ObjectStore store, final ListedSegment segment, final LogId log)
      throws IOException {
    final long size = segment.size();
    final long tailStart = Math.max(0, size - Footer.USUAL_TAIL_LENGTH);
    final var tail = range(store, segment.key(), tailStart, size);
    final var footer = Footer.read(tail);
    Metadata metadata;
    try {
      metadata = Metadata.read(tail, tailStart, footer);
    } catch (final TailTooShortException e) {
      try {
        metadata =
            Metadata.read(
                range(store, segment.key(), e.metadataStart(), size), e.metadataStart(), footer);
      } catch (final TailTooShortException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
    final var result = new Tail(footer, metadata);
    check(segment, result, log);
    return result;
  }

  /**
   * The body's entries, from the frame where a reader that has every record up to {@code position}
   * starts, to the body's end. Each frame is read with its own ranged GET when the reader gets to
   * it, so a rollup's body is never in memory at once.
   */
  static EntryReader body(
      final ObjectStore store, final ListedSegment segment, final Tail tail, final long position)
      throws IOException {
    final long start = tail.metadata().startAfter(position);
    final var bounds = new ArrayList<Long>();
    for (final Frame frame : tail.metadata().frames()) {
      if (frame.offset() >= start) {
        bounds.add(frame.offset());
      }
    }
    bounds.add(tail.footer().bodyLength());
    return new EntryReader(new FrameStream(store, segment.key(), bounds));
  }

  private static ByteBuffer range(
      final ObjectStore store, final String key, final long from, final long to)
      throws IOException {
    final var bytes = store.getRange(key, from, Math.toIntExact(to - from));
    if (bytes.length != to - from) {
      throw new UnexpectedSegmentException(
          key,
          "Expected %d bytes from byte %d, but got %d".formatted(to - from, from, bytes.length));
    }
    return ByteBuffer.wrap(bytes);
  }

  private static void check(final ListedSegment segment, final Tail tail, final LogId log) {
    final var footer = tail.footer();
    final var name = segment.name();
    if (!footer.history().equals(name.history())
        || footer.firstIndex() != name.firstIndex()
        || footer.lastIndex() != name.lastIndex()) {
      throw new UnexpectedSegmentException(
          segment.key(),
          "Expected history %s and indexes %d to %d, as the name says, but the footer has %s and %d to %d"
              .formatted(
                  name.history(),
                  name.firstIndex(),
                  name.lastIndex(),
                  footer.history(),
                  footer.firstIndex(),
                  footer.lastIndex()));
    }
    final var identity = tail.metadata().identity();
    if (!identity.clusterId().equals(log.cluster())
        || !identity.tenantId().equals(log.tenant())
        || identity.partition() != log.partition()) {
      throw new UnexpectedSegmentException(
          segment.key(),
          "Expected a segment of log %s, but got one of %s/%s/%d"
              .formatted(log, identity.clusterId(), identity.tenantId(), identity.partition()));
    }
  }

  /** What ends a segment: its footer, and the metadata before it. */
  record Tail(Footer footer, Metadata metadata) {}

  /** The bytes between consecutive bounds of an object, each range read when it is needed. */
  private static final class FrameStream extends InputStream {

    private final ObjectStore store;
    private final String key;
    private final List<Long> bounds;
    private int next;
    private byte[] chunk = new byte[0];
    private int at;

    private FrameStream(final ObjectStore store, final String key, final List<Long> bounds) {
      this.store = store;
      this.key = key;
      this.bounds = bounds;
    }

    @Override
    public int read() throws IOException {
      return fill() ? chunk[at++] & 0xFF : -1;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      if (!fill()) {
        return -1;
      }
      final int count = Math.min(length, chunk.length - at);
      System.arraycopy(chunk, at, buffer, offset, count);
      at += count;
      return count;
    }

    private boolean fill() throws IOException {
      while (at == chunk.length) {
        if (next + 1 >= bounds.size()) {
          return false;
        }
        final long from = bounds.get(next);
        final long to = bounds.get(next + 1);
        next++;
        chunk = range(store, key, from, to).array();
        at = 0;
      }
      return true;
    }
  }
}
