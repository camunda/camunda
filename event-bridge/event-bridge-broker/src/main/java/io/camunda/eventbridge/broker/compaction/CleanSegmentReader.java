/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import io.camunda.eventbridge.protocol.EventBridgeEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Iterates the single-entry batches of one clean segment and resolves positions by hop-scanning
 * batch headers — there is no index file of any kind (ADR 0001, decision 5). Each batch is
 * self-framing: its {@code batchLength} field gives the distance to the next batch, and its {@code
 * position} field its absolute log position, so {@link #seek(long)} walks batch headers and lands
 * on the first batch at or after the target position (gap-skip semantics).
 *
 * <p>The whole (size-bounded) segment is read into memory once on construction; iteration and seeks
 * are then pure in-memory scans.
 *
 * <p>Threading: not thread-safe; a reader instance is used by a single caller.
 */
public final class CleanSegmentReader {

  private final UnsafeBuffer buffer;
  private final int length;
  private final EventBridgeBatchIterator batchIterator = new EventBridgeBatchIterator();

  private int cursor;

  /**
   * Opens a clean segment for reading, loading its bytes into memory. Callers on a concurrent
   * deletion path should prefer {@link #CleanSegmentReader(ReaderLease)}: this constructor resolves
   * the path directly and is only safe where the file cannot be condemned concurrently (e.g. the
   * cleaner actor reading segments referenced by the latest committed manifest).
   *
   * @param segmentFile the segment file
   */
  public CleanSegmentReader(final Path segmentFile) {
    this(readFile(segmentFile));
  }

  /**
   * Reads a clean segment through a held {@link ReaderLease}, using the lease's already-open
   * channel. This is the safe way to read a segment that the trash queue may condemn concurrently:
   * the open descriptor keeps the bytes readable even after the file is renamed or unlinked. The
   * lease stays owned by the caller — this reader does not release it.
   *
   * @param lease a held lease on the segment
   */
  public CleanSegmentReader(final ReaderLease lease) {
    this(readChannel(lease));
  }

  private CleanSegmentReader(final byte[] bytes) {
    buffer = new UnsafeBuffer(bytes);
    length = bytes.length;
    cursor = 0;
  }

  private static byte[] readFile(final Path segmentFile) {
    try {
      return Files.readAllBytes(segmentFile);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read clean segment " + segmentFile, e);
    }
  }

  private static byte[] readChannel(final ReaderLease lease) {
    try {
      final FileChannel channel = lease.channel();
      final int size = Math.toIntExact(channel.size());
      final ByteBuffer bytes = ByteBuffer.allocate(size);
      int position = 0;
      while (bytes.hasRemaining()) {
        final int read = channel.read(bytes, position);
        if (read < 0) {
          throw new IOException("Unexpected end of clean segment at byte " + position);
        }
        position += read;
      }
      return bytes.array();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read clean segment " + lease.file(), e);
    }
  }

  /**
   * Positions the reader on the first batch whose position is at or after {@code targetPosition}. A
   * target below the segment's first position lands on the first batch; a target beyond the last
   * position leaves the reader exhausted ({@link #hasNext()} returns {@code false}). This is the
   * same "containing or following" seek the live-log reader offers, so a swept (dropped) position
   * resolves to the next surviving record.
   *
   * @param targetPosition the position to seek to (inclusive)
   */
  public void seek(final long targetPosition) {
    cursor = 0;
    while (cursor + EventBridgeBatch.HEADER_LENGTH <= length) {
      final long position = EventBridgeBatch.getPosition(buffer, cursor);
      if (position >= targetPosition) {
        return;
      }
      cursor += batchTotalSizeAt(cursor);
    }
  }

  /** Returns {@code true} if another single-entry batch is available at the cursor. */
  public boolean hasNext() {
    if (cursor + EventBridgeBatch.HEADER_LENGTH > length) {
      return false;
    }
    final int total = batchTotalSizeAt(cursor);
    return cursor + total <= length;
  }

  /**
   * Decodes the batch at the cursor into a {@link CompactionRecord} and advances past it.
   *
   * @return the record
   * @throws NoSuchElementException if the reader is exhausted
   * @throws IllegalStateException if a batch is not a well-formed single-entry batch
   */
  public CompactionRecord next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No more batches in clean segment");
    }
    final int total = batchTotalSizeAt(cursor);
    final int entryCount = EventBridgeBatch.getEntryCount(buffer, cursor);
    if (entryCount != 1) {
      throw new IllegalStateException(
          "Clean segment batch at offset "
              + cursor
              + " has entryCount="
              + entryCount
              + ", expected exactly 1 (clean segments hold single-entry batches)");
    }

    batchIterator.wrap(buffer, cursor, total);
    final int attributes = EventBridgeBatch.getAttributes(buffer, cursor);
    final EventBridgeEntry entry = batchIterator.next();
    final CompactionRecord record =
        new CompactionRecord(
            entry.getPosition(),
            entry.getTimestamp(),
            entry.getKeyCopy(),
            entry.getValueCopy(),
            attributes);

    cursor += total;
    return record;
  }

  private int batchTotalSizeAt(final int offset) {
    final int batchLength = EventBridgeBatch.getBatchLength(buffer, offset);
    if (batchLength <= 0) {
      throw new IllegalStateException(
          "Corrupt clean segment: non-positive batchLength "
              + batchLength
              + " at offset "
              + offset);
    }
    return EventBridgeBatch.totalSize(batchLength);
  }
}
