/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Walks a clean segment's batch headers to build {@link IndexEntry} byte spans for the composite
 * fetch path — the zero-copy counterpart to {@link CleanSegmentReader}, which decodes full records
 * (with key/value copies) for the cleaner pass instead.
 *
 * <p>Only header bytes (a few dozen per record) are ever read into a JVM buffer; each entry's
 * {@code position}/{@code length} describe a byte span of the <em>original file</em>, meant to be
 * streamed later straight off the still-open {@link FileChannel} (e.g. via a zero-copy {@code
 * SharedFileRegion}) — mirroring exactly what {@link IndexScanResult.Success} already carries for
 * the live-log path. There is no persisted index (ADR 0001, decision 5): every scan walks headers
 * from the start of the segment, hopping by each batch's self-framed {@code batchLength}, exactly
 * like {@link CleanSegmentReader#seek}'s gap-skip.
 *
 * @see io.camunda.zeebe.util.IndexScanResult
 */
final class CleanSegmentScan {

  private CleanSegmentScan() {}

  /**
   * Scans {@code channel} (an open clean segment file) for batches at or after {@code
   * fromPosition}, collecting entries until {@code maxBytes} of batch data has been gathered or the
   * segment is exhausted.
   *
   * @param channel the open clean segment channel
   * @param fromPosition the position to seek to (inclusive); a position below the segment's first
   *     batch lands on the first batch (gap-skip)
   * @param maxBytes the soft cap on total batch bytes collected
   * @return the matching entries in ascending position order, empty if {@code fromPosition} is
   *     beyond the segment's last batch
   * @throws IOException if the channel cannot be read
   * @throws IllegalStateException if the segment is corrupt (non-positive {@code batchLength})
   */
  static List<IndexEntry> scan(
      final FileChannel channel, final long fromPosition, final int maxBytes) throws IOException {
    final long size = channel.size();
    final ByteBuffer headerBuffer = ByteBuffer.allocate(EventBridgeBatch.HEADER_LENGTH);
    final UnsafeBuffer headerView = new UnsafeBuffer(headerBuffer);

    final List<IndexEntry> entries = new ArrayList<>();
    long cursor = 0;
    int collectedBytes = 0;

    while (cursor + EventBridgeBatch.HEADER_LENGTH <= size) {
      headerBuffer.clear();
      readFully(channel, headerBuffer, cursor);

      final long position = EventBridgeBatch.getPosition(headerView, 0);
      final int batchLength = EventBridgeBatch.getBatchLength(headerView, 0);
      if (batchLength <= 0) {
        throw new IllegalStateException(
            "Corrupt clean segment: non-positive batchLength "
                + batchLength
                + " at offset "
                + cursor);
      }
      final int totalSize = EventBridgeBatch.totalSize(batchLength);

      if (position >= fromPosition) {
        entries.add(new IndexEntry(position, position, 0, Math.toIntExact(cursor), totalSize));
        collectedBytes += totalSize;
        if (collectedBytes >= maxBytes) {
          break;
        }
      }
      cursor += totalSize;
    }
    return entries;
  }

  private static void readFully(
      final FileChannel channel, final ByteBuffer buffer, final long position) throws IOException {
    long readPosition = position;
    while (buffer.hasRemaining()) {
      final int read = channel.read(buffer, readPosition);
      if (read < 0) {
        throw new IOException("Unexpected end of clean segment at byte " + readPosition);
      }
      readPosition += read;
    }
  }
}
